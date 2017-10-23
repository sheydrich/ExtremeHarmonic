import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.math3.fraction.BigFraction;


/**
 * 
 * This class is for checking whether a given set of parameters achieves a specific competitive ratio.
 * Running this class requires a FULL set of parameters, that is, item types (each with size, red fraction,
 * bluefit, redfit, needs, leaves), red spaces, and values for y1, y2 and y3 for all cases. 
 *
 */
public class ExtremeHarmonicVerifier extends DualLPChecker {

	private BufferedWriter knapsackOutputWriter;
	private BufferedWriter weightsWriter;

	public static void main(String[] args) throws IOException {
		ExtremeHarmonicVerifier verifier = new ExtremeHarmonicVerifier(args==null || args.length==0 ? null : args[0]);
		//This is to make sure that the output files are closed when the program exits.
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (verifier.writer!=null)
						verifier.writer.close();
					if (verifier.knapsackOutputWriter!=null)
						verifier.knapsackOutputWriter.close();
					if (verifier.weightsWriter!=null)
						verifier.weightsWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}){});
		verifier.start();
	}


	/*
	 * This class inherits fields from its parent DualLPChecker that contain the following information:
	 * - types
	 * - red spaces
	 * - target ratio
	 * - expansion of sand items
	 */



	private BigFraction[] y1Values; //index denotes the value of k
	private BigFraction[] y2Values; //index denotes the value of k
	private BigFraction[] y3Values; //index denotes the value of k

	//this is for speeding up the knapsack solver
	private BigFraction patternWeightThreshold;



	/**
	 * Reads input file and initializes everything needed for running the verification.
	 */
	public ExtremeHarmonicVerifier(String inputFile) throws IOException {
		//initialize logging
		knapsackOutputWriter = new BufferedWriter(new FileWriter("knapsackData.txt"));
		writer = new BufferedWriter(new FileWriter("protocol_ExtremeHarmonic.txt"));
		weightsWriter = new BufferedWriter(new FileWriter("weights.txt"));

		//read all the input data, initialize all the arrays etc.
		initialize(inputFile==null ? Util.vpFileName : inputFile);

		//write type information to a file
		writeTypeInformation();
		writeWeights();
	}

	/**
	 * Initialize all parameters by reading the input file given. This given file name must not
	 * be null.
	 */
	private void initialize(String inputFile) throws IOException {
		FileIO io = new FileIO();
		log("Preparation: reading parameters from file");

		//read parameters from the input file
		io.readVerifierInput(inputFile);
		this.types = io.getTypes();
		this.targetRatio = io.getTargetRatio();
		this.redSpace = io.getRedSpaces();
		this.y1Values = io.getY1Values();
		this.y2Values = io.getY2Values();
		this.y3Values = io.getY3Values();

		//compute some additional parameters
		this.sandExpansion = BigFraction.ONE.divide(BigFraction.ONE.subtract(types[types.length-1].getSizeLB()));
		for (int i = 0; i<types.length; ++i)
			types[i].computeWeights();
		patternWeightThreshold = targetRatio.subtract(new BigFraction(1,1000));
		log("\n\tTARGET RATIO: "+targetRatio.doubleValue()+"\n--------------------------------------------");
	}

	/**
	 * This method starts the verification itself.
	 * @throws IOException
	 */
	private void start() throws IOException {
		log("--------------------------------------------\n");
		log("Now starting the searches for heavy patterns.");
		log("Each knapsack search ignores all patterns with weight at most the target ratio minus 0.001.\n");
		log("--------------------------------------------\n");
		log("Checking case where no r-item exists (k=K+1).");

		//check that eps<0.1
		if (types[types.length-1].getSizeLB().compareTo(new BigFraction(1,10))>=0) {
			log("INFEASIBLE! smallest item type must be below 0.1 but has size lower bound " + types[types.length-1].getSizeLB());
			System.exit(0);
		}
		//check that red fraction <1/3 for all types
		for (int i = 0; i<types.length; ++i)
			if (types[i].getRedFraction().compareTo(BigFraction.ONE_THIRD)>=0) {
				log(String.format("INFEASIBLE! red fraction of type with size at least " + types[i].getSizeLB() + " is %.5f>1/3!", types[i].getRedFraction().doubleValue()));
				System.exit(0);
			}

		//---------------------CASE 1: k=K+1 ------------------------------
		//find the heaviest pattern without r and check whether its weight is below our target ratio
		KnapsackPattern v = findHeaviestPatternWithoutR();

		if (v!=null && v.getTotalWeightInclSand(sandExpansion).compareTo(targetRatio)>0) {
			//if this is already too large, stop the program
			log("INFEASIBLE FOR CASE WITHOUT r! Stopping computations.");
			System.exit(0);
		}

		//output
		if (v!=null) {
			log("\tHeaviest pattern: " + v);
			log("\twith weights: " + v.weightString());
			log(String.format("\tand total weight: %.5f", v.getTotalWeightInclSand(sandExpansion).doubleValue()));
		} else {
			log(String.format("\tNo pattern above weight %.5f found!", patternWeightThreshold.doubleValue()));
		}
		log(String.format("Case k = K+1 verified for target value y4 = %s = %.5f!\n\n--------------------------------------------\n", targetRatio.toString(), targetRatio.doubleValue()));


		//---------------------FURTHER CASES: k<K+1 ------------------------------
		//now, check the different cases for r
		for (int k = 0; k<redSpace.length; ++k) {
			if (!isNecessaryToCheckCase(k)) continue; //we can skip this case if there are no red items of this class

			//check feasibility of the dual LP; compute heaviest pattern for corresponding knapsack problem
			v = checkDualLP(k);
			if (v!=null && v.getTotalWeightInclSand(sandExpansion).compareTo(targetRatio)>0) {
				//if this is too large, stop the program
				log("INFEASIBLE FOR CASE WHERE k=" + k + "! Stopping computations.");
				System.exit(0);
			}
			log("Case k=" + k + " verified!\n\n--------------------------------------------\n");
		}

		log("\n\nAll cases proven feasible! Competitive ratio is " + targetRatio.doubleValue());
	}

	/**
	 * This method checks the case that k=K+1.
	 * It returns the heaviest pattern for this case.
	 */
	private KnapsackPattern findHeaviestPatternWithoutR() throws IOException {

		//create the large types that are needed in this case and compute all weights
		BigFraction[] allSizes = new BigFraction[types.length + 2]; //we need to add large items of size in (1/2, 2/3] and (2/3, 1]
		BigFraction[] weights = new BigFraction[allSizes.length];
		allSizes[0] = BigFraction.TWO_THIRDS;
		allSizes[1] = BigFraction.ONE_HALF;
		weights[0] = weights[1] = BigFraction.ONE;
		for (int i = 2; i<allSizes.length; ++i) {
			allSizes[i] = types[i-2].getSizeLB();
			//weights are (1-red)/bluefit
			weights[i] = types[i-2].getBlueWeight();
		}

		//write the weights to a file for reference
		writeKnapsackFile(redSpace.length, allSizes, weights);

		//call the knapsack solver
		KnapsackSolver solver = new KnapsackSolver(allSizes, weights, new AllPatterns(), sandExpansion);
		return solver.solve(patternWeightThreshold);
	}

	/**
	 * This method checks a case where k<K+1.
	 * It returns the heaviest pattern for this case.
	 * 
	 */
	private KnapsackPattern checkDualLP(int k) throws IOException {
		//check whether r is medium or small
		boolean rIsMedium = redSpace[k].compareTo(BigFraction.ONE_THIRD)>0;

		//check that the y3-value is ok, i.e., in the interval [0, 1/2]
		BigFraction y3 = y3Values[k];
		if (y3.compareTo(BigFraction.ZERO)<0) {
			log("INFEASIBLE! Value of y3 must be non-negative but is " + y3.doubleValue() + " in case k = " + k);
			System.exit(0);
		}

		if (y3.compareTo(BigFraction.ONE_HALF)>0) {
			log("INFEASIBLE! Value of y3 must be below 1/2 but is " + y3.doubleValue() + " in case k = " + k);
			System.exit(0);
		}

		if (!rIsMedium) { 

			// --------------------------------
			// r is small
			// --------------------------------


			log("r is small, so check feasibility of simpler dual LP; y3 = " + y3.doubleValue());

			//we need two large types: (1/2, 2/3] and (2/3, 1]
			BigFraction[] allSizes = new BigFraction[types.length+2];
			BigFraction[] weights = new BigFraction[allSizes.length];
			allSizes[0] = BigFraction.TWO_THIRDS;
			allSizes[1] = BigFraction.ONE_HALF;
			weights[0] = BigFraction.ONE; //w=v=1 for this type
			weights[1] = BigFraction.ONE.subtract(y3); //(1-y3)*w + y3*v = 1-y3 as w=1 and v=0 for this type
			for (int i = 0; i<types.length; ++i) {
				allSizes[i+2] = types[i].getSizeLB();
				//weights in this case are (1-y3)*w + y3*v
				weights[i+2] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
			}

			//write weights to a file for reference
			writeKnapsackFile(k, allSizes, weights);

			//check feasibility of the dual LP
			KnapsackPattern p = checkSimpleDualLP(allSizes, weights);

			//output the findings
			if (p!=null)
				log(String.format("\tHeaviest pattern is " + p + "\n\twith weights " + p.weightString() + "\n\tand total weight %.5f", p.getTotalWeightInclSand(sandExpansion).doubleValue()));
			else
				log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
			return p;
		} else { 

			// --------------------------------
			// r is medium
			// --------------------------------


			int t = computeTypeOfRFromClass(k); //compute the type of r

			if (types[t].getRedFraction().equals(BigFraction.ZERO)) {
				log("No need to check this case, as no red item of class " + k + " can exist (red fraction = 0).\n\n--------------------------------------------\n");
				return null;
			}

			//compute weight of the special pattern q1; this is also the weight of q2
			BigFraction w1 = computeWeightOfQ1(t);

			//add three large types: (2/3, 1], (1-t_t(r), 2/3], (1/2, 1-t_t(r)]
			BigFraction[] allSizes = new BigFraction[types.length+3];
			BigFraction[] weights = new BigFraction[allSizes.length];
			allSizes[0] = BigFraction.TWO_THIRDS;
			allSizes[1] = BigFraction.ONE.subtract(types[t-1].getSizeLB());
			allSizes[2] = BigFraction.ONE_HALF;
			//compute weight of large types: (1-y3)*w + y3*v
			weights[0] = weights[1] = BigFraction.ONE; //w=v=1 for these types
			weights[2] = BigFraction.ONE.subtract(y3); //w=1 and v=0 for this type
			for (int i = 0; i<types.length; ++i) allSizes[i+3] = types[i].getSizeLB();

			if (w1.compareTo(targetRatio)<0) {

				// ----------------------------------------
				// FIRST CASE: w1 < y4, so we can use the simpler LP
				// ----------------------------------------

				log(String.format("r is medium and %.5f = w_{1k} < c = %.5f, so check feasibility of simpler dual LP; y3 = "+y3.doubleValue()+".", w1.doubleValue(), targetRatio.doubleValue()));

				//in this case, weights are again (1-y3)*w + y3*v
				for (int i = 0; i<types.length; ++i) {
					weights[i+3] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
				}

				//write weights to file for later reference
				writeKnapsackFile(k, allSizes, weights);

				//check feasibility of the dual LP
				KnapsackPattern p = checkSimpleDualLP(allSizes, weights);

				//output findings
				if (p!=null)
					log(String.format("\tHeaviest pattern is " + p + "\n\twith weights " + p.weightString() + "\n\tand total weight %.5f", p.getTotalWeightInclSand(sandExpansion).doubleValue()));
				else
					log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
				return p;
			} else {

				// ----------------------------------------
				// SECOND CASE: w1 >= y4, so we use the extended LP
				// ----------------------------------------

				log(String.format("r is medium and %.5f = w_{1k} >= c = %.5f, so check feasibility of extended dual LP; y3 = "+y3.doubleValue()+".", w1.doubleValue(), targetRatio.doubleValue()));

				//check that values y1, y2 are ok
				BigFraction y1 = y1Values[k];
				BigFraction y2 = y2Values[k];
				if (y1.compareTo(BigFraction.ZERO)<0) {
					log("INFEASIBLE! Value of y1 must be non-negative but is " + y1 + " in case k = " + k);
					System.exit(0);
				}
				if (y1.compareTo(new BigFraction(5,100))>0) {
					log("INFEASIBLE! Value of y1 must be below 0.05 but is " + y1 + " in case k = " + k);
					System.exit(0);
				}
				if (y2.compareTo(BigFraction.ZERO)<0) {
					log("INFEASIBLE! Value of y2 must be non-negative but is " + y2 + " in case k = " + k);
					System.exit(0);
				}

				//compute weights for non-large items: this is the function omega
				for (int i = 0; i<types.length; ++i)
					weights[i+3] = types[i].computeOmega(types[t], y1, y2, y3);

				//the object check makes sure that patterns q1, q2 are not considered in the search for the heaviest pattern
				PatternFeasibilityCheck check = new NotQ1Q2(allSizes[1], types[t].getSizeLB()); 

				//write weights to file for later reference
				writeKnapsackFile(k, allSizes, weights);

				//check dual LP
				//first, check simple constraints
				if (y1.add(targetRatio).compareTo(w1)<0) {
					log("VERIFICATION FAILED! First constraint (y1+y4 >= w1) is violated in case k="+k+"!");
					System.exit(1);
				}
				if (y2.divide(2).add(targetRatio).compareTo(w1)<0) {
					log("VERIFICATION FAILED! Second constraint (y2/2+y4 >= w1) is violated in case k="+k+"!");
					System.exit(1);
				}
				log("First two constraints of dual LP verified.");

				//now, solve knapsack problem to check other constraints
				KnapsackSolver solver = new KnapsackSolver(allSizes, weights, check, sandExpansion);
				KnapsackPattern p = solver.solve(patternWeightThreshold);

				//output findings
				if (p!=null)
					log(String.format("\tHeaviest pattern is " + p + "\n\twith weights " + p.weightString() + "\n\tand total weight %.5f", p.getTotalWeightInclSand(sandExpansion).doubleValue()));
				else
					log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
				return p;
			}
		}
	}

	//---------------------------------------------
	//---------------------------------------------
	//----------------OUTPUT-----------------------
	//---------------------------------------------
	//---------------------------------------------


	/**
	 * This method writes the the file params.text.
	 */
	private void writeTypeInformation() throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter("params.txt"));
		BigFraction max = BigFraction.ZERO;
		for (int i = 0; i<types.length; ++i) {
			writer.write("Type " + i + ": ( " + types[i].getSizeLB() + " , " + (i==0 ? BigFraction.ONE_HALF : types[i-1].getSizeLB()) + " ]\n");
			writer.write("\tfraction red = " + types[i].getRedFraction().doubleValue() + ", " 
					+ types[i].getBluefit() + " blue items per bin " + ", "
					+ types[i].getRedfit() + " red items per bin " 
					+ ", needs space " + types[i].getNeeds() 
					+ ", leaves space " + types[i].getLeaves() + "\n");
			if(max.compareTo(types[i].getRedFraction())<0) max = types[i].getRedFraction();
		}
		writer.write("\nMaximal red fraction value: "+max.doubleValue());
		writer.write("\n\nRedSpaces:\n");
		for (int i = 0; i<redSpace.length; ++i) {
			writer.write(i + ": " + redSpace[i] + "\n");
		}

		writer.close();
	}


	/**
	 * This method writes the file weights.txt.
	 */
	private void writeWeights() throws IOException {
		String s = "The non-large types (i.e., those with size below 1/2) are the same for all values of k.\n"+
				"For each type i, we give the following information below:\n\t" +
				"- lower bound of the size, i.e., t_{i+1}\n\t"+
				"- the blue weight, which is (1-red_i)/bluefit_i\n\t"+
				"- the red weight, which is red_i/redfit_i\n\t"+
				"- the maximum weight (which is the sum of the two weights)\n\t"+
				"- the value leaves_i\n\t"+
				"- the value needs_i\n\t"+
				"- the weight used in the knapsack problem; several weights are given for different ranges of k\n"+
				"Please note that rational numbers are given as rounded decimals for sake of readability.\nExact values can be found in the file knapsackData.txt.\n"
				+ "K="+(redSpace.length-1)+". The case that k=K+1 thus is the case k="+redSpace.length+".\n\n"; 

		for (TypeInfo t : types) {
			double d1 = t.getSizeLB().doubleValue();
			double d2 = t.getBlueWeight().doubleValue();
			double d3 = t.getRedWeight().doubleValue();
			double d4 = t.getBlueWeight().add(t.getRedWeight()).doubleValue();
			int l = t.getLeaves();
			int n = t.getNeeds();

			BigFraction[] weight = new BigFraction[redSpace.length+1];
			for (int k = 0; k<redSpace.length; ++k) {
				if (y3Values[k]==null) {
					weight[k] = null;
				} else {
					BigFraction a = BigFraction.ONE.subtract(y3Values[k]);
					BigFraction b = y3Values[k];
					boolean simpleDualLP = redSpace[k].compareTo(BigFraction.ONE_THIRD)<=0 || computeWeightOfQ1(computeTypeOfRFromClass(k)).compareTo(targetRatio)<0;
					if (simpleDualLP)
						weight[k] = t.getWeightW(k).multiply(a).add(t.getWeightV(k).multiply(b));
					else
						weight[k] = t.computeOmega(types[computeTypeOfRFromClass(k)], y1Values[k], y2Values[k], y3Values[k]);
				}
			}
			weight[redSpace.length] = t.getBlueWeight();
			s += String.format("%.6f; %.5f; %.5f; %.5f; "+l+"; "+n+";   ", d1, d2, d3, d4) + getStringForWeightFunction(weight) + "\n";
		}

		weightsWriter.write(s);
		weightsWriter.flush();

		BigFraction[] weightsLarge = new BigFraction[redSpace.length];
		for (int k = 0; k<redSpace.length; ++k) {
			if (y3Values[k]==null) weightsLarge[k] = null;
			else weightsLarge[k] = BigFraction.ONE.subtract(y3Values[k]);
		}
		s = "\n\nConsider values of k such that r is medium. We add three large types and give for each the size lower bound, w-weight and v-weight, and finally the knapsack weight for all values of k: \n"
				+ "       2/3; 1; 1;    1 for all k\n"
				+ "1-t_{t(r)}; 1; 1;    1 for all k\n"
				+ "       1/2; 1; 0;    "+getStringForWeightFunction(weightsLarge)+"\n";
		weightsWriter.write(s);
		weightsWriter.flush();

		weightsLarge = new BigFraction[redSpace.length+1];
		for (int k = 0; k<redSpace.length; ++k) {
			if (y3Values[k]==null) weightsLarge[k] = null;
			else weightsLarge[k] = BigFraction.ONE.subtract(y3Values[k]);
		}
		weightsLarge[redSpace.length] = BigFraction.ONE; //(1-red)/bluefit is always 1 for large items
		s = "\n\nConsider values of k such that r is small or k=K+1. We add two large types and give for each the size lower bound, w-weight and v-weight, and finally the knapsack weight for all values of k:\n"
				+ "2/3; 1; 1;    1 for all k\n"
				+ "1/2; 1; 0;    "+getStringForWeightFunction(weightsLarge)+"\n";
		weightsWriter.write(s);
		weightsWriter.flush();
	}

	/**
	 * This method is for writing the weights to the file knapsackData.txt
	 */
	private void writeKnapsackFile(int k, BigFraction[] sizeLB, BigFraction[] weights) throws IOException {
		knapsackOutputWriter.write("Knapsack data for k = " + k + "\n");
		knapsackOutputWriter.write("sizes = [");

		for (int i = 0; i<sizeLB.length; ++i) {
			if (i>0) knapsackOutputWriter.write(", ");
			knapsackOutputWriter.write(sizeLB[i].toString());
		}
		knapsackOutputWriter.write("]\nweights = [");
		for (int i = 0; i<weights.length; ++i) {
			if (i>0) knapsackOutputWriter.write(", ");
			knapsackOutputWriter.write(weights[i].toString());
		}
		knapsackOutputWriter.write("]\n");
	}

	private String getStringForWeightFunction(BigFraction[] weight) {
		String res = "";
		int i = 0;
		int last = -1;
		while (i<weight.length) {
			BigFraction current = weight[i];
			if (weight[i]==null && i<redSpace.length && !redSpace[i].equals(BigFraction.ONE_THIRD) && !redSpace[i].equals(BigFraction.ZERO))
				throw new IllegalStateException();
			if (weight[i]==null) { //in this case, y3==null because we did not need to consider this case
				++i;
				continue;
			}
			while (i<weight.length && (weight[i]==null || weight[i].equals(current)))
				++i;
			res = res + (res.isEmpty() ? "" : ",   ") + String.format("%.5f for ", i<weight.length ? weight[i].doubleValue() : weight[i-1].doubleValue());
			if (last<0) res += "k < "+i;
			else if (last==i-1) res += "k = "+ last;
			else res += last + " <= k < " + i;
			last = i;
		}
		return res;
	}
}

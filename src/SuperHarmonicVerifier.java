import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.math3.fraction.BigFraction;


/**
 * 
 * This class is for checking whether a given set of parameters for SuperHarmonic achieves a specific competitive ratio.
 * Running this class requires a FULL set of parameters, that is, item types (each with size, red fraction,
 * bluefit, redfit, needs, leaves), red spaces, and values for y3 for all cases. 
 *
 */
public class SuperHarmonicVerifier extends DualLPChecker {

	private BufferedWriter knapsackOutputWriter;
	private BufferedWriter weightsWriter;

	public static void main(String[] args) throws IOException {
		SuperHarmonicVerifier verifier = new SuperHarmonicVerifier(args==null || args.length==0 ? null : args[0]);
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



	private BigFraction[] y3Values; //index denotes the value of k

	//this is for speeding up the knapsack solver
	private BigFraction patternWeightThreshold;



	/**
	 * Reads input file and initializes everything needed for running the verification.
	 */
	public SuperHarmonicVerifier(String inputFile) throws IOException {
		//initialize logging
		knapsackOutputWriter = new BufferedWriter(new FileWriter("knapsackData.txt"));
		writer = new BufferedWriter(new FileWriter("protocol_SuperHarmonic.txt"));
		weightsWriter = new BufferedWriter(new FileWriter("weights.txt"));

		//read all the input data, initialize all the arrays etc.
		if (inputFile==null || inputFile.equals("improved")) initialize(Util.shvpFileName);
		else if (inputFile.equals("original")) initialize(Util.hppvpFileName);
		else initialize(inputFile);

		//write type information to a file
		writeTypeInformation();
	}

	/**
	 * Initialize all parameters by reading the input file given. This given file name must not
	 * be null.
	 */
	private void initialize(String inputFile) throws IOException {
		FileIO io = new FileIO();
		log("Preparation: reading parameters from file");

		//read parameters from the input file
		io.readSHVerifierInput(inputFile);
		this.types = io.getTypes();
		this.targetRatio = io.getTargetRatio();
		this.redSpace = io.getRedSpaces();
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
		BigFraction[] allSizes = new BigFraction[types.length];
		BigFraction[] weights = new BigFraction[types.length];
		for (int i = 0; i<types.length; ++i) {
			allSizes[i] = types[i].getSizeLB();
			//weights are (1-red)/bluefit
			weights[i] = types[i].getBlueWeight();
		}

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

		BigFraction[] allSizes = new BigFraction[types.length];
		BigFraction[] weights = new BigFraction[allSizes.length];
		for (int i = 0; i<types.length; ++i) {
			allSizes[i] = types[i].getSizeLB();
			//weights in this case are (1-y3)*w + y3*v
			weights[i] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
		}

		//check feasibility of the dual LP
		KnapsackPattern p = checkSimpleDualLP(allSizes, weights);

		//output the findings
		if (p!=null)
			log(String.format("\tHeaviest pattern is " + p + "\n\twith weights " + p.weightString() + "\n\tand total weight %.5f", p.getTotalWeightInclSand(sandExpansion).doubleValue()));
		else
			log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
		return p;
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

}

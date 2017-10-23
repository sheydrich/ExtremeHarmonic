import java.io.IOException;

import org.apache.commons.math3.fraction.BigFraction;


/**
 * 
 * Given a complete set of parameters (item types (each with size, red fraction, bluefit, redfit, needs, 
 * leaves) and red spaces) and a target competitive ratio, this class conducts K binary searches  
 * for y3-values that make the dual LPs feasible.
 *
 */
public class BinarySearch extends DualLPChecker {
	
	public static void main(String[] args) throws IOException {
		BinarySearch bs = new BinarySearch(args==null || args.length==0 ? null : args[0]);
		bs.start();
	}



	/*
	 * This class inherits fields from its parent DualLPChecker that contain the following information:
	 * - types
	 * - red spaces
	 * - target ratio
	 * - expansion of sand items
	 */


	
	
	/**
	 * These fields will contain the results of the search.
	 */
	private BigFraction[] y1Values; //index denotes the value of k
	private BigFraction[] y2Values; //index denotes the value of k
	private BigFraction[] y3Values; //index denotes the value of k
	private static long startTime = System.nanoTime();    
	
	private final String inputFile;

	public BinarySearch(String inputFile) throws IOException {
		this.inputFile = inputFile;
		//read all the input data, initialize all the arrays etc.
		initialize(inputFile==null ? Util.bspFileName : inputFile);
		showTime();
	}

	private void initialize(String inputFile) throws IOException {
		FileIO io = new FileIO();
		log("Preparation: reading parameters from file");
		io.readBinarySearchInput(inputFile);

		this.types = io.getTypes();
		this.targetRatio = io.getTargetRatio();
		this.redSpace = io.getRedSpaces();
		
		
		this.y1Values = new BigFraction[redSpace.length];
		this.y2Values = new BigFraction[redSpace.length];
		this.y3Values = new BigFraction[redSpace.length];

		//compute some additional parameters
		this.sandExpansion = BigFraction.ONE.divide(BigFraction.ONE.subtract(types[types.length-1].getSizeLB()));
		for (int i = 0; i<types.length; ++i)
			types[i].computeWeights();
		log("\n\tTARGET RATIO: "+targetRatio.doubleValue()+"\n--------------------------------------------");
	}
	
	/**
	 * This method starts the binary search itself.
	 */
	private void start() throws IOException {
		log("--------------------------------------------");
		log("Starting the binary searches for values of y3 for all k.");
		log("--------------------------------------------\n");

		for (int k = 0; k<redSpace.length; ++k) {
			if (!isNecessaryToCheckCase(k)) continue; //if this value of k is impossible (no item of this red class), skip it

			//the initial search space is the interval [0, 3/8]
			//this is a heuristic; it should be [0, 1/2] but y3 never ends up in that range
			//the reason for picking 3/8 is that y3 = 3/16 works in the great majority of cases
			BigFraction[] ellipsoid = {BigFraction.ZERO, new BigFraction(3,8)};
			//the initial y3 value is 3/16
			BigFraction y3_center = new BigFraction(3,16);
			
			//store the difference to the last y3 value tried; this is for stopping the search when 
			//it changes only marginally
			BigFraction y3_diff = BigFraction.ONE;
			//also, count the number of iterations; we also stop after a maximum number of iterations
			int iter = 0;
			int maxIter = 20;
			
			KnapsackPattern maxWeightPattern = null;
			while (iter<maxIter && y3_diff.compareTo(new BigFraction(1, 10000000))>0) {
				iter++;
				//test next y3-value: check feasibility of the dual LP
				maxWeightPattern = checkDualLP(k, y3_center);
				
				// GOOD CASE: the dual LP is feasible, so we can stop the search for this case and store the y3-value
				if (maxWeightPattern==null || maxWeightPattern.getTotalWeightInclSand(sandExpansion).compareTo(targetRatio)<=0) {
					log("\tFeasible!");
					y3Values[k] = y3_center;
					break;
				} else {
					//LP is infeasible: heaviestPattern contains the pattern that violates the constraint, 
					//i.e. the pattern that has weight larger than y4
					log("\tHeaviest pattern: " + maxWeightPattern);
					log("\t\tWeights: " + maxWeightPattern.weightString());
					BigFraction diff = maxWeightPattern.getTotalWeightWInclSand(types, k, sandExpansion)
							.subtract(maxWeightPattern.getTotalWeightVInclSand(types, k, sandExpansion));
					if (diff.compareTo(BigFraction.ZERO)>0) {//change the search interval for y3
						ellipsoid[0] = y3_center;
					} else {
						ellipsoid[1] = y3_center;
					}

					//compute the new center of the interval (i.e., the new value for y3 to test)
					//and the difference to the old center (for stopping the search at some point)
					BigFraction newCenter = ellipsoid[0].add(ellipsoid[1].subtract(ellipsoid[0]).divide(2));
					y3_diff = newCenter.subtract(y3_center).abs();
					y3_center = newCenter;
				}
			}
			if (maxWeightPattern!=null && maxWeightPattern.getTotalWeightInclSand(sandExpansion).compareTo(targetRatio)>0) {
				//we stopped the search without finding a feasible y3-value: stop the program
				log("Couldn't find value for y3 that makes dual LP feasible! Stopping program.");
				System.exit(0);
			}
			log(String.format("Binary search for case k=" + k + " successful! y3=%.5f found.\n\n--------------------------------------------\n", y3Values[k].doubleValue()));
			showTime();
		}
		
		//write the parameters found to a file; this can be used in the verifier then
		createOutputFile();
	}
	
	/**
	 * This method checks feasibility of the dual LP for certain values of k and y3.
	 */
	private KnapsackPattern checkDualLP(int k, BigFraction y3) throws IOException {
		//check whether r is medium or small
		boolean rIsMedium = redSpace[k].compareTo(BigFraction.ONE_THIRD)>0;

		if (!rIsMedium) { 
			
			// --------------------------------
			// r is small
			// --------------------------------
			
			log("r is small, so check feasibility of simpler dual LP; y3 = " + y3.doubleValue());

			//we need two large types: (1/2, 2/3] and (2/3, 1]
			BigFraction[] allSizes = new BigFraction[types.length+2];
			BigFraction[] weights = new BigFraction[allSizes.length];
			allSizes[0] = BigFraction.TWO_THIRDS;
			weights[0] = BigFraction.ONE; //w=v=1 in this case
			allSizes[1] = BigFraction.ONE_HALF;
			weights[1] = BigFraction.ONE.subtract(y3); //(1-y3)*w + y3*v = 1-y3 as w=1 and v=0 for this type
			for (int i = 0; i<types.length; ++i) {
				allSizes[i+2] = types[i].getSizeLB();
				//weights in this case are (1-y3)*w + y3*v
				weights[i+2] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
			}

			return checkSimpleDualLP(allSizes, weights);
		} else { 
			
			// --------------------------------
			// r is medium
			// --------------------------------
			
			
			int t = computeTypeOfRFromClass(k); //compute the type of r

			//if no items of this type are colored red, there can be no red item of class k - no need to check this case
			if (types[t].getRedFraction().equals(BigFraction.ZERO)) {
				log("No need to check this case, as no red item of class " + k + " can exist (red fraction = 0).\n\n--------------------------------------------\n");
				return null;
			}

			//compute the weight of q1 (which is also the weight of q2)
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
				
				log(String.format("r is medium and %.5f = w_{1k} < c = %.5f, so check feasibility of simpler dual LP; y3 = "+y3.doubleValue()+".\n", w1.doubleValue(), targetRatio.doubleValue()));
				
				//in this case, weights are again (1-y3)*w + y3*v
				for (int i = 0; i<types.length; ++i) {
					weights[i+3] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
				}

				return checkSimpleDualLP(allSizes, weights);
			} else {
				
				// ----------------------------------------
				// SECOND CASE: w1 >= y4, so we use the extended LP
				// ----------------------------------------
				
				log(String.format("r is medium and %.5f = w_{1k} >= c = %.5f, so check feasibility of extended dual LP; y3 = "+y3.doubleValue()+".\n", w1.doubleValue(), targetRatio.doubleValue()));

				//compute y1 and y2 from w1 and the target ratio; store these values in case that this proves to be feasible
				BigFraction y1 = w1.subtract(targetRatio);
				BigFraction y2 = w1.subtract(targetRatio).multiply(2);
				y2Values[k] = y2;
				y1Values[k] = y1;
				
				//compute weights for non-large items: this is the function omega
				for (int i = 0; i<types.length; ++i)
					weights[i+3] = types[i].computeOmega(types[t], y1, y2, y3);
				
				//the object check makes sure that patterns q1, q2 are not considered
				PatternFeasibilityCheck check = new NotQ1Q2(allSizes[1], types[t].getSizeLB()); 
				
				//check dual LP
				KnapsackSolver solver = new KnapsackSolver(allSizes, weights, check, sandExpansion);
				return solver.solve(targetRatio.subtract(new BigFraction(1,1000)));
			}
		}
	}
	
	/**
	 * This method writes the parameters of the algorithm to a file that can be used
	 * as input for the verifier.
	 */
	private void createOutputFile() throws IOException {
		String name = inputFile + ".vp";
		if (inputFile!=null && inputFile.endsWith(".bsp")) name = inputFile.substring(0, inputFile.indexOf(".bsp")) + ".vp";
		if (inputFile==null) name = Util.vpFileName;
		FileIO io = new FileIO();
		io.writeBinarySearchOutput(name, targetRatio, redSpace, types, y1Values, 
				y2Values, y3Values);
	}
	
	private void showTime() {
		System.out.println((System.nanoTime() - startTime)/1e9);
	}
}

import java.io.IOException;

import org.apache.commons.math3.fraction.BigFraction;


/**
 * 
 * Given a complete set of parameters for Super Harmonic (item types (each with size, red fraction, bluefit, redfit, needs, 
 * leaves) and red spaces) and a target competitive ratio, this class conducts K binary searches  
 * for y3-values that make the dual LPs feasible.
 *
 */
public class BinarySearchSH extends DualLPChecker {

	public static void main(String[] args) throws IOException {
		BinarySearchSH bs = new BinarySearchSH(args==null || args.length==0 ? null : args[0]);
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
	 * This field will contain the results of the search.
	 */
	private BigFraction[] y3Values; //index denotes the value of k
	private static long startTime = System.nanoTime();    

	private final String inputFile;

	public BinarySearchSH(String inputFile) throws IOException {
		//read all the input data, initialize all the arrays etc.
		if (inputFile==null || inputFile.equals("improved")) this.inputFile = Util.shbspFileName;
		else if (inputFile.equals("original")) this.inputFile = Util.hppbspFileName;
		else this.inputFile = inputFile;
		initialize(this.inputFile);
		showTime();
	}

	private void initialize(String inputFile) throws IOException {
		FileIO io = new FileIO();
		log("Preparation: reading parameters from file");
		io.readBinarySearchInput(inputFile);

		this.types = io.getTypes();
		this.targetRatio = io.getTargetRatio();
		this.redSpace = io.getRedSpaces();

		this.y3Values = new BigFraction[redSpace.length];

		//compute some additional parameters
		this.sandExpansion = BigFraction.ONE.divide(BigFraction.ONE.subtract(types[types.length-1].getSizeLB()));
		for (int i = 0; i<types.length; ++i)
			types[i].computeWeights();
		patternWeightThreshold = targetRatio.subtract(new BigFraction(1,1000));
		log("\n\tTARGET RATIO: "+targetRatio.doubleValue()+"\n--------------------------------------------");
	}

	/**
	 * This method starts the binary search itself.
	 */
	private void start() throws IOException {
		log("--------------------------------------------");
		log("Starting the binary searches for values of y3 for all k.");
		log("--------------------------------------------\n");
		
		checkKPlusOne();

		for (int k = 0; k<redSpace.length; ++k) {
			if (!isNecessaryToCheckCase(k)) continue; //if this value of k is impossible (no item of this red class), skip it
			BigFraction y3 = findY3(k);
			if (y3==null) {
				//we stopped the search without finding a feasible y3-value: stop the program
				log("Couldn't find value for y3 that makes dual LP feasible! Stopping program.");
				System.exit(0);
			}
		}

		//write the parameters found to a file; this can be used in the verifier then
		createOutputFile();
	}
	
	private BigFraction findY3(int k) throws IOException {

		BigFraction[] ellipsoid = {BigFraction.ZERO, new BigFraction(1)};
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
				log(String.format("\tFeasible! y3=%.5f", y3_center.doubleValue()));
				y3Values[k] = y3_center;
				log(String.format("Binary search for case k=" + k + " successful! y3=%.5f found.\n\n--------------------------------------------\n", y3Values[k].doubleValue()));
				showTime();
				return y3_center;
			} else {
				//LP is infeasible: maxWeightPattern contains the pattern that violates the constraint, 
				//i.e. the pattern that has weight larger than y4
				BigFraction totalW = maxWeightPattern.getTotalWeightWInclSand(types, k, sandExpansion);
				BigFraction totalV = maxWeightPattern.getTotalWeightVInclSand(types, k, sandExpansion);
				BigFraction diff = totalW.subtract(totalV);
				if (diff.compareTo(BigFraction.ZERO)==0) {
					//we can't do anything! weight is equal for w and v, so too high for every y3
					break;
				} else if (diff.compareTo(BigFraction.ZERO)>0) {//change the search interval for y3
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
		
		return null;
	}

	/**
	 * This method checks feasibility of the dual LP for certain values of k and y3.
	 */
	@Override
	protected KnapsackPattern checkDualLP(int k, BigFraction y3) throws IOException {
		BigFraction[] allSizes = new BigFraction[types.length];
		BigFraction[] weights = new BigFraction[allSizes.length];
		for (int i = 0; i<types.length; ++i) {
			allSizes[i] = types[i].getSizeLB();
			//weights in this case are (1-y3)*w + y3*v
			weights[i] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
		}

		return checkSimpleDualLP(allSizes, weights);
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
		io.writeSHBinarySearchOutput(name, targetRatio, redSpace, types, y3Values);
	}

	private void showTime() {
		System.out.println((System.nanoTime() - startTime)/1e9);
	}

	@Override
	protected BigFraction checkY1(int k, BigFraction w1) {
		return null;
	}

	@Override
	protected BigFraction checkY2(int k, BigFraction w1) {
		return null;
	}

	@Override
	protected void checkY3(BigFraction y3, int k) {
		//nothing to do here
	}

	@Override
	protected void writeKnapsackFile(int k, BigFraction[] sizes,
			BigFraction[] weights) throws IOException {
		//nothing to do here
	}
}

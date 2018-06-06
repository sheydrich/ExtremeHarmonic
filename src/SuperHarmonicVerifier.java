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
public class SuperHarmonicVerifier extends Verifier {

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
	
	@Override
	protected void readInput(String inputFile, FileIO io) throws IOException {
		io.readSHVerifierInput(inputFile);
	}

	/**
	 * This method checks a case where k<K+1.
	 * It returns the heaviest pattern for this case.
	 * 
	 */
	@Override
	protected KnapsackPattern checkDualLP(int k, BigFraction y3) throws IOException {
		checkY3(y3, k);

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
			log(String.format("\tHeaviest pattern is " + getOutputWeightString(k, p) + "\n\twith weights " + p.weightString() + "\n\tand total weight %.5f", p.getTotalWeightInclSand(sandExpansion).doubleValue()));
		else
			log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
		return p;
	}

	@Override
	protected BigFraction getFirstTypeUpperBound() {
		// TODO Auto-generated method stub
		return BigFraction.ONE;
	}
}

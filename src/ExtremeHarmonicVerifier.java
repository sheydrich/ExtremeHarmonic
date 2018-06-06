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
public class ExtremeHarmonicVerifier extends Verifier {

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

	/**
	 * Reads input file and initializes everything needed for running the verification.
	 */
	public ExtremeHarmonicVerifier(String inputFile) throws IOException {
		//initialize logging
		knapsackOutputWriter = new BufferedWriter(new FileWriter("knapsackData_EH.txt"));
		writer = new BufferedWriter(new FileWriter("protocol_ExtremeHarmonic.txt"));
		weightsWriter = new BufferedWriter(new FileWriter("weights.txt"));

		//read all the input data, initialize all the arrays etc.
		initialize(inputFile==null ? Util.vpFileName : inputFile);

		//write type information to a file
		writeTypeInformation();
		writeWeights();
	}
	
	@Override
	protected void readInput(String inputFile, FileIO io) throws IOException {
		io.readVerifierInput(inputFile);
	}

	@Override
	protected BigFraction getFirstTypeUpperBound() {
		// TODO Auto-generated method stub
		return BigFraction.ONE_HALF;
	}
}

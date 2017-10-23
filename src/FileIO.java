import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.fraction.BigFraction;

/**
 * This is a helper class for all the file I/O needed.
 *
 */
public class FileIO {

	private TypeInfo[] types;
	private BigFraction targetRatio;
	private BigFraction[] redSpaces;
	
	private BigFraction[] y1;
	private BigFraction[] y2;
	private BigFraction[] y3;

	private BigFraction minItemSize;
	private BigFraction redSpaceBound;
	private BigFraction sizeForRedSpaceBound;
	private BigFraction lastTypeBeforeSmallTypeGeneration;
	private boolean computeRedfitLikeSeiden;
	
	/**
	 * Check whether the preferred output file is available (i.e. the one we reached our bound with).
	 * If not, ask the user for an input file.
	 */
	private static File getFile(String name) throws IOException{
		File result = new File(name);
		if (result.exists()) 
			System.out.println("Starting computations with input file " + result.getName());
		else {
			//if not: ask the user for the file (which must exist, so ask as long as it does not exist)
			System.out.println("Default input file not found.");
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			while (!result.exists()) {
				System.out.println("Please enter the path to the parameter file that should be used:");
				String s = in.readLine();
				result = new File(s);
				if (!result.exists()) System.out.println("File not found.");
			}
			in.close();
		}
		return result;
	}

	/**
	 * This method reads an input file for the verifier.
	 * After this method terminates, the following parameters of this object are set:
	 * - target ratio
	 * - redSpace values
	 * - y1, y2, and y3 values
	 * - types with all their parameters
	 */
	public void readVerifierInput(String file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(getFile(file)));
		try {

			readTargetRatio(reader, 1);
			readRedspaces(reader, 2);
			readY1Values(reader, 3);
			readY2Values(reader, 4);
			readY3Values(reader, 5);
			readTypesWithAllParameters(reader, 6);

		} finally {
			reader.close();
		}
	}

	/**
	 * This method reads an input file for the verifier of Super Harmonic.
	 * After this method terminates, the following parameters of this object are set:
	 * - target ratio
	 * - redSpace values
	 * - y3 values
	 * - types with all their parameters
	 */
	public void readSHVerifierInput(String file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(getFile(file)));
		try {

			readTargetRatio(reader, 1);
			readRedspaces(reader, 2);
			readY3Values(reader, 3);
			readTypesWithAllParameters(reader, 4);

		} finally {
			reader.close();
		}
	}

	/**
	 * This method reads an input file for the binary search.
	 * After this method terminates, the following parameters of this object are set:
	 * - target ratio
	 * - redSpace values
	 * - types with all their parameters
	 */
	public void readBinarySearchInput(String file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(getFile(file)));
		try {

			readTargetRatio(reader, 1);
			readRedspaces(reader, 2);
			readTypesWithAllParameters(reader, 3);

		} finally {
			reader.close();
		}
	}

	/**
	 * This method reads an input file for the parameter optimizer.
	 * After this method terminates, the following parameters of this object are set:
	 * - target ratio
	 * - redSpace values
	 * - predefined types with red fractions
	 * - smallestType
	 * - gammaBound
	 * - lastTypeBeforeSmallTypeGeneration
	 */
	public void readParameterOptimizerInput(String inputFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(getFile(inputFile)));
		try {
			readTargetRatio(reader, 1);
			readSmallestType(reader, 2);
			readGammaBound(reader, 3);
			readLastTypeBeforeSmallTypeGeneration(reader, 4);
			readSingleTypes(reader, 5);
		} finally {
			reader.close();
		}
	}

	/**
	 * This method reads an input file for the parameter optimizer.
	 * After this method terminates, the following parameters of this object are set:
	 * - target ratio
	 * - redSpace values
	 * - predefined types with red fractions
	 * - smallestType
	 * - gammaBound
	 * - lastTypeBeforeSmallTypeGeneration
	 * - computeRedfitLikeSeiden
	 */
	public void readSHParameterOptimizerInput(String inputFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(getFile(inputFile)));
		try {
			readTargetRatio(reader, 1);
			readSmallestType(reader, 2);
			readRedspaces(reader, 3);
			readComputeRedfitLikeSeiden(reader, 4);
			readGammaBoundSH(reader, 5);
			readLastTypeBeforeSmallTypeGeneration(reader, 4);
			readSingleTypes(reader, 5);
		} finally {
			reader.close();
		}
	}
	
	private void readComputeRedfitLikeSeiden(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("computeRedfitLikeSeiden:"))
			throw new IllegalStateException("INVALID INPUT FILE! Line "+i+" should be of the form 'computeRedfitLikeSeiden:bool'");
		s = s.substring("computeRedfitLikeSeiden:".length());
		if (!s.equals("true") && !s.equals("false"))
			throw new IllegalStateException("INVALID INPUT FILE! Line "+i+" should be of the form 'computeRedfitLikeSeiden:bool'");
		computeRedfitLikeSeiden = s.equals("true");
	}

	private void readTypesWithAllParameters(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.equals("Types: [size, red fraction, bluefit, redfit, needs, leaves]"))
			throw new IllegalStateException("INVALID INPUT FILE! Line "+i+" should be 'Types: [size, red fraction, bluefit, redfit, needs, leaves]'");
		s = reader.readLine();
		List<TypeInfo> list = new LinkedList<>();
		while (s!=null) {
			TypeInfo t = readSingleTypeFull(s);
			list.add(t);
			s = reader.readLine();
		}
		types = new TypeInfo[list.size()];
		for (int j = 0; j<list.size(); ++j)
			types[j] = list.get(j);
	}

	private TypeInfo readSingleTypeFull(String s) {
		String[] arr = s.split(";");
		if (arr.length!=6) throw new IllegalStateException("INVALID INPUT FILE! Type should have 6 values, but has " + arr.length);
		BigFraction size = readBF(arr[0].trim());
		BigFraction red = readBF(arr[1].trim());
		int bf = Integer.parseInt(arr[2].trim());
		int rf = Integer.parseInt(arr[3].trim());
		int n = Integer.parseInt(arr[4].trim());
		int l = Integer.parseInt(arr[5].trim());
		TypeInfo t = new TypeInfo(size, red);
		t.setBluefit(bf);
		t.setRedfit(rf);
		t.setNeeds(n);
		t.setLeaves(l);
		return t;
	}

	private BigFraction readBF(String string) {
		if (!string.contains("/")) {
			if (string.equals("0")) return BigFraction.ZERO;
			throw new IllegalArgumentException("Illegal BigFraction value! " + string);
		}
		String[] arr = string.split("/");
		if (arr.length!=2)
			throw new IllegalArgumentException("Illegal BigFraction value! " + string);
		BigInteger num = new BigInteger(arr[0].trim());
		BigInteger den = new BigInteger(arr[1].trim());
		return new BigFraction(num,den);
	}

	private void readSingleTypes(BufferedReader reader, int i) throws IOException {
		List<TypeInfo> typesList = new LinkedList<TypeInfo>();
		
		String s = reader.readLine();
		while (s!=null) {
			if (!s.contains(";")) {
				System.out.println("INVALID INPUT FILE! Item type data should be of the form 'size;red fraction'");
				reader.close();
				System.exit(1);
			}
			String[] arr = s.split(";");
			if (arr.length!=2) {
				System.out.println("INVALID INPUT FILE! Item type data should be of the form 'size;red fraction'");
				reader.close();
				System.exit(1);
			}
			if (!arr[0].contains("/")) {
				System.out.println("INVALID INPUT FILE! Item type size should be of the form num/denom");
				reader.close();
				System.exit(1);
			}
			String[] arr2 = arr[0].trim().split("/");
			if (arr2.length!=2) {
				System.out.println("INVALID INPUT FILE! Item type size should be of the form num/denom");
				reader.close();
				System.exit(1);
			}
			int num = Integer.parseInt(arr2[0].trim());
			int denom = Integer.parseInt(arr2[1].trim());
			BigFraction newSize = new BigFraction(num, denom);
			
			BigFraction red = BigFraction.ZERO;
			//now read the red fraction; it might either be zero or a fraction
			if (arr[1].contains("/")) {
				arr2 = arr[1].trim().split("/");
				if (arr2.length!=2) {
					throw new IllegalArgumentException("INVALID INPUT FILE! Red fraction should be of the form num/denom or 0");
				}
				num = Integer.parseInt(arr2[0].trim());
				denom = Integer.parseInt(arr2[1].trim());
				red = new BigFraction(num, denom);
			} else {
				if (!arr[1].equals("0")) {
					throw new IllegalArgumentException("INVALID INPUT FILE! Red fraction should be of the form num/denom or 0");
				} else {
					red = BigFraction.ZERO;
				}
			}
			typesList.add(new TypeInfo(newSize, red));
			
			s = reader.readLine();
		}
		types = new TypeInfo[typesList.size()];
		for (int j = 0; j<typesList.size(); ++j)
			types[j] = typesList.get(j);
	}

	private void readLastTypeBeforeSmallTypeGeneration(BufferedReader reader,
			int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("lastTypeBeforeSmallTypeGeneration:")) {
			System.out.println("INVALID INPUT FILE! Fourth line should contain something of the form 'lastTypeBeforeSmallTypeGeneration:1/denom'");
			reader.close();
			System.exit(1);
		}
		if (s.equals("lastTypeBeforeSmallTypeGeneration:none")) {
			lastTypeBeforeSmallTypeGeneration = null;
		} else {
			if (!s.contains("/")) {
				System.out.println("INVALID INPUT FILE! Fourth line should contain something of the form 'lastTypeBeforeSmallTypeGeneration:1/denom'");
				reader.close();
				System.exit(1);
			}
			String[] arr = s.substring(s.indexOf(":")+1).split("/");
			if (arr.length!=2) {
				System.out.println("INVALID INPUT FILE! Fourth line should contain something of the form 'lastTypeBeforeSmallTypeGeneration:1/denom'");
				reader.close();
				System.exit(1);
			}
			lastTypeBeforeSmallTypeGeneration = new BigFraction(Integer.parseInt(arr[0].trim()), Integer.parseInt(arr[1].trim()));
		}
	}

	private void readGammaBound(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("gammaBound2:")) {
			System.out.println("INVALID INPUT FILE! Line "+i+" should contain the second gamma bound in the form 'gammaBound:num/denom[num/denom]'");
			reader.close();
			System.exit(1);
		}
		s = s.substring(s.indexOf(":")+1);
		if (!s.contains("/") || !s.contains("[") || !s.contains("]")) {
			System.out.println("INVALID INPUT FILE! Line "+i+" should contain the second gamma bound in the form 'gammaBound:num/denom[num/denom]'");
			reader.close();
			System.exit(1);
		}
		int j = s.indexOf("[");
		String s1 = s.substring(0, j); //this is the second gamma bound
		String[] arr = s1.split("/");
		if (arr.length!=2) {
			System.out.println("INVALID INPUT FILE! Line "+i+" should contain the second gamma bound in the form 'gammaBound:num/denom[num/denom]'");
			reader.close();
			System.exit(1);
		}
		redSpaceBound = new BigFraction(Integer.parseInt(arr[0].trim()), Integer.parseInt(arr[1].trim()));

		String s2 = s.substring(j+1, s.indexOf("]")); //this is the size where to start to use this gamma bound
		arr = s2.split("/");
		if (arr.length!=2) {
			System.out.println("INVALID INPUT FILE! Line "+i+" should contain the second gamma bound in the form 'gammaBound:num/denom[num/denom]'");
			reader.close();
			System.exit(1);
		}
		sizeForRedSpaceBound = new BigFraction(Integer.parseInt(arr[0].trim()), Integer.parseInt(arr[1].trim()));
	}

	private void readGammaBoundSH(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("gammaBound:")) {
			throw new IllegalArgumentException("INVALID INPUT FILE! Line "+i+" should contain the second gamma bound in the form 'gammaBound:num/denom'");
		}
		s = s.substring(s.indexOf(":")+1);
		if (s.equals("-")) {
			redSpaceBound = null;
			return;
		}
		if (!s.contains("/")) {
			throw new IllegalArgumentException("INVALID INPUT FILE! Line "+i+" should contain the second gamma bound in the form 'gammaBound:num/denom'");
		}
		String[] arr = s.split("/");
		if (arr.length!=2) {
			throw new IllegalArgumentException("INVALID INPUT FILE! Line "+i+" should contain the second gamma bound in the form 'gammaBound:num/denom'");
		}
		redSpaceBound = new BigFraction(Integer.parseInt(arr[0].trim()), Integer.parseInt(arr[1].trim()));
	}

	private void readSmallestType(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("smallestType:")) {
			System.out.println("INVALID INPUT FILE! Line "+i+" should be 'smallestType:1/int'");
			reader.close();
			System.exit(1);
		}
		int reciprocalOfMinItemSize = Integer.parseInt(s.substring(s.indexOf(":")+3).trim());
		minItemSize = new BigFraction(1, reciprocalOfMinItemSize);
	}

	private void readY1Values(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("DualSolutionY1:")) 
			throw new IllegalStateException("INVALID INPUT FILE: Line "+i+" must contain y1 values of the dual solutions as 'DualSolutionY1:num/denom;num/denom;...;num/denom'");
		s = s.substring(s.indexOf(":")+1);
		y1 = readBigFractionArray(s);
		if (y1.length!=redSpaces.length) throw new IllegalStateException("INVALID INPUT FILE! Number of y1-values is " + y1.length + ", but number of red spaces is " + redSpaces.length);
	}

	private void readY2Values(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("DualSolutionY2:")) 
			throw new IllegalStateException("INVALID INPUT FILE: Line "+i+" must contain y2 values of the dual solutions as 'DualSolutionY2:num/denom;num/denom;...;num/denom'");
		s = s.substring(s.indexOf(":")+1);
		y2 = readBigFractionArray(s);
		if (y2.length!=redSpaces.length) throw new IllegalStateException("INVALID INPUT FILE! Number of y2-values is " + y2.length + ", but number of red spaces is " + redSpaces.length);
	}

	private void readY3Values(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("DualSolutionY3:")) 
			throw new IllegalStateException("INVALID INPUT FILE: Line "+i+" must contain y3 values of the dual solutions as 'DualSolutionY3:num/denom;num/denom;...;num/denom'");
		s = s.substring(s.indexOf(":")+1);
		y3 = readBigFractionArray(s);
		if (y3.length!=redSpaces.length) throw new IllegalStateException("INVALID INPUT FILE! Number of y3-values is " + y3.length + ", but number of red spaces is " + redSpaces.length);
	}

	private void readTargetRatio(BufferedReader reader, int i) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("Goal:"))
			throw new IllegalStateException("INVALID INPUT FILE: Line "+i+" must contain target competitive ratio as 'Goal:num/denom'");
		s = s.substring(s.indexOf(":")+1);
		targetRatio = readBigFractionValue(s);		
	}

	private void readRedspaces(BufferedReader reader, int lineNumber) throws IOException {
		String s = reader.readLine();
		if (!s.startsWith("RedSpace:"))
			throw new IllegalStateException("INVALID INPUT FILE: Line "+lineNumber+" must contain redSpace-values as 'RedSpace:num/denom;num/denom;...;num/denom'");
		s = s.substring(s.indexOf(":")+1);
		redSpaces = readBigFractionArray(s);		
	}

	private BigFraction readBigFractionValue(String s) {
		String[] arr = s.split("/");
		return new BigFraction(Long.parseLong(arr[0].trim()), Long.parseLong(arr[1].trim()));
	}

	private BigFraction[] readBigFractionArray(String s) {
		String[] arr = s.split(";");
		int n = arr.length;
		BigFraction[] result = new BigFraction[n];
		for (int i = 0; i<n; ++i) {
			if (arr[i].trim().equals("none")) {
				result[i] = null;
			} else {
				int j = arr[i].indexOf("/");
				if (j==-1) {
					if (!arr[i].equals("0"))
						throw new IllegalStateException("Error when reading verifier input: Expected series of fractional values ('int/int' or '0', separated by ';'), but found: " + s);
					result[i] = BigFraction.ZERO;
				} else {
					String s1 = arr[i].substring(0, j).trim();
					String s2 = arr[i].substring(j+1).trim();
					result[i] = new BigFraction(Long.parseLong(s1), Long.parseLong(s2));
				}
			}
		}
		return result;
	}

	public TypeInfo[] getTypes() {
		return types;
	}

	public BigFraction getTargetRatio() {
		return targetRatio;
	}

	public BigFraction[] getRedSpaces() {
		return redSpaces;
	}

	public BigFraction[] getY1Values() {
		return y1;
	}

	public BigFraction[] getY2Values() {
		return y2;
	}

	public BigFraction[] getY3Values() {
		return y3;
	}

	public BigFraction getSmallestType() {
		return minItemSize;
	}

	public BigFraction getLastTypeBeforeSmallTypeGeneration() {
		return lastTypeBeforeSmallTypeGeneration;
	}

	public BigFraction getRedSpaceBound() {
		return redSpaceBound;
	}

	public BigFraction getSizeForRedSpaceBound() {
		return sizeForRedSpaceBound;
	}
	
	public boolean getComputeRedfitLikeSeiden() {
		return computeRedfitLikeSeiden;
	}

	/**
	 * This method writes the output of the binary search to a file that can be used as input for the verifier.
	 */
	public void writeBinarySearchOutput(String file, BigFraction targetRatio, BigFraction[] redSpace, TypeInfo[] types, 
			BigFraction[] allY1, BigFraction[] allY2, BigFraction[] allY3) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write("Goal:" + targetRatio.toString());
		writer.write("\nRedSpace:");
		for (BigFraction rs : redSpace)
			writer.write(rs.toString()+";");
		writer.write("\nDualSolutionY1:");
		for (int i = 0; i<allY1.length; ++i)
			writer.write(allY1[i]==null ? "none;" : allY1[i].toString()+";");
		writer.write("\nDualSolutionY2:");
		for (int i = 0; i<allY2.length; ++i)
			writer.write(allY2[i]==null ? "none;" : allY2[i].toString()+";");
		writer.write("\nDualSolutionY3:");
		for (int i = 0; i<allY3.length; ++i)
			writer.write(allY3[i]==null ? "none;" : allY3[i].toString()+";");
		writer.write("\nTypes: [size, red fraction, bluefit, redfit, needs, leaves]\n");
		for (TypeInfo t : types)
			writer.write(t.getSizeLB() + ";" + t.getRedFraction() + ";" + t.getBluefit() + ";" + t.getRedfit() + ";" + t.getNeeds() + ";" + t.getLeaves() + "\n");
		writer.close();
	}

	/**
	 * This method writes the output of the binary search to a file that can be used as input for the verifier.
	 */
	public void writeSHBinarySearchOutput(String file, BigFraction targetRatio, BigFraction[] redSpace, TypeInfo[] types, 
			BigFraction[] allY3) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write("Goal:" + targetRatio.toString());
		writer.write("\nRedSpace:");
		for (BigFraction rs : redSpace)
			writer.write(rs.toString()+";");
		writer.write("\nDualSolutionY3:");
		for (int i = 0; i<allY3.length; ++i)
			writer.write(allY3[i]==null ? "none;" : allY3[i].toString()+";");
		writer.write("\nTypes: [size, red fraction, bluefit, redfit, needs, leaves]\n");
		for (TypeInfo t : types)
			writer.write(t.getSizeLB() + ";" + t.getRedFraction() + ";" + t.getBluefit() + ";" + t.getRedfit() + ";" + t.getNeeds() + ";" + t.getLeaves() + "\n");
		writer.close();
	}

	/**
	 * This method writes the output of the parameter optimizer to a file 
	 * that can be used as input for the binary search.
	 */
	public void writeParameterOptimizationOutput(String file, BigFraction targetRatio, List<BigFraction> redSpace, 
			List<TypeInfo> types) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write("Goal:" + targetRatio.toString());
		writer.write("\nRedSpace:");
		for (BigFraction rs : redSpace)
			writer.write(rs.toString()+";");
		writer.write("\nTypes: [size, red fraction, bluefit, redfit, needs, leaves]\n");
		for (TypeInfo t : types)
			writer.write(t.getSizeLB() + ";" + t.getRedFraction() + ";" + t.getBluefit() + ";" + t.getRedfit() + ";" + t.getNeeds() + ";" + t.getLeaves() + "\n");
		writer.close();
	}
}

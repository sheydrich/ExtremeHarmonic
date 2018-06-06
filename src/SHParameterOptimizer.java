import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.fraction.BigFraction;


/**
 * 
 * This class determines, given some manually defined input parameters and a target competitive ratio, 
 * a full set of input parameters using heuristics.
 *
 */
public class SHParameterOptimizer {


	public static void main(String[] args) throws IOException {
		new SHParameterOptimizer(args==null || args.length==0 ? null : args[0]).start();
	}






	private final List<TypeInfo> types = new LinkedList<>();
	private final List<BigFraction> redSpaces = new LinkedList<>();
	private BigFraction targetRatio;
	public BigFraction sandExpansion;

	private BigFraction gammaBound;
	private BigFraction lastTypeBeforeSmallTypeGeneration;
	private BigFraction smallestType;
	private boolean computeRedfitLikeSeiden;

	private final String inputFile;


	private final Comparator<? super TypeInfo> typeComparator = new Comparator<TypeInfo>(){

		@Override
		public int compare(TypeInfo t1, TypeInfo t2) {
			return t2.getSizeLB().compareTo(t1.getSizeLB());
		}
	};
	private final Comparator<? super BigFraction> bigFracAsc = new Comparator<BigFraction>() {

		@Override
		public int compare(BigFraction b1, BigFraction b2) {
			return b1.compareTo(b2);
		}
	};

	public SHParameterOptimizer(String inputFile) throws IOException {
		//read all the input data, initialize all the arrays etc.
		if (inputFile==null || inputFile.equals("improved")) this.inputFile = Util.shpopFileName;
		else if (inputFile.equals("original")) this.inputFile = Util.hpppopFileName; 
		else this.inputFile = inputFile;
		initialize(this.inputFile);
	}

	private void initialize(String inputFile) throws IOException {
		FileIO io = new FileIO();
		io.readSHParameterOptimizerInput(inputFile);
		this.targetRatio = io.getTargetRatio();
		this.smallestType = io.getSmallestType();
		this.sandExpansion = BigFraction.ONE.subtract(smallestType).reciprocal();
		this.lastTypeBeforeSmallTypeGeneration = io.getLastTypeBeforeSmallTypeGeneration();
		this.computeRedfitLikeSeiden = io.getComputeRedfitLikeSeiden();
		this.gammaBound = io.getRedSpaceBound();
		for (TypeInfo t : io.getTypes())
			this.types.add(t);
		for (BigFraction rs : io.getRedSpaces())
			this.redSpaces.add(rs);
	}

	private void start() throws IOException {
		//First, generate additional types and all redSpace values; furthermore, adjust some of the red fractions
		System.out.println("Finished reading input parameters. Now starting with auto-generation of types and red fractions.\n");
		System.out.println("------------------------------------------------------------------------------\n");

		addOneOverITypesBetweenGivenTypes();
		addMediumTypesToRedSpaces();
		addVerySmallTypes();
		addOneOverITypesUpToLastTypeBeforeSmallTypeGeneration();

		types.sort(typeComparator);
		redSpaces.add(BigFraction.ZERO);
		redSpaces.sort(bigFracAsc);

		//Second, compute all parameters: bluefit, redfit, leaves, needs
		TypeInfo last = null;
		for (TypeInfo t : types) {
			BigFraction sizeUB = last==null ? BigFraction.ONE : last.getSizeLB();
			int b = computeBluefit(sizeUB);
			t.setBluefit(b);
			int r = t.getRedFraction().equals(BigFraction.ZERO) ? 0 : computeRedfit(sizeUB);
			t.setRedfit(r);
			int l = computeLeaves(sizeUB, b);
			t.setLeaves(l);
			int n = computeNeeds(sizeUB, t.getRedFraction(), r);
			t.setNeeds(n);

			last = t;
		}
		
		System.out.println("DONE! Parameter for SuperHarmonic algorithm fully computed.");

		//Finally, write output (which is the input for the binary search program)
		createOutputFile();
	}

	private void addVerySmallTypes() {
		//compute sizes between the last type so far and the minItemSize
		//determine where to start with this; the last size before that should be of the form 1/i for some i
		if (lastTypeBeforeSmallTypeGeneration!=null) {
			System.out.println("Generating remaining sizes between the smallest size so far and the minimum item size.");
			if (lastTypeBeforeSmallTypeGeneration.getNumeratorAsInt()!=1) {
				throw new IllegalArgumentException("INVALID INPUT FILE! For continuing with 1/i-sizes, the last size before starting the small type generation must be of form 1/i as well");
			}
			if (!lastTypeBeforeSmallTypeGeneration.equals(types.get(types.size()-1).getSizeLB())) {
				throw new IllegalArgumentException("INVALID INPUT FILE! lastTypeBeforeSmallTypeGeneration must be the last size seen so far.");
			}
			int last_reciprocal = lastTypeBeforeSmallTypeGeneration.reciprocal().intValue();
			BigFraction last = new BigFraction(1, last_reciprocal);
			int k = last_reciprocal+1;
			boolean limitReached = last.equals(smallestType);
			while (!limitReached) {
				BigFraction nextSize = new BigFraction(1,k);
				TypeInfo t = new TypeInfo(nextSize, BigFraction.ZERO);
				types.add(t);
				limitReached = nextSize.equals(smallestType);
				++k;
				last = nextSize;
			}
		}
	}

	private int computeNeeds(BigFraction sizeUB, BigFraction redFrac, int redfit) {
		if (redFrac.equals(BigFraction.ZERO))
			//there are no red items of types that cannot have red items
			return 0;
		for (int j = 0; j<redSpaces.size(); ++j) {
			if (sizeUB.multiply(redfit).compareTo(redSpaces.get(j))<=0)
				return j;
		}
		throw new IllegalStateException("SEVERE ERROR! Couldn't compute needs for type with size upper bounded by " + sizeUB);
	}

	private int computeLeaves(BigFraction sizeUB, int bluefit) {
		BigFraction remainingSpace = BigFraction.ONE.subtract(sizeUB.multiply(bluefit));
		for (int j = 0; j<redSpaces.size(); ++j) {
			if (remainingSpace.compareTo(redSpaces.get(j))<0) {
				return j-1;
			}
		}
		if (redSpaces.get(redSpaces.size()-1).compareTo(remainingSpace)<=0)
			return redSpaces.size()-1;
		throw new IllegalStateException("SEVERE ERROR! Couldn't compute leaves for type with size upper bounded by " + sizeUB);
	}

	private int computeBluefit(BigFraction sizeUB) {
		return sizeUB.reciprocal().intValue();
	}

	private int computeRedfit(BigFraction sizeUB) {
		if (sizeUB.compareTo(redSpaces.get(redSpaces.size()-1))>0) 
			throw new IllegalStateException(String.format("THIS SHOULD NOT HAPPEN! No red space large enough to fit red items of type with size upper bounded by " + sizeUB + " = %.5f (max. red space = %.5f)!", sizeUB.doubleValue(), redSpaces.get(redSpaces.size()-1).doubleValue()));
		else if (computeRedfitLikeSeiden) {
			if (sizeUB.compareTo(redSpaces.get(redSpaces.size()-1))>0) {
				return 0;
			} else if (sizeUB.compareTo(redSpaces.get(0))<=0) {
				return redSpaces.get(0).divide(sizeUB).intValue();
			} else {
				return 1;
			}
		} else {
			if (sizeUB.compareTo(redSpaces.get(redSpaces.size()-1))>0) return 0;
			BigFraction localBound;
			if (gammaBound!=null) {
				localBound = gammaBound;
			} else {
				localBound = redSpaces.get(0);
			}
			int res = localBound.divide(sizeUB).intValue();
			if (res<1) return 1;
			return res;
		}
	}

	private void addOneOverITypesUpToLastTypeBeforeSmallTypeGeneration() {
		System.out.println("Generating 1/j types up to lastTypeBeforeSmallTypeGeneration.");
		
		int j = 3;
		while (new BigFraction(1, j+1).compareTo(lastTypeBeforeSmallTypeGeneration)>=0) {
			BigFraction toAdd = new BigFraction(1, j+1);
			boolean existing = false;
			for (TypeInfo t : types) if (t.getSizeLB().equals(toAdd)) {existing = true; break;}
			if (!existing) {
				System.out.println("\tAdding size " + toAdd + " (for the moment with red fraction = 0)");
				types.add(new TypeInfo(toAdd, BigFraction.ZERO));
			}
			j++;
		}
		types.sort(typeComparator);
	}

	private void addOneOverITypesBetweenGivenTypes() {
		System.out.println("Generating types of the form 1/i in between given types.");
		TypeInfo last = null;
		List<TypeInfo> newTypes = new LinkedList<TypeInfo>();
		for (TypeInfo t : types) {
			if (last==null) {
				last = t;
				continue;
			}
			BigFraction s = t.getSizeLB();
			if (s.getNumeratorAsInt()==1 && last.getSizeLB().compareTo(new BigFraction(1,s.getDenominatorAsInt()-1))<0) {
				//fill in other types, with red fraction=0 for the moment (will be adjusted later)
				int start = last.getSizeLB().getDenominatorAsInt()+1;
				while (start<s.getDenominatorAsInt()) {
					BigFraction toAdd = new BigFraction(1, start);
					newTypes.add(new TypeInfo(toAdd, BigFraction.ZERO));
					System.out.println("\tAdding type " + toAdd + " with red fraction = 0");
					++start;
				}
			}

			last = t;
		}
		for (TypeInfo t : newTypes) types.add(t);
		types.sort(typeComparator);
	}

	private void addMediumTypesToRedSpaces() {
		System.out.println("Adding medium input types to redSpaces.");
		for (TypeInfo t : types) {
			BigFraction s = t.getSizeLB();
			if (s.compareTo(BigFraction.ONE_HALF)<0 && s.compareTo(BigFraction.ONE_THIRD)>0) {
				if (!redSpaces.contains(s)){
//					redSpaces.add(s);
//					System.out.println(String.format("\t\tAdding redSpace %s = %.5f because of size %.5f", s.toString(), s.doubleValue(), s.doubleValue()));
				}
				BigFraction b = BigFraction.ONE.subtract(s.multiply(2));
				if (!redSpaces.contains(b)) {
//					redSpaces.add(b);
//					System.out.println(String.format("\t\tAdding redSpace %s = %.5f because of size %.5f", b.toString(), b.doubleValue(), s.doubleValue()));
				}

				//also, if this size is between 1/3 and 1/2 and the red fraction given in the file is zero,
				//compute an initial value for the red fraction (from the pattern 1/2, x, sand, where x is the new size, 
				//and according to the weights of the case that there is no r item); this will later be adjusted
				if (t.getRedFraction().equals(BigFraction.ZERO)) {
					BigFraction firstBoundForRed = BigFraction.ONE.subtract(targetRatio.subtract(1).subtract(BigFraction.ONE_HALF.subtract(s).multiply(sandExpansion)).multiply(2));
					if (firstBoundForRed.compareTo(BigFraction.ZERO)<0) firstBoundForRed = BigFraction.ZERO;
					if (!firstBoundForRed.equals(t.getRedFraction())) {
						t.setRedFraction(firstBoundForRed);
						System.out.println(String.format("\tAdjusted red fraction for " + s + " to %.5f (given: 0)", firstBoundForRed.doubleValue()));
					}
				}
			}
		}
		types.sort(typeComparator);
	}

	private void createOutputFile() throws IOException {
		String name = inputFile + ".bsp";
		if (inputFile!=null && inputFile.endsWith(".pop")) name = inputFile.substring(0, inputFile.indexOf(".pop")) + ".bsp";
		if (inputFile==null) name = Util.shbspFileName;
		FileIO io = new FileIO();
		io.writeParameterOptimizationOutput(name, targetRatio, redSpaces, types);
	}

}

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
public class ParameterOptimizer {


	public static void main(String[] args) throws IOException {
		new ParameterOptimizer(args==null || args.length==0 ? null : args[0]).start();
	}






	private final List<TypeInfo> types = new LinkedList<>();
	private final List<BigFraction> redSpaces = new LinkedList<>();
	private BigFraction targetRatio;
	public BigFraction sandExpansion;

	private BigFraction gammaBound;
	private BigFraction redSpaceBound;
	private BigFraction sizeForRedSpaceBound;
	private BigFraction lastTypeBeforeSmallTypeGeneration;
	private BigFraction smallestType;

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

	public ParameterOptimizer(String inputFile) throws IOException {
		this.inputFile = inputFile;
		//read all the input data, initialize all the arrays etc.
		initialize(inputFile==null ? Util.vpFileName : inputFile);
	}

	private void initialize(Object object) throws IOException {
		FileIO io = new FileIO();
		io.readParameterOptimizerInput(inputFile==null ? Util.popFileName : inputFile);
		this.targetRatio = io.getTargetRatio();
		this.smallestType = io.getSmallestType();
		this.sandExpansion = BigFraction.ONE.subtract(smallestType).reciprocal();
		this.redSpaceBound = io.getRedSpaceBound();
		this.sizeForRedSpaceBound = io.getSizeForRedSpaceBound();
		this.lastTypeBeforeSmallTypeGeneration = io.getLastTypeBeforeSmallTypeGeneration();
		this.gammaBound = BigFraction.ONE.subtract(lastTypeBeforeSmallTypeGeneration).divide(3);
		for (TypeInfo t : io.getTypes())
			this.types.add(t);
	}

	private void start() throws IOException {
		//First, generate additional types and all redSpace values; furthermore, adjust some of the red fractions
		System.out.println("Finished reading input parameters. Now starting with auto-generation of types and red fractions.\n");
		System.out.println("------------------------------------------------------------------------------\n");

		addMediumTypesToRedSpaces();
		addOneOverITypesBetweenGivenTypes();
		addOneOverITypesUpToLastTypeBeforeSmallTypeGeneration();
		addTypesDependingOnGammaBound();
		addMediumTypes();
		addSmallRedSpaces();
		addVerySmallTypes();
		adjustRedFractionOfSmallTypes();

		types.sort(typeComparator);
		redSpaces.add(BigFraction.ZERO);
		redSpaces.sort(bigFracAsc);

		//Second, compute all parameters: bluefit, redfit, leaves, needs
		TypeInfo last = null;
		for (TypeInfo t : types) {
			BigFraction sizeUB = last==null ? BigFraction.ONE_HALF : last.getSizeLB();
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
		
		//Third, eliminate unused redSpace values
		eliminateUnusedRedSpaces();
		
		System.out.println("DONE! Parameter for ExtremeHarmonic algorithm fully computed.");

		//Finally, write output (which is the input for the binary search program)
		createOutputFile();
	}

	private void eliminateUnusedRedSpaces() {
		System.out.println("Eliminating unused redSpace values.");
		boolean[] isneeded = new boolean[redSpaces.size()];
		boolean[] isleft = new boolean[redSpaces.size()];
		for(int i=0;i<types.size();i++) {
			isneeded[types.get(i).getNeeds()] = true;
			isleft[types.get(i).getLeaves()] = true;
		}
		int correction = 0;
		for (int redclass=0; redSpaces.get(redclass-correction).compareTo(BigFraction.ONE_THIRD)<0; redclass++) {
			if(!isneeded[redclass]||!isleft[redclass]){	// redclass does not correspond to the needs of any item
				System.out.println(String.format("\tRemoving red space %s = %.5f", redSpaces.get(redclass-correction).toString(), redSpaces.get(redclass-correction).doubleValue()));
				if(isneeded[redclass])
					isneeded[redclass+1]=true;	// we need to record that some item wanted this space.
				redSpaces.remove(redclass-correction);
				correction++;	// now this location needs to be checked again!
			}
		}
		TypeInfo last = null;
		for (TypeInfo t : types) {
			BigFraction s = last==null ? BigFraction.ONE_HALF : last.getSizeLB();
			t.setNeeds(computeNeeds(s, t.getRedFraction(), t.getRedfit()));
			t.setLeaves(computeLeaves(s, t.getBluefit()));
			last = t;
		}
	}

	private void addVerySmallTypes() {
		BigFraction thresholdExpansion = BigFraction.ONE;
		
		//compute sizes between the last type so far and the minItemSize
		//determine where to start with this; the last size before that should be of the form 1/i for some i
		if (lastTypeBeforeSmallTypeGeneration!=null) {
			System.out.println("Generating remaining sizes between the smallest size so far and the minimum item size.");
			if (lastTypeBeforeSmallTypeGeneration.getNumeratorAsInt()!=1)
				throw new IllegalStateException("INVALID INPUT FILE! For continuing with 1/i-sizes, the last size before starting the small type generation must be of form 1/i as well");
			TypeInfo lastUpToNow = types.get(types.size()-1);
			if (!lastTypeBeforeSmallTypeGeneration.equals(lastUpToNow.getSizeLB()))
				throw new IllegalStateException("INVALID INPUT FILE! lastTypeBeforeSmallTypeGeneration must be the last size seen so far.");
			int last_reciprocal = lastTypeBeforeSmallTypeGeneration.reciprocal().intValue();
			BigFraction last = new BigFraction(1, last_reciprocal);
			BigFraction lastRed = lastUpToNow.getRedFraction();
			BigFraction secondToLast = types.get(types.size()-2).getSizeLB();
			int lastBluefit = computeBluefit(secondToLast);//compute bluefit and redfit of the last type
			int lastRedfit = computeRedfit(secondToLast);

			BigFraction fullExp = BigFraction.ONE.subtract(lastRed).divide(lastBluefit).add(lastRed.divide(lastRedfit)).divide(last).subtract(new BigFraction(1,10000));

			int k = last_reciprocal+1;
			boolean limitReached = last.equals(smallestType);
			while (!limitReached) {
				//find the next size to add
				//first, compute the bluefit- and redfit-value for this new type - this only depends on the 
				//UPPER bound of the size of this type, hence only on the PREVIOUS lower bound
				int bluefit = last.reciprocal().intValue();
				BigFraction bluefitF = new BigFraction(bluefit,1);
				int redfit = computeRedfit(last);
				boolean finished = false;
				BigFraction nextSize = new BigFraction(1,k);
				BigFraction nextRed = null;
				BigFraction oldRedUB = lastRed;
				BigFraction redLB = BigFraction.ONE.subtract(nextSize.multiply(bluefit));
				BigFraction redUB = BigFraction.ONE.subtract(nextSize.multiply(bluefit)); // to make sure ub = lb at the start
				int startk = k;
				while (!finished) {
					redLB = BigFraction.ONE.subtract(nextSize.multiply(bluefit).multiply(thresholdExpansion));

					oldRedUB = redUB;	// store previous redUB in case we will actually use it
					redUB = fullExp.multiply(nextSize).subtract(bluefitF.reciprocal()).multiply(bluefit*redfit).divide(bluefit-redfit);
					if (nextSize.equals(smallestType)) {
						nextRed = redLB;
						break;
					} else if (redLB.compareTo(redUB)>0) {//upper bound is smaller than lower bound - stop and go back to last size
						if (startk<k) {
							--k;
							nextRed = oldRedUB;
						} else nextRed = redLB;
						finished = true;
					} else { //otherwise, skip this size
						++k;
					}
					nextSize = new BigFraction(1,k);
				}

				nextRed = nextRed.multiply(new BigFraction(105,100));
				types.add(new TypeInfo(nextSize, nextRed));
				System.out.println(String.format("\tAdded %.5f with red fraction = " + nextRed + " = %.5f", nextSize.doubleValue(), nextRed.doubleValue()));
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
		else {
			BigFraction localBound = gammaBound;
			if(sizeUB.compareTo(sizeForRedSpaceBound)<=0) {
				localBound = redSpaceBound;
			}
			int res = localBound.divide(sizeUB).intValue();
			if (res<1) return 1;
			return res;
		}
	}

	private void addSmallRedSpaces() {
		System.out.println("Generating redSpaces smaller than 1/3.");
		for (int i = 0; i<types.size(); ++i) {
			BigFraction space = types.get(i).getSizeLB();
			if (space.compareTo(gammaBound)<=0 && space.compareTo(gammaBound.divide(2))>=0 
					&& !redSpaces.contains(space)) {
				redSpaces.add(space);
				System.out.println(String.format("\tAdding redSpace %s = %.5f because of type with size lower bounded by %.5f", space.toString(), space.doubleValue(), space.doubleValue()));
			}
			space = space.multiply(2);
			if (space.compareTo(gammaBound)<=0 && space.compareTo(gammaBound.divide(2))>=0 && 
					!redSpaces.contains(space)) {
				redSpaces.add(space);
				System.out.println(String.format("\tAdding redSpace %s = %.5f because of type with size lower bounded by %.5f", space.toString(), space.doubleValue(), space.divide(2).doubleValue()));
			}
		}
		if (!redSpaces.contains(gammaBound)) redSpaces.add(gammaBound);
	}

	private void addMediumTypes() {
		BigFraction stepSize = smallestType;
		BigFraction lastSize = BigFraction.ONE_THIRD; 
		BigFraction thisRedFrac = BigFraction.ONE,lastRedFrac=BigFraction.ZERO,redStep=BigFraction.ZERO;
		boolean finalItem = false;
		System.out.println("Generating sizes above one third. Adding medium items in intervals of "+stepSize + ".");
		while (thisRedFrac.compareTo(BigFraction.ZERO)>0) {
			if(finalItem) {
				thisRedFrac = BigFraction.ZERO;
			} else {
				lastRedFrac = thisRedFrac;
				thisRedFrac = BigFraction.ONE.subtract(	
						targetRatio.subtract(1)
						.subtract(BigFraction.ONE_HALF.subtract(lastSize).multiply(sandExpansion))
						.multiply(2)
						);
			}
			TypeInfo existing = null;
			for (TypeInfo t : types) if (t.getSizeLB().equals(lastSize)) {existing = t; break;}
			if (existing!=null) {
				//we do not need to insert the size, just set the new red fraction
				existing.setRedFraction(thisRedFrac);
			} else {
				//insert the new type
				types.add(new TypeInfo(lastSize, thisRedFrac));
			}

			//add redSpace values for the new type, if they do not exist yet
			if (!redSpaces.contains(lastSize)) {
				redSpaces.add(lastSize);
				System.out.println(String.format("\t\tAdding redSpace %s = %.5f because of size %.5f", lastSize.toString(), lastSize.doubleValue(), lastSize.doubleValue()));
			}
			BigFraction b = BigFraction.ONE.subtract(lastSize.multiply(2));
			if (!redSpaces.contains(b)) {
				redSpaces.add(b);
				System.out.println(String.format("\t\tAdding redSpace %s = %.5f because of size %.5f", b.toString(), b.doubleValue(), lastSize.doubleValue()));
			}

			System.out.println("\tAdded size " + lastSize + " = "+lastSize.doubleValue()+" with red fraction = "+thisRedFrac.doubleValue());


			if(lastSize.equals(new BigFraction(1,3))){
				//in this case, we are in the first iteration - we just added the type 1/3
				//then, we find the next size in a "clever" way s.t. the difference between 1/2 and this
				//next size is a multiple of the stepsize
				lastSize = new BigFraction(1,2);
				while(lastSize.compareTo(BigFraction.ONE_THIRD)>0)	// to get nice numbers
					lastSize = lastSize.subtract(stepSize);
				lastSize = lastSize.add(stepSize);	// to end up above 1/3 again - this is the next type we want to try					
			} else {
				redStep = lastRedFrac.subtract(thisRedFrac);
				if (thisRedFrac.compareTo(redStep)<0) {
					lastSize = lastSize.add(thisRedFrac.multiply(stepSize).divide(redStep));
					finalItem = true;
				} else {
					lastSize = lastSize.add(stepSize);
				}
			}
		}
		types.sort(typeComparator);
	}

	private void adjustRedFractionOfSmallTypes() {
		System.out.println("Adjusting red fractions of small types to get small expansion 1.");

		BigFraction localThreshold = BigFraction.ONE;
		int ind = 0;
		while (types.get(ind).getSizeLB().compareTo(new BigFraction(1,6))>-1) ++ind;

		//adjust small red fractions
		if (lastTypeBeforeSmallTypeGeneration!=null) {
			for (int i = ind; i<types.size(); ++i) {
				BigFraction s = types.get(i).getSizeLB();
				if (s.compareTo(lastTypeBeforeSmallTypeGeneration)<0) {
					break; //stop this here
				}
				if(s.compareTo(new BigFraction(1,13))<1 && s.compareTo(lastTypeBeforeSmallTypeGeneration)>0)
					localThreshold = new BigFraction(95,100);
				else
					localThreshold = BigFraction.ONE;
				BigFraction redUB = BigFraction.ONE.subtract(s.multiply(types.get(i-1).getSizeLB().reciprocal().intValue()).multiply(localThreshold));
				BigFraction oldRed = types.get(i).getRedFraction();
				if (oldRed.compareTo(redUB)<0){ //we only INCREASE red fractions
					System.out.println(String.format("\tAdjusting red fraction of type " + i + " (%.5f) from %.5f to %.5f", s.doubleValue(), oldRed.doubleValue(), redUB.doubleValue()));
					types.get(i).setRedFraction(redUB);
				} else
					System.out.println(String.format("\tNOT Adjusting red fraction of type " + i + " (%.5f) from %.5f to %.5f", s.doubleValue(), oldRed.doubleValue(), redUB.doubleValue()));
			}
		}
	}

	private void addTypesDependingOnGammaBound() {
		//add gammaBound, gammaBound/2, gammaBound/3, gammaBound/4 to the item types
		List<TypeInfo> newTypes = new LinkedList<TypeInfo>();
		System.out.println("Adding sizes according to gammaBound.");
		for (int i = 1; i<5; ++i) {
			int index = 0;
			BigFraction sizeToInsert = gammaBound.divide(i), 
					size2ToInsert = gammaBound.subtract(lastTypeBeforeSmallTypeGeneration.divide(2)).divide(i);
			while (index<types.size() && types.get(index).getSizeLB().compareTo(sizeToInsert)>0) ++index;
			newTypes.add(new TypeInfo(sizeToInsert, BigFraction.ZERO));
			System.out.println("\tAdding size " + sizeToInsert.doubleValue() + " (for the moment with red fraction = 0)");
			newTypes.add(new TypeInfo(size2ToInsert, BigFraction.ZERO));
			System.out.println("\tAdding size " + size2ToInsert.doubleValue() + " (for the moment with red fraction = 0)");
		}
		for (TypeInfo t : newTypes) types.add(t);
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
					redSpaces.add(s);
					System.out.println(String.format("\t\tAdding redSpace %s = %.5f because of size %.5f", s.toString(), s.doubleValue(), s.doubleValue()));
				}
				BigFraction b = BigFraction.ONE.subtract(s.multiply(2));
				if (!redSpaces.contains(b)) {
					redSpaces.add(b);
					System.out.println(String.format("\t\tAdding redSpace %s = %.5f because of size %.5f", b.toString(), b.doubleValue(), s.doubleValue()));
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
		if (inputFile==null) name = Util.bspFileName;
		FileIO io = new FileIO();
		io.writeParameterOptimizationOutput(name, targetRatio, redSpaces, types);
	}

}

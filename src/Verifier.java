import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.math3.fraction.BigFraction;


public abstract class Verifier extends DualLPChecker {


	protected BufferedWriter knapsackOutputWriter;
	protected BufferedWriter weightsWriter;

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

	/**
	 * Initialize all parameters by reading the input file given. This given file name must not
	 * be null.
	 */
	protected void initialize(String inputFile) throws IOException {
		FileIO io = new FileIO();
		log("Preparation: reading parameters from file");

		//read parameters from the input file
		readInput(inputFile, io);
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
	
	protected abstract void readInput(String inputFile, FileIO io) throws IOException;

	protected abstract BigFraction getFirstTypeUpperBound();

	/**
	 * This method starts the verification itself.
	 * @throws IOException
	 */
	protected void start() throws IOException {
		log("First checking validity of parameters.");
		verifySizesSorted();
		verifyEnoughTypesBetweenOneThirdAndOneHalf();
		verifyRedSpaceValuesSorted();
		for (int i = 0; i<types.length; ++i) {
			verifyBluefit(i);
			verifyLeaves(i);
			verifyNeeds(i);
		}
		log("\tBluefit values are valid.");
		log("\tLeaves values are valid.");
		log("\tNeeds values are valid.");

		//check that eps<0.1
		if (types[types.length-1].getSizeLB().compareTo(new BigFraction(1,10))>=0) {
			log("INFEASIBLE! smallest item type must be below 0.1 but has size lower bound " + types[types.length-1].getSizeLB());
			System.exit(0);
		}
		log("\tEpsilon is small enough.");
		//check that red fraction <1/3 and >=0 for all types
		for (int i = 0; i<types.length; ++i)
			if (types[i].getRedFraction().compareTo(BigFraction.ONE_THIRD)>=0 || types[i].getRedFraction().compareTo(BigFraction.ZERO)<0) {
				log(String.format("INFEASIBLE! red fraction of type with size at least " + types[i].getSizeLB() + " is %.5f!", types[i].getRedFraction().doubleValue()));
				System.exit(0);
			}
		log("\tRed fractions are between 0 and 1/3.");
		log("--------------------------------------------\n");
		log("Now starting the searches for heavy patterns.");
		log("Each knapsack search ignores all patterns with weight at most the target ratio minus 0.001.\n");
		log("--------------------------------------------\n");
		log("Checking case where no r-item exists (k=K+1).");

		//---------------------CASE 1: k=K+1 ------------------------------
		//find the heaviest pattern without r and check whether its weight is below our target ratio
		checkKPlusOne();

		//---------------------FURTHER CASES: k<K+1 ------------------------------
		//now, check the different cases for r
		for (int k = 0; k<redSpace.length; ++k) {
			checkCasek(k);
		}

		log("\n\nAll cases proven feasible! Competitive ratio is " + targetRatio.doubleValue());
	}
	
	private void checkCasek(int k) throws IOException {
		if (!isNecessaryToCheckCase(k)) return; //we can skip this case if there are no red items of this class

		//check feasibility of the dual LP; compute heaviest pattern for corresponding knapsack problem
		KnapsackPattern v = checkDualLP(k, y3Values[k]);
		
		if (v!=null && v.getTotalWeightInclSand(sandExpansion).compareTo(targetRatio)>0) {
			//if this is too large, stop the program
			log("INFEASIBLE FOR CASE WHERE k=" + k + "! Stopping computations.");
			System.exit(0);
		}
		
		log("Case k=" + k + " verified!\n\n--------------------------------------------\n");
	}

	@Override
	protected BigFraction checkY1(int k, BigFraction w1) {
		BigFraction y1 = y1Values[k];
		if (y1.compareTo(BigFraction.ZERO)<0) {
			throw new IllegalStateException("INFEASIBLE! Value of y1 must be non-negative but is " + y1 + " in case k = " + k);
		}
		if (y1.compareTo(new BigFraction(5,100))>0) {
			throw new IllegalStateException("INFEASIBLE! Value of y1 must be below 0.05 but is " + y1 + " in case k = " + k);
		}
		return y1;
	}

	@Override
	protected BigFraction checkY2(int k, BigFraction w1) {
		BigFraction y2 = y2Values[k];
		if (y2.compareTo(BigFraction.ZERO)<0) {
			throw new IllegalStateException("INFEASIBLE! Value of y2 must be non-negative but is " + y2 + " in case k = " + k);
		}
		return y2;
	}

	@Override
	protected void checkY3(BigFraction y3, int k) {
		//check that the y3-value is ok, i.e., in the interval [0, 1/2]
		if (y3.compareTo(BigFraction.ZERO)<0) {
			throw new IllegalStateException("INFEASIBLE! Value of y3 must be non-negative but is " + y3.doubleValue() + " in case k = " + k);
		}
		if (y3.compareTo(new BigFraction(6,10))>0) { //BigFraction.ONE_HALF)>0) {
			throw new IllegalStateException("INFEASIBLE! Value of y3 must be below 1/2 but is " + y3.doubleValue() + " in case k = " + k);
		}
	}

	private void verifySizesSorted() throws IOException {
		for (int i = 1; i<types.length; ++i) {
			if (types[i].getSizeLB().compareTo(types[i-1].getSizeLB())>=0) throw new IllegalStateException("Item types are not sorted according to size!");
		}
		log("\tSizes are sorted.");
	}

	/**
	 * This method verifies that the types between 1/3 and 1/2 are less than x apart, where x is the
	 * lower bound of the smallest type (above sand).
	 * @throws IOException 
	 */
	private void verifyEnoughTypesBetweenOneThirdAndOneHalf() throws IOException {
		BigFraction minItemSize = types[types.length-1].getSizeLB();
		int largestTypeBelowOneHalf = -1;
		int smallestTypeAboveOneThird = -1;
		for (int i = 1; i<types.length; ++i) {
			if (largestTypeBelowOneHalf==-1 && types[i].getSizeLB().compareTo(BigFraction.ONE_HALF)<0)
				largestTypeBelowOneHalf = i;
			if (types[i].getSizeLB().compareTo(BigFraction.ONE_THIRD)>=0)
				smallestTypeAboveOneThird = i;
		}
		for (int i = smallestTypeAboveOneThird+1; i<largestTypeBelowOneHalf; ++i) {
			BigFraction diff = types[i].getSizeLB().subtract(types[i-1].getSizeLB());
			if (diff.compareTo(minItemSize)>=0) {
				throw new IllegalStateException("ERROR: Sizes between 1/3 and 1/2 are not always less than minSize apart: difference between lower bound " + types[i].getSizeLB() + " of type " + i + " and lower bound " + types[i-1].getSizeLB() + " of type " + (i-1) + " is " + diff);
			}
		}
		log("\tSpaces between medium types are sufficiently small.");
	}

	/**
	 * This method verifies that the redSpace-values are sorted in increasing order and no redSpace- value
	 * appears twice.
	 * @throws IOException 
	 */
	private void verifyRedSpaceValuesSorted() throws IOException {
		for (int i = 0; i<redSpace.length-1; ++i) {
			BigFraction last = redSpace[i];
			BigFraction next = redSpace[i+1];
			int c = last.compareTo(next);
			if (c==0) {
				throw new IllegalStateException("ERROR: Duplicate redSpace value " + last);
			} else if (c>0) {
				throw new IllegalStateException("ERROR: redSpaces not sorted increasingly: " + last + " > " + next);
			}
		}
		log("\tRedspace values are sorted.");
	}

	private void verifyBluefit(int type) throws IOException {
		int bluefit = types[type].getBluefit();
		int Type0BlueFit = BigFraction.ONE.divide(getFirstTypeUpperBound()).intValue();
		int supposedBluefit = type==0 ? Type0BlueFit : types[type-1].getSizeLB().reciprocal().intValue();
		if (bluefit!=supposedBluefit) {
			System.out.println("ERROR: bluefit of type "+type+" is incorrect, should be " + supposedBluefit + " but was " + bluefit);
			System.exit(1);
		}
	}

	/**
	 * This method verifies that for type i>1, bluefit_i items fit in one bin but bluefit_i+1 do not. Furthermore,
	 * it verifies that bluefit_i blue items leave a space of at least redSpace_{leaves[i]} 
	 * but less than redSpace_{leaves[i]+1} unoccupied.
	 * @throws IOException 
	 */
	private void verifyLeaves(int type) throws IOException {
		BigFraction sizeUb = type>0 ? types[type-1].getSizeLB() : getFirstTypeUpperBound();
		BigFraction spaceOccupied = sizeUb.multiply(types[type].getBluefit());
		BigFraction spaceLeft = BigFraction.ONE.subtract(spaceOccupied);
		if (spaceLeft.compareTo(sizeUb)>0) throw new IllegalStateException("Wrong bluefit value for type "+type+"! Bluefit=" + types[type].getBluefit() + " items of max. size " + sizeUb + " leave space " + spaceLeft);

		//verify that free is in (redSpace[leaves[i]-1], redSpace[leaves[i]]]
		int blue = types[type].getLeaves();
		if (blue==-1) { //this means that no red items are accepted
			if (spaceLeft.compareTo(redSpace[0])>=0) {
				throw new IllegalStateException("ERROR: leaves of type " + type + " is incorrect! Free space is " + spaceLeft + ", which is more than " + redSpace[0] + " (=smallest redSpace)");
			}
		} else {
			BigFraction lbRedSpace = redSpace[blue];
			BigFraction ubRedSpace = blue<redSpace.length-1 ? redSpace[blue+1] : BigFraction.ONE;
			if (spaceLeft.compareTo(lbRedSpace)<0) {
				throw new IllegalStateException("ERROR: leaves of type " + type + " is incorrect! Free space is " + spaceLeft + ", which is less than " + lbRedSpace + " (=redSpace[" + (blue) + "])");
			}
			if (spaceLeft.compareTo(ubRedSpace)>=0) {
				throw new IllegalStateException("ERROR: leaves of type " + type + " is incorrect! Free space is " + spaceLeft + ", which is more than " + ubRedSpace + " (=redSpace[" + (blue+1) + "])");
			}
		}
	}
	
	/**
	 * This method verifies that if t_i>redSpace[K-1], then redfit_i=0 and red_i=0. Furthermore, it
	 * verifies that redfit_i red items occupy a space of no more than redSpace_{needs[i]}
	 * but more than redSpace_{needs[i]-1}.
	 * @throws IOException 
	 */
	private void verifyNeeds(int type) throws IOException {
		BigFraction sizeUb = type>0 ? types[type-1].getSizeLB() : BigFraction.ONE_HALF;
		int redfit = types[type].getRedfit();
		int r = types[type].getNeeds();

		if (!types[type].getRedFraction().equals(BigFraction.ZERO)) {
			if (sizeUb.compareTo(redSpace[redSpace.length-1])>0) {
				throw new IllegalStateException("ERROR: red and redfit of type " + type + " needs to be zero, as the upper bound " + sizeUb + " of this type is larger than the maximum redSpace " + redSpace[redSpace.length-1]);
			} else {
				BigFraction occupied = sizeUb.multiply(redfit);
				BigFraction free = BigFraction.ONE.subtract(occupied);
				if (r==0) {
					BigFraction ubRedSpace = redSpace[r];
					if (ubRedSpace.compareTo(free)<0) {
						throw new IllegalStateException("ERROR: needs of type " + type + " is incorrect, red items occupy space " + occupied + ", which is more than redSpace[" +(r) + "]=" + ubRedSpace);
					}
				} else {
					BigFraction lbRedSpace = redSpace[r-1];
					BigFraction ubRedSpace = redSpace[r];
					if (lbRedSpace.compareTo(occupied)>=0) {
						throw new IllegalStateException("ERROR: needs of type " + type + " is incorrect, red items occupy space " + occupied + ", which is less than redSpace[" +(r-1) + "]=" + lbRedSpace);
					}
					if (ubRedSpace.compareTo(occupied)<0) {
						throw new IllegalStateException("ERROR: needs of type " + type + " is incorrect, red items occupy space " + occupied + ", which is more than redSpace[" +(r) + "]=" + ubRedSpace);
					}
				}
			}
		} else {
			if (types[type].getRedfit()!=0)
				throw new IllegalStateException("Redfit must be 0 for type " + type + " as red fraction is 0!");
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
	protected void writeTypeInformation() throws IOException {
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
	protected void writeWeights() throws IOException {
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
	@Override
	protected void writeKnapsackFile(int k, BigFraction[] sizeLB, BigFraction[] weights) throws IOException {
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
				throw new IllegalStateException("Weight of type " + i + " is null!");
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

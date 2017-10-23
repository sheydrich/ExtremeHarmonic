import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.apache.commons.math3.fraction.BigFraction;

public class LowerBound {

	private static BigFraction maxWeight = BigFraction.TWO;
	
	private static int[] heaviestPattern, overallHeaviestPattern;
	private static BigFraction[] alphasForMaxWeight;
	private static final BigFraction[] size = {BigFraction.ONE_HALF,
		BigFraction.ONE_THIRD,  BigFraction.ONE_QUARTER , new BigFraction(1,7)};
	private static final int typeOneHalf = 0, typeOneThird = 1;
	private static final int[] beta = new int[size.length];
	private static final int[][] patterns = new int[10000][size.length];
	private static BigFraction[] patternWeightsForMaxWeight = new BigFraction[patterns.length];
	private static final BigFraction[] patternTotalSize = new BigFraction[10000];
	private static int numberOfPatterns = 0;
	private static final BigFraction[] currentWeights = new BigFraction[10000];
	

	public static void main(String[] args) {
		generateInitialPatterns();
		generateAllPatterns(1);

		for (int i = 0; i<size.length; ++i) {
			beta[i] = size[i].reciprocal().intValue();
			if (size[i].reciprocal().compareTo(new BigFraction(beta[i]))==0) beta[i]--;
		}
		BigFraction[] alpha = new BigFraction[size.length];
		BigFraction[] alphaGuess = new BigFraction[size.length];
		BigFraction[] alphaLB = new BigFraction[size.length];
		BigFraction[] alphaUB = new BigFraction[size.length];
		
		int[] gamma = new int[size.length];
		
		//set alphas and gammas
		alphaGuess[0] = BigFraction.ZERO;
		alphaGuess[1] = new BigFraction(1828,10000);
		alphaGuess[2] = new BigFraction(1269,10000);
		alphaGuess[3] = new BigFraction(1428,10000);
		
		for(int i=0;i<size.length; i++){
			alpha[i] = alphaGuess[i];
			alphaLB[i] = alphaGuess[i].multiply(new BigFraction(9,10));
			alphaUB[i] = alphaGuess[i].multiply(new BigFraction(12,10));
		}
		gamma[0] = 0;
		gamma[1] = 1;
		gamma[2] = 1;
		gamma[3] = 2;

		BigFraction stepSize = new BigFraction(1,20);
		for(int i=0;i<20;i++) {
			boolean notdone = true;
			while(notdone) {
				notdone = lookforWeight(alpha, alphaLB, alphaUB, stepSize, gamma);
			}
			printPattern();
			// we have found the best alphas for this stepsize
			stepSize = stepSize.multiply(2).divide(3);
		}
		System.out.println("***********************************");
		ArrayList<Integer> sorting = new ArrayList<Integer>();
		for (int i = 0; i<numberOfPatterns; ++i) sorting.add(i);
		Collections.sort(sorting, new Comparator<Integer>(){

			@Override
			public int compare(Integer o1, Integer o2) {
				return patternWeightsForMaxWeight[o2].compareTo(patternWeightsForMaxWeight[o1]);
			}});
		for (int j = 0; j<9; ++j) printPattern(sorting.get(j), patternWeightsForMaxWeight[sorting.get(j)], alpha);
	}

	private static void generateInitialPatterns() {
		int maxNumberOfItems = size[0].reciprocal().intValue();
		if (size[0].reciprocal().subtract(maxNumberOfItems).equals(BigFraction.ZERO)) maxNumberOfItems--;
		numberOfPatterns = maxNumberOfItems+1;
		for (int i = 0; i<=maxNumberOfItems; ++i) {
			patterns[i][0] = i;
			patternTotalSize[i] = size[0].multiply(i);
		}
	}

	private static void generateAllPatterns(int type) {
		int maxNumberOfItems = size[type].reciprocal().intValue();
		if (size[0].reciprocal().subtract(maxNumberOfItems).equals(BigFraction.ZERO)) maxNumberOfItems--;
		int n = numberOfPatterns;
		for (int p = 0; p<n; ++p) {
			for (int i = 1; i<maxNumberOfItems; ++i) {
				if (patternTotalSize[p].add(size[type].multiply(i)).compareTo(BigFraction.ONE)<0) {
					numberOfPatterns++;
					System.arraycopy(patterns[p], 0, patterns[numberOfPatterns-1], 0, size.length);
					patterns[numberOfPatterns-1][type] = i;
					patternTotalSize[numberOfPatterns-1] = patternTotalSize[p].add(size[type].multiply(i));
				} else {
					break;
				}
			}
		}
		if (type<size.length-1) generateAllPatterns(type+1);
	}

	private static void printPattern(int i, BigFraction weight, BigFraction[] alpha) {
		for (int j = 0; j<size.length; ++j) {
			System.out.print(patterns[i][j] + " ");
		}
		System.out.print("\t" + weight.doubleValue() + "\t");
		for(int j=1; j<size.length;j++)
			System.out.print(alpha[j].doubleValue()+"  "); //+alpha[j]+"), ");
		System.out.println();
	}

	private static void printPattern() {
		for (int j = 0; j<size.length; ++j) {
			System.out.print(overallHeaviestPattern[j] + " ");
		}
		System.out.print("\t" + maxWeight.doubleValue() + "\t");
		for(int j=1; j<size.length;j++)
			System.out.print(alphasForMaxWeight[j].doubleValue()+"  "); //+alphasForMaxWeight[j]+"), ");
		System.out.println();
	}

	private static BigFraction computeWeights(BigFraction[] alpha, int[] gamma) {
		BigFraction maxWeight = BigFraction.ZERO;
		for (int p = 0; p<numberOfPatterns; ++p) { //for each pattern
			BigFraction totalWeightOfThisPattern = BigFraction.ZERO;
			for (int t = 0; t<size.length; ++t) { //for each item type
				if (patterns[p][t]==0) continue; //no items of this type in this pattern, so go to next type/pattern

				BigFraction blueWeight = BigFraction.ONE.subtract(alpha[t]).divide(beta[t]);

				//check, whether red items of type t can be combined with other items in this pattern
				boolean redItemsCanBeCombined = false;
				for (int i = 0; i<t; ++i) {
					if (patterns[p][i]>0 && //type i is used in this pattern 
							size[i].multiply(beta[i]).add(size[t].multiply(gamma[t])).compareTo(BigFraction.ONE)<1) { //blue type i items and red type t items fit together (depending on gamma!) 
						redItemsCanBeCombined = true;
						break;
					}
				}
				if (redItemsCanBeCombined) {
					//then we only count the blue weight of type t items
					totalWeightOfThisPattern = totalWeightOfThisPattern.add(blueWeight.multiply(patterns[p][t]));
				} else {
					//otherwise, we count the total weight (red and blue) of this type
					BigFraction redWeight = gamma[t]==0 ? BigFraction.ZERO : alpha[t].divide(gamma[t]);
					BigFraction totalWeight = redWeight.add(blueWeight);
					totalWeightOfThisPattern = totalWeightOfThisPattern.add(totalWeight.multiply(patterns[p][t]));
				}
			}
			//finally, add the sand
			BigFraction sandVolume = BigFraction.ONE.subtract(patternTotalSize[p]);
			totalWeightOfThisPattern = totalWeightOfThisPattern.add(sandVolume);

			
			//check whether lemma 7/8 apply
			boolean containsOneHalf = patterns[p][typeOneHalf]>0;
			boolean containsOneThird = false;
			for(int i=typeOneHalf;i<typeOneThird;i++)
				if(patterns[p][i+1]>0)
					containsOneThird = true;
			if (!containsOneHalf && containsOneThird) { //apply lemma 7 and 8
				BigFraction q1lemma7 = BigFraction.ONE.subtract(alpha[typeOneThird]).divide(2).multiply(patterns[p][typeOneThird]);
				BigFraction q2lemma8 = BigFraction.TWO.divide(BigFraction.ONE.subtract(alpha[typeOneThird]));
				BigFraction temp = BigFraction.ZERO;
				for (int t = typeOneThird+1; t<size.length; ++t) {
					if (size[t].multiply(gamma[t]).add(size[typeOneThird].multiply(2)).compareTo(BigFraction.ONE)<0) {
						temp = temp.add(alpha[t].divide(gamma[t]).multiply(patterns[p][t]));
					}
				}
				q2lemma8 = q2lemma8.multiply(temp);
				BigFraction sum = BigFraction.ONE.add(q1lemma7).add(q2lemma8);
				BigFraction ratioP = BigFraction.ONE.divide(sum);
				BigFraction ratioq1 = q1lemma7.divide(sum);
				BigFraction ratioq2 = q2lemma8.divide(sum);

				BigFraction weightOfOneThird = alpha[typeOneThird].add(BigFraction.ONE.subtract(alpha[typeOneThird]).divide(2));
				BigFraction weightOfQ1 = BigFraction.ONE.add(weightOfOneThird);
				BigFraction weightOfQ2 = weightOfQ1;
				totalWeightOfThisPattern = ratioP.multiply(totalWeightOfThisPattern).add(
						ratioq1.multiply(weightOfQ1)).add(ratioq2.multiply(weightOfQ2));
			}
			currentWeights[p] = totalWeightOfThisPattern;
//			printPattern(p, totalWeightOfThisPattern, alpha);
			if (totalWeightOfThisPattern.compareTo(maxWeight)>0) {
				maxWeight = totalWeightOfThisPattern;
				heaviestPattern = new int[size.length];
				System.arraycopy(patterns[p], 0, heaviestPattern, 0, size.length);
			}
		}
		return maxWeight;
	}

	private static boolean lookforWeight(BigFraction[] alphas, BigFraction[] alphaLB,
			BigFraction[] alphaUB, BigFraction stepSize, int[] gamma){
		boolean somethingChanged = false;
		boolean anyChange = false;
		for (int t = size.length-1; t>0; --t) {
			somethingChanged = false;
			BigFraction LB = alphas[t].subtract(stepSize.multiply(15));
			if (LB.compareTo(alphaLB[t])<0)
				LB = alphaLB[t];
			BigFraction UB = alphas[t].add(stepSize.multiply(15));
			if (UB.compareTo(alphaUB[t])>0)
				UB = alphaUB[t];
			BigFraction oldAlpha = alphas[t];
			BigFraction newBestAlpha = BigFraction.ZERO;
			for (BigFraction alpha = UB; alpha.compareTo(LB)>0; alpha = alpha.subtract(stepSize)) {
				alphas[t] = alpha;	// try putting in this new alpha value in the alpha array
				BigFraction w = computeWeights(alphas, gamma);
				if (w.compareTo(maxWeight)<0) {
					newBestAlpha = alpha;
					somethingChanged = true;
					anyChange = true;
					maxWeight = w;
					overallHeaviestPattern = heaviestPattern;
					alphasForMaxWeight = new BigFraction[size.length];
					System.arraycopy(alphas, 0, alphasForMaxWeight, 0, size.length);
					System.arraycopy(currentWeights, 0, patternWeightsForMaxWeight, 0, currentWeights.length);
				}
			}
			if(!somethingChanged){
				alphas[t]=oldAlpha;	// no improvement: go back to old value
			} else {
				alphas[t]=newBestAlpha;
			}
		}
		return anyChange;
	}
}

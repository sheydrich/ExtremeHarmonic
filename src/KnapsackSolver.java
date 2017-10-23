import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.fraction.BigFraction;

/**
 * 
 * This class solves a knapsack problem. In order to use it, you have to specify a set of
 * item types, each with a size lower bound and a weight, and a checker that can exclude
 * certain patterns from the search. Furthermore, you need to give a value for the sand expansion.
 *
 */
public class KnapsackSolver {

	private final BigFraction[] size;
	private final BigFraction[] weight;
	private final PatternFeasibilityCheck check;
	private final BigFraction sandExpansion;

	public KnapsackSolver(BigFraction[] sizes, BigFraction[] weights, PatternFeasibilityCheck check,
			BigFraction sandExpansion) {

		this.size = sizes;
		this.weight = weights;
		this.check = check;
		this.sandExpansion = sandExpansion;

	}

	private BigFraction maxWeightFound;
	private KnapsackPattern heaviestPattern;
	private int[] typePermutation;

	/**
	 * This method starts the computations.
	 * @param patternWeightThreshold the threshold used to speed up the search. (Partial) Patterns that
	 * cannot reach a weight above this threshold are discarded.
	 */
	public KnapsackPattern solve(BigFraction patternWeightThreshold) throws IOException {
		//initialize variables
		KnapsackPattern p = new KnapsackPattern();
		maxWeightFound = patternWeightThreshold;
		heaviestPattern = null;
		
		//create the permutation that sorts types in descending order of expansion
		typePermutation = createPermutation();
		
		//start the recursive search for the heaviest pattern
		packRecursively(0, p);
		
		return heaviestPattern;
	}

	/**
	 * This method creates the permutation of types that sorts them in descending order of expansion.
	 * Types that have expansion below the sandExpansion are not considered (i.e., they are not
	 * included in the int[] that is returned).
	 */
	private int[] createPermutation() {

		//compute expansion of all types
		BigFraction[] expansion = new BigFraction[size.length];
		for (int i = 0; i<size.length; ++i) {
			expansion[i] = weight[i].divide(size[i]);
		}

		//sort by expansion

		//create list of all types WITH EXPANSION >= sandExpansion! Types with less expansion 
		//do NOT need to be considered!
		List<Integer> pi_function = new LinkedList<>();
		for (int i = 0; i<size.length; ++i) {
			if (expansion[i].compareTo(sandExpansion)<0) continue;
			pi_function.add(i);
		}
		//sort list
		Collections.sort(pi_function, new Comparator<Integer>(){

			@Override
			public int compare(Integer arg0, Integer arg1) {
				//sort in decreasing (!) order of expansion
				return expansion[arg1].compareTo(expansion[arg0]);
			}});
		//write list to array
		int[] pi = new int[pi_function.size()];
		for (int i = 0; i<pi_function.size(); ++i) {
			pi[i] = pi_function.get(i);
		}

		//return result
		return pi;
	}

	/**
	 * The recursive method that finds the heaviest pattern.
	 * @param typeIndexInPermutation the type-index (w.r.t. the permutation) of the next item to add to the pattern
	 * @param pattern the current pattern
	 */
	private void packRecursively(int typeIndexInPermutation, KnapsackPattern pattern) {
		BigFraction expansion = findMaxExpansionFitting(typeIndexInPermutation, pattern);

		//compute upper bound by multiplying the maximum expansion found above by 
		//the remaining space, adding it to the total weight so far
		BigFraction upperBound = pattern.getTotalWeightWithoutSand()
				.add(pattern.getRemainingSpace().multiply(expansion));

		//if this upper bound does not beat the best pattern found so far, skip this branch
		if (maxWeightFound.compareTo(upperBound)>0) {
			return;
		}

		if (typeIndexInPermutation==typePermutation.length) { //we have added all item sizes - we are done
			maxWeightFound = upperBound; //store the pattern and its weight
			heaviestPattern = pattern.copy();
		} else { //add items of type typeIndexInPermutation to this pattern
			int typeToAdd = typePermutation[typeIndexInPermutation];

			//find out how many items of the current type we can add
			int numberOfItemsToAdd = pattern.howManyItemsFit(size[typeToAdd]);

			//check, if we really can add this item (or if it would lead to a forbidden pattern)
			if (!check.canAdd(size[typeToAdd], pattern)) numberOfItemsToAdd = 0;

			//now, add that many items to the current pattern
			pattern.addItems(size[typeToAdd], weight[typeToAdd], numberOfItemsToAdd);

			//now, we try to add items of smaller size to this pattern, while we decrease the number of items
			//of the current type (i.e. we start with adding smaller items to the pattern that contains
			//numberOfItemsToAdd many items of the current type; then we reduce this by one and add smaller items, 
			//and so on, until we end up with the pattern we started with, i.e. we try at last the case that
			//we do not add any items of the current type and proceed to the next type)
			while (numberOfItemsToAdd>=0) {
				//try to add items of smaller size; for this, find the next type that fits
				packRecursively(typeIndexInPermutation+1, pattern);

				//now, reduce the number of current items by one
				numberOfItemsToAdd--;
				if (numberOfItemsToAdd>=0) {
					pattern.removeItem(size[typeToAdd]);
				}
			}
			pattern.removeAllItems(size[typeToAdd]);
		}
	}

	/**
	 * This method finds the first type in the permuted order (starting from the given type) that still fits in 
	 * the remaining space of the given pattern and returns its expansion.
	 */
	private BigFraction findMaxExpansionFitting(int type, KnapsackPattern pattern) {
		int i = type; 
		while (i<typePermutation.length && pattern.getRemainingSpace().compareTo(size[typePermutation[i]])<=0) ++i;
		BigFraction expansion;
		if (i==typePermutation.length) {
			expansion = sandExpansion;
		} else {
			expansion = weight[typePermutation[i]].divide(size[typePermutation[i]]);
		}
		if(expansion.compareTo(sandExpansion)<0)
			expansion = sandExpansion;
		return expansion;
	}
}

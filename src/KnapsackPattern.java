import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.fraction.BigFraction;

/**
 * This class represents one pattern considered in the knapsack solver.
 *
 */
public class KnapsackPattern {

	protected final List<Entry> items; //the items of this pattern
	protected BigFraction totalSize; //the total size of these items
	protected BigFraction totalWeight; //the total weight of these items; this does not include the weight of sand!
	
	/**
	 * 
	 * This class represents one item type packed in this pattern.
	 *
	 */
	private class Entry {
		private final BigFraction size; //size of the item
		private final BigFraction weight; //weight of the item
		private int cardinality; //cardinality, how often this item is present in the pattern
		
		public Entry(BigFraction size, BigFraction weight, int card) {
			this.size = size;
			this.weight = weight;
			this.cardinality = card;
			if (card<1) throw new IllegalArgumentException("Cannot add zero item to pattern!");
		}
		
		/**
		 * Add n items of this type to the entry.
		 */
		public void add(int n) {cardinality = cardinality + n;}
		
		/**
		 * Remove one item of this type from the entry. This may not reduce the cardinality to zero.
		 */
		public void removeOne() {
			cardinality--;
			if (cardinality<1) throw new IllegalArgumentException("Cannot add zero item to pattern!");
		}
		
		/**
		 * Create a copy of this entry.
		 */
		public Entry clone() {
			return new Entry(size, weight, cardinality);
		}
	}
	
	
	
	
	// ------------------------------------------
	// ------------------------------------------
	// ------------------------------------------
	// Now the code for the pattern itself starts
	// ------------------------------------------
	// ------------------------------------------
	// ------------------------------------------

	public KnapsackPattern() {
		items = new LinkedList<>();
		totalWeight = BigFraction.ZERO;
		totalSize = BigFraction.ZERO;
	}

	/**
	 * This method adds items of the given size, weight and cardinality to this pattern.
	 */
	public void addItems(BigFraction size, BigFraction w, int number) {
		if (number==0) return;
		boolean existing = false;
		for (Entry e : items) {
			if (e.size.equals(size)) {
				existing = true;
				e.add(number);
			}
		}
		if (!existing) {
			//this type is not present yet, so add it
			Entry e = new Entry(size, w, number);
			items.add(e);
		}

		//add weight for new items and increase total size
		totalWeight = totalWeight.add(w.multiply(number));
		totalSize = totalSize.add(size.multiply(number));
	}

	/**
	 * This method returns a copy of this pattern with the same items.
	 * 
	 */
	public KnapsackPattern copy() {
		KnapsackPattern p = new KnapsackPattern();
		for (Entry e : items) p.items.add(e.clone());
		p.totalSize = totalSize;
		p.totalWeight = totalWeight;
		return p;
	}

	/**
	 * Checks how many items of the given size could be added to this pattern space-wise.
	 */
	public int howManyItemsFit(BigFraction size) {
		BigFraction bf = BigFraction.ONE.subtract(totalSize).divide(size);
		int intPart = bf.intValue();
		int res = bf.equals(new BigFraction(intPart)) ? intPart-1 : intPart;
		if (res<0) res = 0;
		return res;
	}

	/**
	 * Removes one item of the given size from this pattern.
	 */
	public void removeItem(BigFraction size) {
		Entry found = null;
		for (Entry e : items) {
			if (e.size.equals(size)) {
				found = e;
				break;
			}
		}
		if (found==null) //no items of this type
			return;

		if (found.cardinality==1) {
			items.remove(found);
		} else {
			found.removeOne();
		}

		totalSize = totalSize.subtract(size.multiply(1));
		totalWeight = totalWeight.subtract(found.weight.multiply(1));
	}

	/**
	 * Removes all items of the given size from this pattern.
	 */
	public void removeAllItems(BigFraction size) {
		Entry e = null;
		for (Entry f : items) {
			if (f.size.equals(size)) {
				e = f;
				break;
			}
		}
		if (e==null) //no items of this type
			return;

		items.remove(e);

		totalSize = totalSize.subtract(size.multiply(e.cardinality));
		totalWeight = totalWeight.subtract(e.weight.multiply(e.cardinality));
	}


	// ------------------------------------------
	// ------------------------------------------
	// ------------------------------------------
	// Some getter methods
	// ------------------------------------------
	// ------------------------------------------
	// ------------------------------------------
	
	
	public BigFraction getRemainingSpace() {
		return BigFraction.ONE.subtract(totalSize);
	}

	public BigFraction getTotalWeightWithoutSand() {
		return totalWeight;
	}

	public boolean containsSize(BigFraction size) {
		for (Entry e : items) if (e.size.equals(size)) return true;
		return false;
	}

	public BigFraction getTotalWeightInclSand(BigFraction sandExpansion) {
		return totalWeight.add(BigFraction.ONE.subtract(totalSize).multiply(sandExpansion));
	}

	/**
	 * This method computes the total w-weight of items in this pattern plus the weight of the sand.
	 * As w-/v-weights are not stored in this pattern object separately, we need to supply the array
	 * of all types. We also need the value of k for computing the w-weight.
	 */
	public BigFraction getTotalWeightWInclSand(TypeInfo[] types, int k, BigFraction sandExpansion) {
		BigFraction total = BigFraction.ZERO;
		
		for (Entry e : items) {
			int index = -1;
			for (int i = 0; i<types.length; ++i) {
				if (types[i].getSizeLB().equals(e.size)) {
					index = i;
					break;
				}
			}
			if (index<0) total = total.add(BigFraction.ONE); //then it is a large item, those have w-weight 1
			else total = total.add(types[index].getWeightW(k));
		}
		
		return total;
	}

	/**
	 * This method computes the total v-weight of items in this pattern plus the weight of the sand.
	 * As w-/v-weights are not stored in this pattern object separately, we need to supply the array
	 * of all types. We also need the value of k for computing the v-weight.
	 */
	public BigFraction getTotalWeightVInclSand(TypeInfo[] types, int classOfR, BigFraction sandExpansion) {
		BigFraction total = BigFraction.ZERO;
		
		for (Entry e : items) {
			int index = -1;
			for (int i = 0; i<types.length; ++i) {
				if (types[i].getSizeLB().equals(e.size)) {
					index = i;
					break;
				}
			}
			if (index<0) {//it is a large item
				if (!e.size.equals(BigFraction.ONE_HALF)) //then it has weight 1; otherwise it has weight 0
					total = total.add(BigFraction.ONE);
			} else 
				total = total.add(types[index].getWeightV(classOfR));
		}
		
		return total;
	}
	


	// ------------------------------------------
	// ------------------------------------------
	// ------------------------------------------
	// Printing
	// ------------------------------------------
	// ------------------------------------------
	// ------------------------------------------

	public String weightString() {
		String s = "";
		for (int i = 0; i<items.size(); ++i) {
			Entry e = items.get(i);
			s += String.format("%.5f ["+e.cardinality+" times]",e.weight.doubleValue());
			if (i<items.size()-1) s+= " , ";
		}
		return s;
	}

	@Override
	public String toString() {
		String s = "";
		for (int i = 0; i<items.size(); ++i) {
			Entry e = items.get(i);
			s += e.size + " ["+e.cardinality+" times]";
			if (i<items.size()-1) s += " , ";
		}
		return s;
	}

}

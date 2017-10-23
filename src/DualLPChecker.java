import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.fraction.BigFraction;


/**
 * This is an abstract super class used by BinarySearch and ExtremeHarmonicVerifier.
 * It contains some methods to facilitate the knapsack problem solving.
 *
 */
public abstract class DualLPChecker {

	/**
	 * This array contains all information about NON-LARGE types used by the algorithm. Large types are handled
	 * separately in each of the cases.
	 */
	protected TypeInfo[] types;

	/**
	 * These fields contain general problem information.
	 */
	protected BigFraction targetRatio;
	protected BigFraction sandExpansion;
	protected BigFraction[] redSpace;
	
	
	
	
	/**
	 * This is for logging.
	 */
	protected BufferedWriter writer;
	


	/**
	 * This method solves the knapsack problem associated with the simple dual LP (D^{k,sml}_w in the paper).
	 * It returns the maximum weight found.
	 */
	protected KnapsackPattern checkSimpleDualLP(BigFraction[] sizes, BigFraction[] weights) throws IOException {
		//solve knapsack problem
		PatternFeasibilityCheck check = new AllPatterns(); //there are no special patterns
		KnapsackSolver solver = new KnapsackSolver(sizes, weights, check, sandExpansion);
		return solver.solve(targetRatio.subtract(new BigFraction(1,1000)));
	}

	/**
	 * This method checks whether it is necessary to consider a given value of k. It checks whether
	 * k references the red space of size 0 (which exists to make computation of leaves/needs values easier)
	 * and which item types have this k as red class. If it refers to 0 or there are no types
	 * with corresponding needs-value, false is returned. Otherwise, true is returned.
	 */
	protected boolean isNecessaryToCheckCase(int k) throws IOException {
		if (redSpace[k].equals(BigFraction.ZERO)) return false; //no need to check this class if redSpace is zero
		int[] typesForThisClass = findTypesForRedClass(k); //these are all types t with needs(t)==k

		log("Checking case where k = needs(t(r)) = " + k 
				+ ":  redspace_k = "+redSpace[k]+", item sizes with this red class: " + sizeStringForTypes(typesForThisClass));

		if (typesForThisClass.length==0) {
			log("No need to check this case; types with this red class do not exist.\n\n--------------------------------------------\n");
			return false;
		}
		return true;
	}

	/**
	 * This method computes the weight of the pattern q1 (which is also the weight of q2).
	 */
	protected BigFraction computeWeightOfQ1(int typeOfR) {
		BigFraction totalWeight = BigFraction.ONE; //1-s(r) item
		BigFraction wgtRItem = types[typeOfR].getBlueWeight().add(types[typeOfR].getRedWeight());
		totalWeight = totalWeight.add(wgtRItem); //r item

		//now add sand
		BigFraction remainingSpace = types[typeOfR-1].getSizeLB().subtract(types[typeOfR].getSizeLB());
		totalWeight = totalWeight.add(remainingSpace.multiply(sandExpansion));
		return totalWeight;
	}
	
	/**
	 * This method computes the type of r given its class k.
	 * The method assumes that r is medium!
	 */
	protected int computeTypeOfRFromClass(int k) {
		for (int i = 0; i<types.length; ++i) {
			if (types[i].getNeeds()==k) return i;
		}
		throw new IllegalStateException("SEVERE ERROR! Couldn't compute type of r from class k=" + k);
	}
	
	//---------------------------------------------
	//---------------------------------------------
	//----------------HELPER-----------------------
	//---------------------------------------------
	//---------------------------------------------


	/**
	 * Returns the indices of all types t with needs(t)==k
	 */
	private int[] findTypesForRedClass(int k) {
		List<Integer> list = new LinkedList<>();
		for(int i=0;i<types.length;i++)
			if(types[i].getNeeds()==k){
				list.add(i);
			}
		int[] res = new int[list.size()];
		for (int i = 0; i<res.length; ++i)
			res[i] = list.get(i);
		return res;
	}

	/**
	 * 
	 * Returns a string with all types represented by the indices in the given array.
	 */
	private String sizeStringForTypes(int[] arr) {
		String s = "";
		boolean first = true;
		for(int i : arr) {
			if(first) first=!first; else s+= ", ";
			s += types[i].getSizeLB();
		}
		return s;
	}
	
	/**
	 * This is for printing messages to system.out and also to a protocol file.
	 */
	protected void log(String msg) throws IOException {
		System.out.println(msg);
		if (writer!=null) {
			writer.write(msg + "\n");
		}
	}
}

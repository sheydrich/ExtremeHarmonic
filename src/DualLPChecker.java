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
	protected BigFraction patternWeightThreshold; //this is for speeding up the knapsack solver
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
	 * This method checks feasibility of the dual LP for certain values of k and y3.
	 */
	protected KnapsackPattern checkDualLP(int k, BigFraction y3) throws IOException {
		checkY3(y3, k);
		//check whether r is medium or small
		boolean rIsMedium = redSpace[k].compareTo(BigFraction.ONE_THIRD)>0;
		if (!rIsMedium) { 

			// --------------------------------
			// r is small
			// --------------------------------



			//we need two large types: (1/2, 2/3] and (2/3, 1]
			BigFraction[] allSizes = new BigFraction[types.length+2];
			BigFraction[] weights = new BigFraction[allSizes.length];
			allSizes[0] = BigFraction.TWO_THIRDS;
			allSizes[1] = BigFraction.ONE_HALF;
			weights[0] = BigFraction.ONE; //w=v=1 for this type
			weights[1] = BigFraction.ONE.subtract(y3); //(1-y3)*w + y3*v = 1-y3 as w=1 and v=0 for this type
			for (int i = 0; i<types.length; ++i) {
				allSizes[i+2] = types[i].getSizeLB();
				//weights in this case are (1-y3)*w + y3*v
				weights[i+2] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
			}

			log(String.format("r is small, so check feasibility of simpler dual LP; y3 = " + y3.doubleValue() + ".\n"));
			
			//write weights to a file for reference
			writeKnapsackFile(k, allSizes, weights);

			//check feasibility of the dual LP
			KnapsackPattern p = checkSimpleDualLP(allSizes, weights);

			//output the findings
			if (p!=null)
				log(String.format("\tHeaviest pattern is " + getOutputWeightString(k, p) + "\n\twith weights " + p.weightString() + ", %.5f (sand)\n\tand total weight %.5f", p.getRemainingSpace().multiply(sandExpansion).doubleValue(), p.getTotalWeightInclSand(sandExpansion).doubleValue()));
			else
				log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
			return p;
		} else { 

			// --------------------------------
			// r is medium
			// --------------------------------


			int t = computeTypeOfRFromClass(k); //compute the type of r

			if (types[t].getRedFraction().equals(BigFraction.ZERO)) {
				log("No need to check this case, as no red item of class " + k + " can exist (red fraction = 0).\n\n--------------------------------------------\n");
				return null;
			}

			//compute weight of the special pattern q1; this is also the weight of q2
			BigFraction w1 = computeWeightOfQ1(t);
			boolean rangeOfRSmallEnough = types[t-1].getSizeLB().subtract(types[t].getSizeLB()).compareTo(types[types.length-1].getSizeLB())<=0;

			//add three large types: (2/3, 1], (1-t_t(r), 2/3], (1/2, 1-t_t(r)]
			BigFraction[] allSizes = new BigFraction[types.length+3];
			BigFraction[] weights = new BigFraction[allSizes.length];
			allSizes[0] = BigFraction.TWO_THIRDS;
			allSizes[1] = BigFraction.ONE.subtract(types[t-1].getSizeLB());
			allSizes[2] = BigFraction.ONE_HALF;
			//compute weight of large types: (1-y3)*w + y3*v
			weights[0] = weights[1] = BigFraction.ONE; //w=v=1 for these types
			weights[2] = BigFraction.ONE.subtract(y3); //w=1 and v=0 for this type
			for (int i = 0; i<types.length; ++i) allSizes[i+3] = types[i].getSizeLB();
			
			//weights are now computed depending on whether w1 or targetRatio is larger

			if (!rangeOfRSmallEnough || w1.compareTo(targetRatio)<1) {

				// ----------------------------------------
				// FIRST CASE: w1 <= y4, so we can use the simpler LP
				// ----------------------------------------

				
				//in this case, weights are again (1-y3)*w + y3*v
				for (int i = 0; i<types.length; ++i) {
					weights[i+3] = types[i].getWeightW(k).multiply(BigFraction.ONE.subtract(y3)).add(types[i].getWeightV(k).multiply(y3));
				}
				String s = rangeOfRSmallEnough ? String.format("%.5f = w_{1k} < c = %.5f", w1.doubleValue(), targetRatio.doubleValue()) : "the range of t(r) -- which is ("+types[t].getSizeLB()+", "+types[t-1].getSizeLB()+"] -- is larger than " + types[types.length-1].getSizeLB();
				log(String.format("r is medium and "+s+", so check feasibility of simpler dual LP; y3 = "+y3.doubleValue() + ".\n"));

				//write weights to file for later reference
				writeKnapsackFile(k, allSizes, weights);

				//check feasibility of the dual LP
				KnapsackPattern p = checkSimpleDualLP(allSizes, weights);

				//output findings
				if (p!=null)
					log(String.format("\tHeaviest pattern is " + getOutputWeightString(k, p) + "\n\twith weights " + p.weightString() + ", %.5f (sand)\n\tand total weight %.5f", p.getRemainingSpace().multiply(sandExpansion).doubleValue(), p.getTotalWeightInclSand(sandExpansion).doubleValue()));
				else
					log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
				return p;
			} else {

				// ----------------------------------------
				// SECOND CASE: w1 > y4, so we use the extended LP
				// ----------------------------------------

				
				//check that values y1, y2 are ok
				BigFraction y1 = checkY1(k, w1);
				BigFraction y2 = checkY2(k, w1);

				//compute weights for non-large items: this is the function omega
				for (int i = 0; i<types.length; ++i)
					weights[i+3] = types[i].computeOmega(types[t], y1, y2, y3);

				log(String.format("r is medium and %.5f = w_{1k} >= c = %.5f, so check feasibility of extended dual LP; y3 = "+y3.doubleValue() + ".\n", w1.doubleValue(), targetRatio.doubleValue()));

				//the object check makes sure that patterns q1, q2 are not considered in the search for the heaviest pattern
				PatternFeasibilityCheck check = new NotQ1Q2(allSizes[1], types[t].getSizeLB()); 

				//write weights to file for later reference
				writeKnapsackFile(k, allSizes, weights);

				//check dual LP
				//first, check simple constraints
				if (y1.add(targetRatio).compareTo(w1)<0) {
					log("VERIFICATION FAILED! First constraint (y1+y4 >= w1) is violated in case k="+k+"!");
					System.exit(1);
				}
				if (y2.divide(2).add(targetRatio).compareTo(w1)<0) {
					log("VERIFICATION FAILED! Second constraint (y2/2+y4 >= w1) is violated in case k="+k+"!");
					System.exit(1);
				}
				log("First two constraints of dual LP verified.");

				//now, solve knapsack problem to check other constraints
				KnapsackSolver solver = new KnapsackSolver(allSizes, weights, check, sandExpansion);
				KnapsackPattern p = solver.solve(patternWeightThreshold);
				p = compareWithQ3(p, t, k, y1, y3);
				
				//output findings
				if (p!=null)
					log(String.format("\tHeaviest pattern is " + getOutputWeightString(k, p) + "\n\twith weights " + p.weightString() + " %.5f (sand)" + "\n\tand total weight %.5f", p.getRemainingSpace().multiply(sandExpansion).doubleValue(), p.getTotalWeightInclSand(sandExpansion).doubleValue()));
				else
					log(String.format("\tNo pattern has weight above %.5f", patternWeightThreshold.doubleValue()));
				return p;
			}
		}
	}

	private KnapsackPattern compareWithQ3(KnapsackPattern p, int t, int k, BigFraction y1, BigFraction y3) throws IOException {
		BigFraction sandVolumeInQ3 = (t>0?types[t-1].getSizeLB().subtract(types[t].getSizeLB()):BigFraction.ZERO);
		BigFraction sandWeightInQ3 = sandExpansion.multiply(sandVolumeInQ3);
		BigFraction w3k = BigFraction.ONE.add(types[t].getBlueWeight()).add(sandWeightInQ3);
		BigFraction v3k = BigFraction.ONE.add(types[t].getWeightV(k)).add(sandWeightInQ3);
		BigFraction weightOfQ3 = BigFraction.ONE.subtract(y3).multiply(w3k).add(y3.multiply(v3k)).add(y1.multiply(BigFraction.ONE.subtract(types[t].getRedFraction()).divide(BigFraction.ONE.add(types[t].getRedFraction()))));
		log(String.format("Weight of q3 is low enough at %.5f", weightOfQ3.doubleValue()));
		if (weightOfQ3.compareTo(patternWeightThreshold)>0 && (p==null || weightOfQ3.compareTo(p.getTotalWeightInclSand(sandExpansion))>0)) {
			KnapsackPattern p2 = new KnapsackPattern();
			p2.addItems(BigFraction.ONE.subtract(types[t-1].getSizeLB()), BigFraction.ONE, 1);
			p2.addItems(types[t].getSizeLB(), types[t].getBlueWeight().multiply(BigFraction.ONE.subtract(y3)).add(types[t].getWeightV(k).multiply(y3)), 1);
			if (weightOfQ3.compareTo(p2.getTotalWeightInclSand(sandExpansion))!=0)
				throw new IllegalStateException();
			return p2;
		}
		return p;
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
	
	protected void checkKPlusOne() throws IOException {
		KnapsackPattern v = findHeaviestPatternWithoutR();

		if (v!=null && v.getTotalWeightInclSand(sandExpansion).compareTo(targetRatio)>0) {
			//if this is already too large, stop the program
			log("\tHeaviest pattern: " + v);
			log("\twith weights: " + v.weightString());
			log("\tand total weight: " + v.getTotalWeightInclSand(sandExpansion).doubleValue());
			log("INFEASIBLE FOR CASE WITHOUT r! Stopping computations.");
			System.exit(0);
		}

		//output
		if (v!=null) {
			log("\tHeaviest pattern: " + getOutputWeightString(redSpace.length, v));
			log("\twith weights: " + v.weightString());
			log(String.format("\tand total weight: %.5f", v.getTotalWeightInclSand(sandExpansion).doubleValue()));
		} else {
			log(String.format("\tNo pattern above weight %.5f found!", patternWeightThreshold.doubleValue()));
		}
		log(String.format("Case k = K+1 verified for target value y4 = %s = %.5f!\n\n--------------------------------------------\n", targetRatio.toString(), targetRatio.doubleValue()));
	}

	/**
	 * This method checks the case that k=K+1.
	 * It returns the heaviest pattern for this case.
	 */
	private KnapsackPattern findHeaviestPatternWithoutR() throws IOException {

		//create the large types that are needed in this case and compute all weights
		BigFraction[] allSizes = new BigFraction[types.length + 2]; //we need to add large items of size in (1/2, 2/3] and (2/3, 1]
		BigFraction[] weights = new BigFraction[allSizes.length];
		allSizes[0] = BigFraction.TWO_THIRDS;
		allSizes[1] = BigFraction.ONE_HALF;
		weights[0] = weights[1] = BigFraction.ONE;
		for (int i = 2; i<allSizes.length; ++i) {
			allSizes[i] = types[i-2].getSizeLB();
			//weights are (1-red)/bluefit
			weights[i] = types[i-2].getBlueWeight();
		}

		//write the weights to a file for reference
		writeKnapsackFile(redSpace.length, allSizes, weights);

		//call the knapsack solver
		KnapsackSolver solver = new KnapsackSolver(allSizes, weights, new AllPatterns(), sandExpansion);
		return solver.solve(patternWeightThreshold);
	}
	
	protected abstract BigFraction checkY1(int k, BigFraction w1);
	protected abstract BigFraction checkY2(int k, BigFraction w1);
	protected abstract void checkY3(BigFraction y3, int k);
	protected abstract void writeKnapsackFile(int k, BigFraction[] sizes, BigFraction[] weights) throws IOException;
	
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
	
	protected String getOutputWeightString(int k, KnapsackPattern maxWeightPattern) {
		String s = "";
		for (KnapsackPattern.Entry e : maxWeightPattern.items) {
			if (!s.isEmpty()) s += " , ";
			TypeInfo t = null;
			for (TypeInfo tt : types) {
				if (tt.getSizeLB().equals(e.size)) {
					t = tt;
					break;
				}
			}
			if (t==null && e.size.compareTo(BigFraction.ONE_HALF)<0) throw new IllegalStateException("Couldn't find type of size " + s);
			if (t!=null)
				s += e.size + " [" + e.cardinality + " times; " + (t.isWHigh(k) ? "high w, " : "low w, ") + (t.isVHigh(k) ? "high v]" : "low v]");
			else
				s += e.size + " [" + e.cardinality + " times]";
		}
		return s;
	}
}

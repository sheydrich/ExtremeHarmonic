import org.apache.commons.math3.fraction.BigFraction;

/**
 * This class represents one item type with all its parameters.
 *
 */
public class TypeInfo {

	private BigFraction sizeLB;
	private BigFraction redFraction;
	private int redfit;
	private int bluefit;
	private int needs;
	private int leaves;
	private BigFraction redWeight;
	private BigFraction blueWeight;
	
	public TypeInfo(BigFraction sizeLB, BigFraction redFraction) {
		this.sizeLB = sizeLB;
		this.redFraction = redFraction;
	}
	
	public BigFraction getSizeLB() {
		return sizeLB;
	}

	public void setSizeLB(BigFraction sizeLB) {
		this.sizeLB = sizeLB;
	}

	public BigFraction getRedFraction() {
		return redFraction;
	}

	public void setRedFraction(BigFraction redFraction) {
		this.redFraction = redFraction;
	}

	public int getRedfit() {
		return redfit;
	}

	public void setRedfit(int redfit) {
		this.redfit = redfit;
	}

	public int getBluefit() {
		return bluefit;
	}

	public void setBluefit(int bluefit) {
		this.bluefit = bluefit;
	}

	public int getNeeds() {
		return needs;
	}

	public void setNeeds(int needs) {
		this.needs = needs;
	}

	public int getLeaves() {
		return leaves;
	}

	public void setLeaves(int leaves) {
		this.leaves = leaves;
	}

	public BigFraction getRedWeight() {
		return redWeight;
	}

	public BigFraction getBlueWeight() {
		return blueWeight;
	}
	
	public String toString() {
		return String.format("%.5f (red = %.5f)", sizeLB.doubleValue(), redFraction.doubleValue());
	}
	
	/*
	 * ***************************************************
	 * ***************************************************
	 * ***************************************************
	 * The following methods are used for calculating weights
	 * ***************************************************
	 * ***************************************************
	 * ***************************************************
	 */

	public BigFraction getWeightW(int k) {
		if (needs>=k || needs==0) return blueWeight.add(redWeight);
		else return blueWeight;
	}
	
	public BigFraction getWeightV(int k) {
		if (leaves<k) return blueWeight.add(redWeight);
		else return redWeight;
	}

	public void computeWeights() {
		redWeight = redFraction.equals(BigFraction.ZERO) ? redFraction : redFraction.divide(redfit);
		blueWeight = BigFraction.ONE.subtract(redFraction).divide(bluefit);
	}
	
	public BigFraction computeOmega(TypeInfo typeOfR, BigFraction y1, BigFraction y2, BigFraction y3) {
		BigFraction v = getWeightV(typeOfR.getNeeds());
		BigFraction w = getWeightW(typeOfR.getNeeds());
		if (this.equals(typeOfR)) {
			BigFraction termA = BigFraction.ONE.subtract(redFraction)
					.divide(bluefit)
					.add(redFraction.divide(redfit));
			BigFraction termB = v;
			BigFraction termC = BigFraction.ONE.subtract(redFraction)
					.divide(BigFraction.ONE.add(redFraction));
			return BigFraction.ONE.subtract(y3).multiply(termA)
					.add(y3.multiply(termB))
					.add(y1.multiply(termC));
		} else if (needs>0 && needs<=typeOfR.getLeaves()) {
			return BigFraction.ONE.subtract(y3).multiply(w)
					.add(y3.multiply(v))
					.add(y2.multiply(redFraction.divide(redfit)));
		} else {
			return BigFraction.ONE.subtract(y3).multiply(w).add(y3.multiply(v));
		}
	}
	
}

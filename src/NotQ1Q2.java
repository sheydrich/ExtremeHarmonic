import org.apache.commons.math3.fraction.BigFraction;


public class NotQ1Q2 implements PatternFeasibilityCheck {
	
	private final BigFraction sizeOfLarge;
	private final BigFraction sizeOfTypeOfR;
	
	public NotQ1Q2(BigFraction sizeOfLarge, BigFraction sizeOfTypeOfR) {
		this.sizeOfLarge = sizeOfLarge;
		this.sizeOfTypeOfR = sizeOfTypeOfR;
	}

	@Override
	public boolean canAdd(BigFraction size, KnapsackPattern pattern) {
		if (!sizeOfLarge.equals(size) && !sizeOfTypeOfR.equals(size)) return true; //all types other than these two types can be added freely
		
		if (sizeOfLarge.equals(size) && pattern.containsSize(sizeOfTypeOfR)) // we want to add a large (1-r) item, so we cannot have a t(r)-type item
			return false;
		else if (sizeOfTypeOfR.equals(size) && pattern.containsSize(sizeOfLarge)) //we want to add a t(r)-type item, so we cannot have a (1-r)-item
			return false;
		return true;
	}
}

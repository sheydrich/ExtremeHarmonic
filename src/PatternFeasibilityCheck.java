import org.apache.commons.math3.fraction.BigFraction;


public interface PatternFeasibilityCheck {

	public boolean canAdd(BigFraction size, KnapsackPattern pattern);
}

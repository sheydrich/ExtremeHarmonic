import org.apache.commons.math3.fraction.BigFraction;


/**
 * This pattern checker accepts all patterns.
 *
 */
public class AllPatterns implements PatternFeasibilityCheck {

	@Override
	public boolean canAdd(BigFraction size, KnapsackPattern pattern) {
		return true;
	}

}

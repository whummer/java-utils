package io.hummer.util.test;

/**
 * Miscellaneous utility functions.
 * @author Waldemar Hummer
 */
public class TestUtil {
	
	private static final TestUtil instance = new TestUtil();

	public static TestUtil getInstance() {
		return instance;
	}

	public void assertTrue(boolean bool) {
		if(!bool)
			throw new RuntimeException("Assertion failed.");
	}

	public void assertFalse(boolean bool) {
		if(bool)
			throw new RuntimeException("Assertion failed.");
	}

	public boolean isNullOrNegative(Integer in) {
		return in == null || in < 0;
	}

	public boolean isNullOrFalse(Boolean in) {
		return in == null || !in;
	}

	public boolean isNullOrTrue(Boolean in) {
		return in == null || in;
	}

}

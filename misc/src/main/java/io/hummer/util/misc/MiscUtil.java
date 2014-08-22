package io.hummer.util.misc;


/**
 * Miscellaneous utility functions.
 * @author Waldemar Hummer
 */
public class MiscUtil {
	
	private static final MiscUtil instance = new MiscUtil();

	public static MiscUtil getInstance() {
		return instance;
	}


	public void sleep(long duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void sleepRandom(long maxDuration) {
		sleepRandom(0, maxDuration);
	}
	public void sleepRandom(long minDuration, long maxDuration) {
		try {
			Thread.sleep(minDuration + (long)(Math.random() * (double)(maxDuration - minDuration)));
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
}

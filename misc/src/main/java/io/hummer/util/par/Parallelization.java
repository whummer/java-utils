package io.hummer.util.par;

import io.hummer.util.log.LogUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;

public class Parallelization {

	private static final Map<Runnable,LinkedBlockingQueue<?>> runnableToLoopCollections = new HashMap<Runnable, LinkedBlockingQueue<?>>();
	private static final Logger logger = LogUtil.getLogger(Parallelization.class);

	@SuppressWarnings("all")
	public static synchronized <T> T takeNext(Runnable runnable) {
		try {
			return (T)runnableToLoopCollections.get(runnable).take();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static void runMultiple(final Runnable job, int numThreads, boolean blocking) {
		List<Integer> ids = new LinkedList<Integer>();
		for(int i = 0; i < numThreads; i ++) {
			ids.add(i);
		}
		loop(job, ids, blocking);
	}
	
	public static void loop(final Runnable job, Collection<?> loopArray, boolean blocking) {
		if(runnableToLoopCollections.containsKey(job))
			throw new IllegalArgumentException("Job " + job + " already exists.");
		int loopArraySize = loopArray.size();
		runnableToLoopCollections.put(job, new LinkedBlockingQueue<Object>(loopArray));
		final LinkedBlockingQueue<Object> terminationTokens = new LinkedBlockingQueue<Object>();
		for(int i = 0; i < loopArray.size(); i ++) {
			GlobalThreadPool.execute(new Runnable() {
				public void run() {
					try {
						job.run();
						terminationTokens.put(new Object());
					} catch (Throwable e) {
						try {
							terminationTokens.put(e);
						} catch (InterruptedException e1) {
							terminationTokens.add(e);
						}
					}
				}
			});
		}
		
		if(blocking) {
			int counter = 0;
			while(counter < loopArraySize) {
				try {
					terminationTokens.take();
				} catch (InterruptedException e1) { }
				counter ++;
			}
		}
	}

	public static void warnIfNoResultAfter(final AtomicReference<?> resultRef, final String message, long afterTimeoutMS) {
		Runnable r = new Runnable() {
			public void run() {
				if(resultRef.get() == null) {
					logger.warn(message);
				}
			}
		};
		GlobalThreadPool.executeAfter(r, afterTimeoutMS, false);
	}

	public static void runAndInterrupt(final Runnable runnable, long timeoutMS) throws InterruptedException {
		final LinkedBlockingQueue<Object> finished = new LinkedBlockingQueue<Object>();
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					runnable.run();
				} catch (Exception e) {
					logger.warn("Error executing runnable: " + e);
				}
				try {
					finished.put(true);
				} catch (InterruptedException e) { /* should not happen */}
			}
		});
		t.start();
		try {
			Object result = finished.poll(timeoutMS, TimeUnit.MILLISECONDS);
			if(result == null) {
				throw new InterruptedException();
			}
		} catch (InterruptedException e) {
			try {
				t.interrupt();
			} catch (Throwable e2) {
				/* swallow */
			}
			throw e;
		}
	}
	
}

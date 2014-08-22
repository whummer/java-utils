package io.hummer.util.par;

import io.hummer.util.log.LogUtil;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

/**
 * Provides simple access to a global cached thread pool.
 * @author Waldemar Hummer
 */
public class GlobalThreadPool {

	private static final ExecutorService executor = Executors.newCachedThreadPool();
	private static final Timer timer = new Timer(true);
	private static final Logger logger = LogUtil.getLogger(GlobalThreadPool.class);

	private GlobalThreadPool() {}

	public static void execute(Runnable r) {
		synchronized(executor) {
			try {
				executor.execute(r);				
			} catch (RuntimeException e) {
				logger.error("Unable to execute Runnable in thread pool. " + executor);
				throw e;
			} catch (Error e) {
				logger.error("Unable to execute Runnable in thread pool. " + executor);
				throw e;
			}
		}
	}

	public static void executeAfter(final Runnable r, final long afterTimeoutMS, boolean blocking) {
		Runnable run = new Runnable() {
			public void run() {
				try {
					Thread.sleep(afterTimeoutMS);
				} catch (Exception e) { }
				synchronized(executor) {
					executor.execute(r);
				}
			}
		};
		if(blocking)
			run.run();
		else 
			execute(run);
	}

	private static class PeriodicTaskWithVariableInterval extends TimerTask {
		private Runnable actualRunnable;
		private long intervalIfSuccessMS;
		private long intervalIfErrorMS;
		PeriodicTaskWithVariableInterval(Runnable actualRunnable, long intervalIfSuccessMS, long intervalIfErrorMS) {
			this.actualRunnable = actualRunnable;
			this.intervalIfErrorMS = intervalIfErrorMS;
			this.intervalIfSuccessMS = intervalIfSuccessMS;
		}
		PeriodicTaskWithVariableInterval(PeriodicTaskWithVariableInterval other) {
			this.actualRunnable = other.actualRunnable;
			this.intervalIfErrorMS = other.intervalIfErrorMS;
			this.intervalIfSuccessMS = other.intervalIfSuccessMS;
		}
		public void run() {
			try {
				actualRunnable.run();
				timer.schedule(new PeriodicTaskWithVariableInterval(this), intervalIfSuccessMS);
			} catch (Exception e) {
				timer.schedule(new PeriodicTaskWithVariableInterval(this), intervalIfErrorMS);
			}
		}
	}
	
	public static void executePeriodically(final Runnable r, final long intervalIfSuccessMS, final long intervalIfErrorMS) {
		timer.schedule(new PeriodicTaskWithVariableInterval(r, intervalIfSuccessMS, intervalIfErrorMS), 0);
	}

	public static void executePeriodically(final Runnable r, final long intervalMS) {
		executePeriodically(r, intervalMS, intervalMS);
	}

	public static ExecutorService getExecutorService() {
		return executor;
	}
}
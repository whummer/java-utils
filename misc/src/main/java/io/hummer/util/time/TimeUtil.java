package io.hummer.util.time;

import io.hummer.util.log.LogUtil;
import io.hummer.util.math.MathUtil;
import io.hummer.util.str.StringUtil;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * This class provides utility methods related to date and time.
 * 
 * @author Waldemar Hummer
 */
public class TimeUtil {

	private static TimeUtil instance;
	private MathUtil mathUtil = new MathUtil();
	private StringUtil strUtil = new StringUtil();
	public Logger logger = LogUtil.getLogger(TimeUtil.class);

	private static class DurationEstimate {
		long startTime;
		long lastTime;
		int totalSteps;
		final List<Long> times = new LinkedList<Long>();
	}

	private Map<String,DurationEstimate> estimates = new HashMap<String,DurationEstimate>();

	public static TimeUtil getInstance() {
		if(instance == null) {
			instance = new TimeUtil();
		}
		return instance;
	}

	public String format(long milliseconds) {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(3);
		if(milliseconds < 0) {
			return "n/a";
		}
		if(milliseconds < 10*1000) {
			return milliseconds + "ms";
		}
		if(milliseconds < 2*60*1000) {
			return nf.format((double)milliseconds/1000.0) + "sec";
		}
		return nf.format((double)milliseconds/1000.0/60.0) + "min";
	}

	public double getRemainingTime(int totalExpectedItems, boolean addOneItem) {
		return getRemainingTime(null, totalExpectedItems, addOneItem);
	}
	public double getRemainingTime(String id, int totalExpectedSteps, boolean addOneFinishedSteps) {
		if(addOneFinishedSteps) {
			if(id == null)
				id = "";
			if(!estimates.containsKey(id)) {
				DurationEstimate d = new DurationEstimate();
				d.startTime = System.currentTimeMillis();
				d.lastTime = d.startTime;
				d.totalSteps = totalExpectedSteps;
				estimates.put(id, d);
			} else {
				DurationEstimate d = estimates.get(id);
				if(addOneFinishedSteps) {
					long lastTime = d.lastTime;
					d.lastTime = System.currentTimeMillis();
					d.totalSteps = totalExpectedSteps;
					d.times.add(d.lastTime - lastTime);
				}
			}
		}
		DurationEstimate d = estimates.get(id);
		if(d != null && !d.times.isEmpty()) {
			double avgTime = mathUtil.avg(d.times);
			return ((double)d.totalSteps - (double)d.times.size()) * avgTime;
		}
		return -1;
	}
	public void printRemainingTime(int totalExpectedSteps, boolean addOneFinishedSteps) {
		printRemainingTime(null, totalExpectedSteps, addOneFinishedSteps);
	}
	public void printRemainingTime(String id, int totalExpectedSteps, boolean addOneFinishedSteps) {
		String prefix = strUtil.isEmpty(id) ? "" : ("'" + id + "': ");
		logger.info(prefix + "Time remaining (apprx.): " + 
				format((long)getRemainingTime(id, totalExpectedSteps, addOneFinishedSteps)));
	}

}

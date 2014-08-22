/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010-2012 Vienna University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hummer.util.perf;

import io.hummer.util.io.IOUtil;
import io.hummer.util.log.LogUtil;
import io.hummer.util.par.GlobalThreadPool;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

public class PerformanceProfiler implements Runnable {

	public static final int CHECK_INTERVAL_MS = 1000;
	private static final int MAX_LIST_SIZE = 20;

	private static final Logger logger = LogUtil.getLogger(PerformanceProfiler.class);

	private Boolean running = false;
	private final List<Double> valuesMemTotal = new LinkedList<Double>();
	private final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
	private final List<Double> cpuTotalPercents = new LinkedList<Double>();
	private final List<Double> openFiles = new LinkedList<Double>();
	private long processCpuLastTimestamp;
	private long processCpuLastDuration;
	private double numProcessors = (double)Runtime.getRuntime().availableProcessors();
	private int checkIntervalMS = CHECK_INTERVAL_MS;

	public PerformanceProfiler() { 
		this(CHECK_INTERVAL_MS);
	}
	public PerformanceProfiler(int checkIntervalMS) { 
		if (mxBean.isCurrentThreadCpuTimeSupported()) {
		    mxBean.setThreadCpuTimeEnabled(true);
		}
		this.checkIntervalMS = checkIntervalMS;
		GlobalThreadPool.execute(this);
	}
	
	public void run() {
		while (true) {
			try {
				synchronized (this) {
					while(!running) {
						wait();
					}
				}
				long total = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
				synchronized (valuesMemTotal) {
					valuesMemTotal.add((double)total);
					while(valuesMemTotal.size() > MAX_LIST_SIZE)
						valuesMemTotal.remove(0);
				}
				double totalRatio = 0;
				
				OperatingSystemMXBean b = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
				long cpuNow = 0;
				try {
					cpuNow = (Long)b.getClass().getMethod("getProcessCpuTime").invoke(b);						
				} catch (Exception e) {
					cpuNow = 0;
					totalRatio = b.getSystemLoadAverage() / numProcessors;
				}
				if(processCpuLastTimestamp > 0) {
					Long cpuThen = processCpuLastDuration;
					Long timeThen = processCpuLastTimestamp;
					long timeNow = System.nanoTime();
					totalRatio = (double)(cpuNow - cpuThen) / (double)(timeNow - timeThen);
					totalRatio /= (double)numProcessors;
				} 
				if(cpuNow > 0) {
					processCpuLastDuration = cpuNow;
					processCpuLastTimestamp = System.nanoTime();
				}

				synchronized (cpuTotalPercents) {
					cpuTotalPercents.add(totalRatio);
					while(cpuTotalPercents.size() > MAX_LIST_SIZE)
						cpuTotalPercents.remove(0);
				}

				try {
					Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", 
							"lsof -p " + getPID() + " | wc -l"});
					p.waitFor();
					String lsof = new IOUtil().readFile(p.getInputStream());
					openFiles.add(Double.parseDouble(lsof));
					while(openFiles.size() > MAX_LIST_SIZE)
						openFiles.remove(0);
				} catch (Exception e) { }
				
				Thread.sleep(checkIntervalMS);
			} catch (Exception e) {
				logger.warn("Unexpected error.", e);
			}
		}
	}

	public int getMeasurementWindowInMilliseconds() {
		return MAX_LIST_SIZE * checkIntervalMS;
	}

	private int getPID() {
		try {
			String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
			return Integer.parseInt(pid);
		} catch (Exception e) {
			return -1;
		}
	}
	
	public synchronized void start() {
		synchronized (valuesMemTotal) {
			running = true;
			valuesMemTotal.clear();
			notifyAll();
		}
	}

	public double getAverageMemUsage() {
		return getAverage(valuesMemTotal);
	}

	public double getMaximumMemUsage() {
		return getMaximum(valuesMemTotal);
	}

	public double getMaximumCPU() {
		double max = getMaximum(cpuTotalPercents);
		return max;
	}

	public double getMaximumMemUsage(long duration) {
		if(valuesMemTotal.isEmpty())
			return 0.0;
		double sum = 0;
		double count = 0;
		synchronized (valuesMemTotal) {
			for(int i = 0; i < ((double)duration)/((double)checkIntervalMS) 
					&& i < valuesMemTotal.size(); i ++) {
				sum += valuesMemTotal.get(valuesMemTotal.size() - 1 - i);
				count ++;
			}
		}
		return sum / count;
	}
	public double getAverageCPU(long duration) {
		if(cpuTotalPercents.isEmpty())
			return 0.0;
		double sum = 0;
		double count = 0;
		synchronized (cpuTotalPercents) {
			for(int i = 0; i < ((double)duration)/((double)checkIntervalMS) 
					&& i < cpuTotalPercents.size(); i ++) {
				sum += cpuTotalPercents.get(cpuTotalPercents.size() - 1 - i);
				count ++;
			}
		}
		return sum / count;
	}
	public double getCurrentCPU() {
		if(cpuTotalPercents.isEmpty())
			return 0.0;
		return cpuTotalPercents.get(cpuTotalPercents.size() - 1);
	}
	
	public int getMaximumOpenFiles() {
		return (int)getMaximum(openFiles);
	}
	
	private synchronized double getMaximum(List<Double> list) {
		synchronized (list) {
			double maximum = Double.MIN_VALUE;
			for(double d : list)
				if(d > maximum)
					maximum = d;
			return maximum;
		}
	}

	private synchronized double getAverage(List<Double> list) {
		synchronized (list) {
			if(list.size() <= 0)
				return 0;
			double sum = 0;
			for(double d : list)
				sum += d;
			return sum / (double)list.size();
		}
	}
	
	public static void main(String[] args) {
		PerformanceProfiler p = new PerformanceProfiler();
		p.start();
	}
	
}

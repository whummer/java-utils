/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010 Vienna University of Technology
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
package io.hummer.util.misc;

import io.hummer.util.log.LogUtil;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

public class PerformanceInterceptor {

	private static final Logger logger = LogUtil.getLogger(PerformanceInterceptor.class);
	private static boolean verbose = false;
	protected final Map<EventType, Map<String,Long>> eventTimes = new HashMap<EventType, Map<String,Long>>();
	private static final PerformanceInterceptor defaultInterceptor = new PerformanceInterceptor();

	public static enum EventType {
		START_XQUERY, FINISH_XQUERY,
		START_INVOCATION, FINISH_INVOCATION,
		START_SEND_INVOCATION, FINISH_SEND_INVOCATION,
		START_COLLECT_RESULTS, FINISH_COLLECT_RESULTS, 
		START_FINALIZE_RESULT, FINISH_FINALIZE_RESULT,
		START_HTTP_GET, FINISH_HTTP_GET,
		START_RESPONSE_TO_XML, FINISH_RESPONSE_TO_XML,
		START_STRING_TO_XML, FINISH_STRING_TO_XML,
		START_PARSE_XML_STRICT, FINISH_PARSE_XML_STRICT,
		START_PARSE_XML_TIDY, FINISH_PARSE_XML_TIDY
	}

	public static final List<PerformanceInterceptor> interceptors = new LinkedList<PerformanceInterceptor>();

	static {
		interceptors.add(defaultInterceptor);
	}

	public static String event(EventType type) {
		String id = UUID.randomUUID().toString();
		if(type.name().startsWith("START_")) {
			//System.out.println("starting phase for event type: " + type);
		}
		for(PerformanceInterceptor i : interceptors)
			i.handleEvent(type, id);
		return id;
	}
	public static void event(EventType type, String correlationID, Object ... userObjects) {
		for(PerformanceInterceptor i : interceptors)
			i.handleEvent(type, correlationID, userObjects);
	}
	public static void addInterceptor(PerformanceInterceptor inter) {
		interceptors.add(inter);
	}

	public void handleEvent(EventType type, String correlationID, Object ... userObjects) {
		try {
			long time = System.currentTimeMillis();
			if(!eventTimes.containsKey(type)) {
				eventTimes.put(type, new HashMap<String,Long>());
			}
			eventTimes.get(type).put(correlationID, time);
			if(type.name() != null && correlationID != null & type.name().startsWith("FINISH_")) {
				EventType startType = EventType.valueOf("START_" + type.name().substring("FINISH_".length()));
				if(startType != null && eventTimes.containsKey(startType)) {
					if(eventTimes.get(startType).containsKey(correlationID)) {
						if(verbose) {
							long start = eventTimes.get(startType).get(correlationID);
							System.out.println("Interceptor: " + startType + " -> " + type + " : " + (time - start) + "ms");
						}
						eventTimes.get(startType).remove(correlationID);
						eventTimes.get(type).remove(correlationID);
					}
				}
			}
		} catch (Exception e) {
			logger.warn("Unable to handle performance interceptor event.", e);
		}
		
	}
	
	public long getSumOfDurations(EventType startType, EventType finishType) {
		long total = 0;
		if(!eventTimes.containsKey(startType))
			eventTimes.put(startType, new HashMap<String,Long>());
		if(!eventTimes.containsKey(finishType))
			eventTimes.put(finishType, new HashMap<String,Long>());
		for(String id : eventTimes.get(startType).keySet()) {
			Long start = eventTimes.get(startType).get(id);
			Long end = eventTimes.get(finishType).get(id);
			if(start != null && end != null) {
				total += end - start;
			}
		}
		return total;
	}
	
	public List<String> getUnfinishedEvents() {
		List<String> result = new LinkedList<String>();
		//TODO
		return result;
	}
	
	public static void setVerbose(boolean v) {
		verbose = v;
	}

	public long getXQueryDurations() {
		return getSumOfDurations(EventType.START_XQUERY, EventType.FINISH_XQUERY);
	}
	public long getInvocationDurations() {
		return getSumOfDurations(EventType.START_INVOCATION, EventType.FINISH_INVOCATION);
	}
	
	public static PerformanceInterceptor getDefaultInterceptor() {
		return defaultInterceptor;
	}
	
	public void reset() {
		eventTimes.clear();
	}
	
}

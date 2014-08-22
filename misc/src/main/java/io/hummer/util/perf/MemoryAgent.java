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

import io.hummer.util.log.LogUtil;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.*;

import org.apache.log4j.Logger;

/**
 * Taken from http://www.javaspecialists.eu/archive/Issue142.html
 * 
 * In order to use this code, the java process of your application needs 
 * to be started with the flag -javaagent:/path/to/InfosysUtil.jar
 */
@SuppressWarnings("all")
public class MemoryAgent {

	private static Instrumentation instrumentation;

	private static boolean warningLogged = false;
	private static Logger logger = LogUtil.getLogger(MemoryAgent.class);

	/** Initializes agent */
	public static void premain(String agentArgs, Instrumentation instrumentation) {
		MemoryAgent.instrumentation = instrumentation;
	} 

	/** Returns object size. */
	public static long sizeOf(Object obj) {
		if (instrumentation == null) {
			throw new IllegalStateException(
					"Instrumentation environment not initialised.");
		}
		if (isSharedFlyweight(obj)) {
			return 0;
		}
		return instrumentation.getObjectSize(obj);
	}

	/**
	 * Calls deepSizeOf(..) of this class and returns -1 
	 * if the invocation throws an exception.
	 * @param obj
	 * @return
	 */
	public static long getSafeDeepSizeOf(Object obj) {
		try {
			return deepSizeOf(obj);
		} catch (Exception e) {
			if(!warningLogged) {
				logger.info("Unable to get size of Object. Instrumentation environment might not have been initialized: " + e);
				warningLogged = true;
			}
			return -1;
		}
	}
	
	public static long deepSizeOf(Object obj) {
		return deepSizeOf(obj, new IdentityHashMap<Object, Object>());
	}
	
	public static long deepSizeOf(Object obj, IdentityHashMap visited) {
		return deepSizeOf(obj, null, visited);
	}

	/**
	 * Returns deep size of object, recursively iterating over its fields and
	 * superclasses.
	 */
	public static long deepSizeOf(Object obj, Object lock, IdentityHashMap visited) {
		if(visited == null)
			visited = new IdentityHashMap();
		Stack stack = new Stack();
		stack.push(obj);

		if(lock == null)
			lock = new Object();

		long result = 0;
		synchronized(lock) {
			do {
				result += internalSizeOf(stack.pop(), stack, visited);
			} while (!stack.isEmpty());
		}
		return result;
	}

	/**
	 * Returns true if this is a well-known shared flyweight. For example,
	 * interned Strings, Booleans and Number objects
	 */
	private static boolean isSharedFlyweight(Object obj) {
		// optimization - all of our flyweights are Comparable
		if (obj instanceof Comparable) {
			if (obj instanceof Enum) {
				return true;
			} else if (obj instanceof String) {
				return (obj == ((String) obj).intern());
			} else if (obj instanceof Boolean) {
				return (obj == Boolean.TRUE || obj == Boolean.FALSE);
			} else if (obj instanceof Integer) {
				return (obj == Integer.valueOf((Integer) obj));
			} else if (obj instanceof Short) {
				return (obj == Short.valueOf((Short) obj));
			} else if (obj instanceof Byte) {
				return (obj == Byte.valueOf((Byte) obj));
			} else if (obj instanceof Long) {
				return (obj == Long.valueOf((Long) obj));
			} else if (obj instanceof Character) {
				return (obj == Character.valueOf((Character) obj));
			}
		}
		return false;
	}

	private static boolean skipObject(Object obj, Map visited) {
		return obj == null || isSharedFlyweight(obj) || visited.containsKey(obj);
	}

	private static long internalSizeOf(Object obj, Stack stack, Map visited) {
		if (skipObject(obj, visited)) {
			return 0;
		}

		Class clazz = obj.getClass();
		if (clazz.isArray()) {
			addArrayElementsToStack(clazz, obj, stack);
		} else {
			// add all non-primitive fields to the stack
			while (clazz != null) {
				Field[] fields = clazz.getDeclaredFields();
				for (Field field : fields) {
					if (!Modifier.isStatic(field.getModifiers())
							&& !field.getType().isPrimitive()) {
						field.setAccessible(true);
						try {
							Object o = field.get(obj);
							if(o != null)
								stack.add(o);
						} catch (IllegalAccessException ex) {
							throw new RuntimeException(ex);
						}
					}
				}
				clazz = clazz.getSuperclass();
			}
		}
		visited.put(obj, null);
		return sizeOf(obj);
	}

	private static void addArrayElementsToStack(Class clazz, Object obj,
			Stack stack) {
		if (!clazz.getComponentType().isPrimitive()) {
			int length = Array.getLength(obj);
			for (int i = 0; i < length; i++) {
				stack.add(Array.get(obj, i));
			}
		}
	}
}

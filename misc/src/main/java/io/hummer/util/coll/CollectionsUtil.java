package io.hummer.util.coll;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Often used utility functions for handling Java collections.
 * @author Waldemar Hummer
 */
public class CollectionsUtil {
	
	private static final CollectionsUtil instance = new CollectionsUtil();

	public static CollectionsUtil getInstance() {
		return instance;
	}

	public <T1, T2> List<Map<T1, T2>> generateCombinations(
			Map<T1, ? extends Collection<T2>> values) {
		List<Map<T1, T2>> result = new LinkedList<Map<T1, T2>>();
		generateCombinations(new HashMap<T1, Collection<T2>>(values),
				new HashMap<T1, T2>(), result,
				new LinkedList<T1>(values.keySet()));
		return result;
	}

	private <T1, T2> void generateCombinations(
			Map<T1, ? extends Collection<T2>> values, Map<T1, T2> valuesSoFar,
			List<Map<T1, T2>> allResults, List<T1> keyList) {

		if(valuesSoFar.size() >= values.size()) {
			if(!allResults.contains(valuesSoFar)) {
				allResults.add(valuesSoFar);
			}
			return;
		}

		if(keyList.isEmpty())
			return;

		T1 key = keyList.get(0);

		List<T1> keyListCopy = new LinkedList<T1>(keyList);
		keyListCopy.remove(key);

		for(T2 value : values.get(key)) {
			Map<T1, T2> valuesSoFarCopy = new HashMap<T1, T2>(valuesSoFar);
			valuesSoFarCopy.put(key, value);
			generateCombinations(values, valuesSoFarCopy, allResults,
					keyListCopy);
		}
	}

	public <T> List<Set<T>> generateCombinations(Collection<T> values) {
		return generateCombinations(values, 1, values.size());
	}

	public <T> List<Set<T>> generateCombinations(Collection<T> values,
			int combinationLength) {
		return generateCombinations(values, combinationLength,
				combinationLength);
	}

	public <T> List<Set<T>> generateCombinations(Collection<T> values,
			int minCombinationLength, int maxCombinationLength) {
		List<Set<T>> result = new LinkedList<Set<T>>();

		for(int i = minCombinationLength; i <= maxCombinationLength; i++) {
			int combinationLength = i;
			generateCombinations(new LinkedList<T>(values), new HashSet<T>(),
					result, combinationLength);
		}
		return result;
	}

	private <T> void generateCombinations(Collection<T> values,
			Set<T> valuesSoFar, List<Set<T>> allResults, int combinationLength) {

		if(valuesSoFar.size() == combinationLength) {
			if(!allResults.contains(valuesSoFar)) {
				allResults.add(valuesSoFar);
			}
			return;
		}

		for(T value : values) {
			if(!valuesSoFar.contains(value)) {
				Set<T> valuesSoFarCopy = new HashSet<T>(valuesSoFar);
				valuesSoFarCopy.add(value);
				List<T> valuesCopy = new LinkedList<T>(values);
				valuesCopy.remove(value);
				generateCombinations(valuesCopy, valuesSoFarCopy, allResults,
						combinationLength);
			}
		}
	}

	public String[] generateArray(String namePattern, List<?> values) {
		return generateArray(namePattern, "<placeholder>", values);
	}
	public String[] generateArray(String namePattern, String placeholder, List<?> values) {
		List<String> result = new LinkedList<String>();
		for(Object i : values) {
			result.add(namePattern.replace(placeholder, ""+i));
		}
		return result.toArray(new String[0]);
	}

	public List<Integer> createSequence(int end) {
		return createSequence(0, end);
	}
	
	public List<Integer> createSequence(int start, int end) {
		return createSequence(start, end, 1);
	}
	
	public List<Integer> createSequence(int start, int end, int increment) {
		List<Integer> result = new ArrayList<Integer>();
		for(int i = start; i <= end; i += increment)
			result.add(i);
		return result;
	}
	

	private class ObjectComparator implements Comparator<Object> {
		@SuppressWarnings({"unchecked","rawtypes"})
		public int compare(Object o2, Object o1) {

			if(o1 instanceof AtomicInteger)
				o1 = ((AtomicInteger) o1).get();
			if(o2 instanceof AtomicInteger)
				o2 = ((AtomicInteger) o2).get();
			if(o1 instanceof AtomicLong)
				o1 = ((AtomicLong) o1).get();
			if(o2 instanceof AtomicLong)
				o2 = ((AtomicLong) o2).get();

			if((o1 instanceof Comparable<?>) && (o2 instanceof Comparable<?>)) {
				return ((Comparable) o1).compareTo(o1);
			}
			if((o1 instanceof Comparable<?>) && (o2 instanceof Comparable<?>)) {
				Number number1 = (Number) o1;
				Number number2 = (Number) o2;
				if(((Object) number2).getClass().equals(
						((Object) number1).getClass())) {
					if(number1 instanceof Comparable) {
						return ((Comparable) number1).compareTo(number2);
					}
				}
				if(Math.abs(number1.doubleValue() - number2.doubleValue()) < 0.0000001) // small
																						// threshold
					return 0;
				if(number1.doubleValue() < number2.doubleValue())
					return -1;
				if(number1.doubleValue() > number2.doubleValue())
					return 1;
			}
			throw new IllegalArgumentException(
					"Not sure how to compare objects of the following types: "
							+ o1.getClass() + " - " + o2.getClass());
		}
	}

	public void removeKeyWithSmallestValue(Map<?, ?> map) {
		if(map.size() <= 0)
			return;
		Object minKey = map.keySet().iterator().next();
		Object minValue = map.get(minKey);
		ObjectComparator c = new ObjectComparator();
		for(Object k : new HashSet<Object>(map.keySet())) {
			if(c.compare(map.get(k), minValue) < 0) {
				minValue = map.get(k);
				minKey = k;
			}
		}
		map.remove(minKey);
	}

	public List<?> flattenValues(Object values) {
		if(values instanceof Map<?,?>) 
			return flattenValues((Map<?,?>)values);
		else if(values instanceof List<?>) 
			return flattenValues((List<?>)values);
		return new LinkedList<Object>(Arrays.asList(values));
	}
	public List<?> flattenValues(Map<?,?> values) {
		List<Object> result = new LinkedList<Object>();
		for(Object o : values.values()) {
			result.addAll(flattenValues(o));
		}
		return result;
	}
	public List<?> flattenValues(List<?> values) {
		List<Object> result = new LinkedList<Object>();
		for(Object o : values) {
			result.addAll(flattenValues(o));
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public <T extends Number> List<T> sort(List<T> in) {
		Object[] copy = in.toArray();
		Arrays.sort(copy);
		List<T> result = new LinkedList<T>();
		for(Object o : copy) {
			result.add((T) o);
		}
		return result;
	}

	public List<String> toStringList(List<?> list) {
		List<String> result = new LinkedList<String>();
		for(Object o : list) {
			result.add("" + o);
		}
		return result;
	}

	@SuppressWarnings("all")
	public <T> Set<T> union(Set<T>... sets) {
		Set<T> result = new HashSet<T>();
		for(Set<T> s : sets)
			result.addAll(s);
		return result;
	}

	public <T> boolean inRange(T[] array, int index) {
		return index > 0 && index < array.length;
	}

	public <K, V> MapBuilder<K, V> asMap(K key, V value) {
	    return new MapBuilder<K, V>().entry(key, value);
	}
	
	public static class MapBuilder<K, V> extends HashMap<K, V> {
		private static final long serialVersionUID = 1L;

		public MapBuilder() { }
		public MapBuilder(K key, V value) {
			entry(key, value);
		}
		public MapBuilder<K, V> entry(K key, V value) {
	        this.put(key, value);
	        return this;
	    }
		public static <K,V> MapBuilder<K,V> map(K key, V value) {
			return new MapBuilder<K, V>(key, value);
		}
	}

	/**
	 * Shift the entries of an array by a fixed number of positions to
	 * the right (positive) or to the left (negative). Drops any entries that .
	 * @param array the array to modify
	 * @param numPositions positive or negative number of index positions
	 */
	public <T> void shift(T[] array, int numPositions) {
		if(numPositions > 0) {
			System.arraycopy(array, 0, array, numPositions, array.length-numPositions);
//			for(int i = array.length - 1; i >= numPositions; i --) {
//				if(inRange(array, i)) {
//					if(inRange(array, i - numPositions)) {
//						array[i] = array[i - numPositions];
//					}
//					//if(i < numPositions)
//					//	array[i] = null;
//				}
//			}
//			for(int i = 0; i < numPositions; i ++) {
//				array[i] = null;
//			}
//			for(int i = array.length - numPositions - 1; i >= 0; i --) {
//				if(inRange(array, i)) {
//					if(inRange(array, i + numPositions)) {
//						array[i + numPositions] = array[i];
//					}
//					if(i < numPositions)
//						array[i] = null;
//				}
//			}
		} else if(numPositions < 0) {
			for(int i = numPositions; i <= array.length; i ++) {
				if(inRange(array, i)) {
					if(inRange(array, i - numPositions)) {
						array[i - numPositions] = array[i];
					}
					if(i >= array.length - numPositions)
						array[i] = null;
				}
			}
		}
	}
	
	public <T> T[] concat(T item, T[] items) {
		items = Arrays.copyOf(items, items.length + 1);
		shift(items, 1);
		items[0] = item;
		return items;
	}
	public <T> T[] concat(T[] items, T item) {
		return append(items, item);
	}
	public <T> T[] append(T[] items, T item) {
		items = Arrays.copyOf(items, items.length + 1);
		items[items.length - 1] = item;
		return items;
	}

	public <T> T getRandom(Collection<T> items) {
		if(items == null || items.isEmpty())
			return null;
		List<T> list = null;
		if(!(items instanceof List<?>)) {
			list = new LinkedList<T>(items);
		} else {
			list = (List<T>)items;
		}
		int index = (int)((double)list.size() * Math.random());
		return list.get(index);
	}

	public String join(List<?> list) {
		return join(list, "\n");
	}
	public String join(List<?> list, String delimiter) {
		StringBuilder b = new StringBuilder();
		int counter = 0;
		for(Object o : list) {
			b.append("" + o);
			if(++counter < list.size()) {
				b.append(delimiter);
			}
		}
		return b.toString();
	}
}

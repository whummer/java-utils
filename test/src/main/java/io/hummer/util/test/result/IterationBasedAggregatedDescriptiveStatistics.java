package io.hummer.util.test.result;

import io.hummer.util.coll.Pair;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

/**
 * Used for storing and analyzing performance test results, 
 * mostly in combination with GenericTestResult.
 */
public class IterationBasedAggregatedDescriptiveStatistics<KeyType extends Comparable<KeyType>> {
//	List<KeyType> keyOrder = new LinkedList<KeyType>();
//	Map<KeyType,Double> values = new HashMap<KeyType,Double>();
	List<Entry<KeyType>> values = new LinkedList<Entry<KeyType>>();

	public static class Range<KeyType extends Comparable<KeyType>> {
		KeyType fromKey, toKey;

		public boolean isInRange(KeyType key) {
			return key.compareTo(fromKey) >= 0 && key.compareTo(toKey) < 0;
		}

		@Override
		public String toString() {
			return "Range[" + fromKey + "," + toKey + "]";
		}
	}
	public static class RangesGeneratorDefault extends RangesGenerator<Double> {
		private double start;
		private double step;
		private double end;
		//		double modulo;
		public RangesGeneratorDefault(double start, double step, double end) {
//			this.modulo = modulo;
			this.start = start;
			this.step = step;
			this.end = end;
		}
		public List<Range<Double>> getRanges() {
			List<Range<Double>> result = new LinkedList<Range<Double>>();
			for(double i = start; i <= end - step; i += step) {
				Range<Double> range = new Range<Double>();
				range.fromKey = i;
				range.toKey = i + step;
				result.add(range);
			}
			return result;
		}
//		@Override
//		public boolean closeAndStartNewRange(Double currentKey, List<Double> nextKeys) {
//			if(nextKeys.isEmpty()) {
//				return true;
//			}
//			Double nextKey = nextKeys.get(0);
//			if(zeroBased && nextKey > currentKey && nextKey % modulo == 0) {
//				return true;
//			}
//			if(!zeroBased && nextKey > currentKey && currentKey % modulo == 0) {
//				return true;
//			}
//			return false;
//		}
	}
	public static class Entry<KeyType> {
		KeyType key;
		Double value;
		public Entry(KeyType key, Double value) {
			this.key = key;
			this.value = value;
		}
		@Override
		public String toString() {
			return "Entry[key=" + key + ",value=" + value + "]";
		}
	}

	public static abstract class RangesGenerator<KeyType extends Comparable<KeyType>> {
//		public abstract boolean closeAndStartNewRange(
//				KeyType next, List<KeyType> nextKeys);
		public abstract List<Range<KeyType>> getRanges();
	}

	public void addValue(KeyType key, double value) {
//		values.put(key, value);
//		keyOrder.add(key);
		values.add(new Entry<KeyType>(key, value));
	}
	public DescriptiveStatistics getStatistics() {
		DescriptiveStatistics stats = new DescriptiveStatistics();
		for(Entry<KeyType> e : values) {
			stats.addValue(e.value);
		}
		return stats;
	}
	public List<Pair<Range<KeyType>,DescriptiveStatistics>> getStatistics(
			RangesGenerator<KeyType> gen) {
		List<Pair<Range<KeyType>,DescriptiveStatistics>> result = 
				new LinkedList<Pair<Range<KeyType>,DescriptiveStatistics>>();
		for(Pair<Range<KeyType>,List<Double>> entry : getValues(gen)) {
			DescriptiveStatistics d = new DescriptiveStatistics();
			for(Double val : entry.getSecond()) {
				d.addValue(val);
			}
			result.add(new Pair<Range<KeyType>,
					DescriptiveStatistics>(entry.getFirst(),d));
		}
		return result;
	}
//	public List<Pair<Range<KeyType>,List<Double>>> getValues(
//			RangesGenerator<KeyType> gen) {
//		Range<KeyType> current = null;
//		List<Pair<Range<KeyType>,List<Double>>> result = 
//				new LinkedList<Pair<Range<KeyType>,List<Double>>>();
//		List<Double> currentValues = new LinkedList<Double>();
//		for(int i = 0; i < values.size(); i ++) {
//			Entry<KeyType> e1 = values.get(i);
//			
//			//Entry<KeyType> e2 = values.get(i + 1);
//			currentValues.add(e1.value);
//			if(current == null) {
//				current = new Range<KeyType>();
//				current.fromKey = e1.key;
//			}
//			List<KeyType> nextKeys = new LinkedList<KeyType>();
//			for(int j = i + 1; j < values.size(); j ++) {
//				nextKeys.add(values.get(j).key);
//			}
//			if(gen.closeAndStartNewRange(e1.key, nextKeys)) {
//				current.toKey = e1.key;
//				result.add(new Pair<Range<KeyType>, 
//						List<Double>>(current, currentValues));
//				current = null;
//				currentValues = new LinkedList<Double>();
//			}
//		}
//		return result;
//	}
	public List<Pair<Range<KeyType>,List<Double>>> getValues(
			RangesGenerator<KeyType> gen) {
		List<Pair<Range<KeyType>,List<Double>>> result = 
				new LinkedList<Pair<Range<KeyType>,List<Double>>>();
		for(Range<KeyType> r : gen.getRanges()) {
			result.add(new Pair<Range<KeyType>,List<Double>>(
					r, getValues(r)));
		}
		return result;
	}
	private List<Double> getValues(Range<KeyType> r) {
		List<Double> result = new LinkedList<Double>();
		for(Entry<KeyType> entry : values) {
			if(r.isInRange(entry.key)) {
				result.add(entry.value);
			}
		}
		return result;
	}

	public void clear() {
		values.clear();
	}
	
	/**
	 * Default implementation.
	 */
	public static class IterationBasedAggregatedDescriptiveStatisticsDefault extends 
			IterationBasedAggregatedDescriptiveStatistics<Double> {
//		public List<Pair<Range<Double>, DescriptiveStatistics>> getStatistics(
//				double stepSize) {
//			double to = 0;
//			if(!values.isEmpty()) {
//				to = values.get(values.size() - 1).key;
//				to = ((double)((int)(to / stepSize)) + 1.0) * stepSize;
//			}
//			return getStatistics(0, stepSize, to);
//		}
		public List<Pair<Range<Double>, DescriptiveStatistics>> getStatistics(
				double steps, double to) {
			return getStatistics(0, steps, to);
		}
		public List<Pair<Range<Double>, DescriptiveStatistics>> getStatistics(
				double from, double steps, double to) {
			to = ((double)((int)(to / steps)) + 1.0) * steps;
			return getStatistics(new RangesGeneratorDefault(from, steps, to));
		}
		public Pair<Range<Double>, DescriptiveStatistics> getLastStatistics(double modulo, double timeTo) {
			return getLastStatistics(modulo, timeTo, new double[]{0});
		}
		public Pair<Range<Double>, DescriptiveStatistics> getLastStatistics(double modulo, double timeTo, double[] valuesIfEmpty) {
			List<Pair<Range<Double>, DescriptiveStatistics>> list = getStatistics(modulo, timeTo);
			if(list.isEmpty()) {
				return new Pair<Range<Double>, DescriptiveStatistics>(null, new DescriptiveStatistics(valuesIfEmpty));
			}
			return list.get(list.size() - 1);
		}
	}
}

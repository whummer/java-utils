package io.hummer.util.coll;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 
 * This class implements the delegator pattern for the java.util.Map interface.
 * 
 * @author Waldemar Hummer
 *
 * @param <K> key class
 * @param <V> value class
 */
public class MapDelegator<K,V> implements Map<K,V> {

	protected Map<K,V> underlyingMap;

	public MapDelegator(Map<K,V> delegate) {
		underlyingMap = delegate;
	}
	
	public void clear() {
		underlyingMap.clear();
	}

	public boolean containsKey(Object arg0) {
		return underlyingMap.containsKey(arg0);
	}

	public boolean containsValue(Object arg0) {
		return underlyingMap.containsValue(arg0);
	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return underlyingMap.entrySet();
	}

	public boolean equals(Object arg0) {
		return underlyingMap.equals(arg0);
	}

	public V get(Object arg0) {
		return underlyingMap.get(arg0);
	}

	public int hashCode() {
		return underlyingMap.hashCode();
	}

	public boolean isEmpty() {
		return underlyingMap.isEmpty();
	}

	public Set<K> keySet() {
		return underlyingMap.keySet();
	}

	public V put(K arg0, V arg1) {
		return underlyingMap.put(arg0, arg1);
	}

	public void putAll(Map<? extends K, ? extends V> arg0) {
		underlyingMap.putAll(arg0);
	}

	public V remove(Object arg0) {
		return underlyingMap.remove(arg0);
	}

	public int size() {
		return underlyingMap.size();
	}

	public Collection<V> values() {
		return underlyingMap.values();
	}
	
}

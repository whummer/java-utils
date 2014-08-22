package io.hummer.util.coll;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A hash map whose size can be limited to maxSize. If the size is 
 * greater than maxSize, older elements will be deleted from the 
 * HashSet until size <= maxSize. Elements are removed in FIFO order
 * (iteration order is preserved as the class extends LinkedHashMap).
 * 
 * This class is useful for implementing simple object caches.
 * 
 * @author Waldemar Hummer
 */
public class LimitedSizeHashMap<K,V> extends LinkedHashMap<K,V> {
	private static final long serialVersionUID = 1L;

	private int maxSize;

	public LimitedSizeHashMap(int maxSize) {
		this.maxSize = maxSize;
	}

	@Override
	public V put(K key, V value) {
		if(size() >= maxSize && !containsKey(key)) {
			removeOneElement();
		}
		/* We want to make sure that often requested
		 * keys are not flushed out of the map, hence we
		 * remove the key here first in order to (re-)add 
		 * it, in which case it gets pushed back to the 
		 * end of the keys list and hence remains in the 
		 * map for some longer time. */
		super.remove(key);

		return super.put(key, value);
	}
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		/* We want to make sure that often requested
		 * keys are not flushed out of the map, hence we
		 * remove all keys here first (also see put(k,v) method). */
		for(K k : m.keySet()) {
			super.remove(k);
		}

		super.putAll(m);
		shrinkToMaxSize();
	}

	private void shrinkToMaxSize() {
		Iterator<Map.Entry<K,V>> iter = entrySet().iterator();
		while(size() > maxSize && iter.hasNext()) {
			iter.next();
			iter.remove();
		}
	}

	private Map.Entry<K,V> removeOneElement() {
		Iterator<Map.Entry<K,V>> iter = entrySet().iterator();
		if(iter.hasNext()) {
			Map.Entry<K,V> o = iter.next();
			iter.remove();
			return o;
		}
		return null;
	}
}

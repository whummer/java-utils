package io.hummer.util.coll;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * A hashset whose size can be limited to maxSize. If the size is 
 * greater than maxSize, older elements will be deleted from the 
 * HashSet until size <= maxSize. Elements are removed in FIFO order
 * (iteration order is preserved as the class extends LinkedHashSet).
 * 
 * This class is useful for implementing simple object caches.
 * 
 * @author Waldemar Hummer
 */
public class LimitedSizeHashSet<T> extends LinkedHashSet<T> {
	private static final long serialVersionUID = 1L;

	private int maxSize;

	public LimitedSizeHashSet(int maxSize) {
		this.maxSize = maxSize;
	}

	public boolean add(T e) {
		if(size() >= maxSize && !contains(e)) {
			removeOneElement();
		}
		/* We want to make sure that often requested
		 * keys are not flushed out of the map, hence we
		 * remove the key here first in order to (re-)add 
		 * it, in which case it gets pushed back to the 
		 * end of the keys list and hence remains in the 
		 * map for some longer time. */
		remove(e);

		return super.add(e);
	}
	@Override
	public boolean addAll(Collection<? extends T> c) {
		/* We want to make sure that often requested keys
		 * are not flushed out of the map, hence we remove
		 * all keys here first (also see add(..) method). */
		removeAll(c);

		boolean result = super.addAll(c);
		shrinkToMaxSize();
		return result;
	}

	private void shrinkToMaxSize() {
		Iterator<T> iter = iterator();
		while(size() > maxSize && iter.hasNext()) {
			iter.next();
			iter.remove();
		}
	}

	private T removeOneElement() {
		Iterator<T> iter = iterator();
		if(iter.hasNext()) {
			T o = iter.next();
			iter.remove();
			return o;
		}
		return null;
	}
}

package io.hummer.util.coll;

/*
 * https://trac.tk.informatik.tu-darmstadt.de/svn/projects/mundo/MundoComposer/MundoComposer/src/org/mundo/util/BlockingMap.java
 */
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <H1>A Blocking Map</H1> This class implements a blocking map, that acts as a
 * synchronizer between the producers of a objects and the consumers.
 * <p>
 * Object are set with <code>put()</code>only ONCE. Further attempts to set an
 * object are just ignored. Consumers request the object with
 * <code>get(key)</code> or with <code>take(take)</code>. If the object is not
 * already set, consumers are blocked waiting until the object is available or
 * until an interrupt (InteruptedException) terminates the wait.
 * <code>take(take)</code> gets and removes the object from the map. The map can
 * be tested for object availability with isAvailable(), which answers true if
 * the object has already been set.
 * 
 * @author Sarveswaran M
 * @version 1.1, 08/12/08 based on the implementation by Alfred Peisl simplified
 *          to implement Map by Stefan Radomski
 */
public class BlockingMap<K, V> implements Map<K, V> {

	/** The queues map. */
	private final ConcurrentMap<K, ObjectLatch<V>> map = new ConcurrentHashMap<K, ObjectLatch<V>>();

	/** lock for remove invariant */
	private final Lock removeLock = new ReentrantLock();

	/**
	 * map containing blocked threads,necessary to interrupt all threads waiting
	 * on keys in a cleared map
	 */
	private final Map<Thread, ObjectLatch<?>> blockedThreadsMap = new ConcurrentHashMap<Thread, ObjectLatch<?>>();

	/**
	 * Sets the object with the given key if it is not already set. Otherwise
	 * ignore this request.
	 * 
	 * @param key
	 *            object key
	 * @param object
	 *            the object
	 */
	public V put(K key, V object) {
		ObjectLatch<V> latch = map.get(key);

		// part of remove invariant,should be locked
		removeLock.lock();
		try {
			if (latch == null) {
				map.putIfAbsent(key, new ObjectLatch<V>());
				latch = map.get(key);
			}
		} finally {
			removeLock.unlock();
		}

		latch.set(object);
		return null;
	}

	/**
	 * Get the object with the given key if it is already available (has already
	 * been set).
	 * <p>
	 * If it is not available, wait until it is or until an interrupt
	 * (InterruptedException) terminates the wait.
	 * 
	 * @param key
	 *            object key
	 * @return the object if it is already available
	 * @throws InterruptedException
	 *             if map is cleared while waiting on this get
	 */
	@SuppressWarnings("all")
	public V get(Object key) {
		V result = null;
		ObjectLatch<V> latch = map.get(key);

		// part of remove invariant,should be locked
		removeLock.lock();
		try {
			if (latch == null) {
				map.putIfAbsent((K) key, new ObjectLatch<V>());
				latch = map.get(key);
			}
		} finally {
			removeLock.unlock();
		}

		// put thread in map before awaiting
		blockedThreadsMap.put(Thread.currentThread(), latch);
		try {
			result = latch.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// remove thread after awaiting
		blockedThreadsMap.remove(Thread.currentThread());

		return result;
	}

	/**
	 * Checks if the object is already available (has been already set).
	 * 
	 * @param key
	 *            object key
	 * @return true, if the object is already available (has been already set)
	 */
	public boolean isAvailable(K key) {
		// ==> part of remove invariant, should be locked
		removeLock.lock();
		try {
			ObjectLatch<V> latch = map.get(key);
			return latch != null && latch.isAvailable();
		} finally {
			removeLock.unlock();
		}
	}

	/**
	 * Answer and remove the object with the given key if it is already
	 * available (has already been set).
	 * <p>
	 * If it is not available, wait until it is or until an interrupt
	 * (InterruptedException) terminates the wait.
	 * 
	 * @param key
	 *            object key
	 * @return the object if it is already available (has already been set)
	 * @throws InterruptedException
	 *             if map is cleard while waiting on this take
	 */
	public V take(K key) throws InterruptedException {
		V result = null;
		ObjectLatch<V> latch = map.get(key);

		// part of remove invariant,should be locked
		removeLock.lock();
		try {
			if (latch == null) {
				map.putIfAbsent(key, new ObjectLatch<V>());
				latch = map.get(key);
			}
		} finally {
			removeLock.unlock();
		}

		// put thread in map before awaiting
		blockedThreadsMap.put(Thread.currentThread(), latch);
		result = latch.get();
		// remove thread after awaiting
		blockedThreadsMap.remove(Thread.currentThread());

		// ==> part of remove invariant, should be locked
		removeLock.lock();
		try {
			map.remove(key);
		} finally {
			removeLock.unlock();
		}
		return result;
	}

	/**
	 * Removes all mappings from this map.
	 * <p>
	 * 
	 * Interrupts any threads waiting on any key in map before clearing. This is
	 * done to prevent threads being blocked forever
	 * 
	 */
	@SuppressWarnings("all")
	public void clear() {

		Set<ObjectLatch<?>> waitingLatches = new HashSet<ObjectLatch<?>>();

		// part of remove invariant,should be locked
		removeLock.lock();
		try {
			for (Iterator<Thread> iterator = blockedThreadsMap.keySet()
					.iterator(); iterator.hasNext();) {
				Thread thread = iterator.next();
				thread.interrupt();
			}
			map.clear();
		} finally {
			removeLock.unlock();
		}
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	@SuppressWarnings("all")
	public V remove(Object key) {
		if (map.containsKey(key)) {
			try {
				return take((K) key);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	@SuppressWarnings("all")
	public boolean containsKey(Object key) {
		return isAvailable((K) key);
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public boolean equals(Object o) {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public int hashCode() {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public boolean isEmpty() {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public Set<K> keySet() {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public int size() {
		throw new UnsupportedOperationException();
	}

	/**
	 * operation not suppported
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public Collection<V> values() {
		throw new UnsupportedOperationException();
	}

	/** 
	 * Simple test program
	 * @param args
	 */
	public static void main(String[] args) {
		final BlockingMap<String,Object> map = new BlockingMap<String,Object>();
		new Thread() {
			public void run() {
				try {
					System.out.println(map.get("foo"));
					System.out.println(map.take("foo"));
					System.out.println(map.get("bar"));
					System.out.println(map.get("bar"));
					System.out.println(map.take("bar"));
					System.out.println(map.take("foo"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
		map.put("foo", "foo1");
		map.put("bar", "bar");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		map.put("foo", "foo2");
	}

}

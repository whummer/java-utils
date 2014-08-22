package io.hummer.util.coll;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DoubleHashSet<T1,T2> {

	private Map<T1,Set<T2>> map1 = new HashMap<T1, Set<T2>>();
	private Map<T2,Set<T1>> map2 = new HashMap<T2, Set<T1>>();

	public Set<T2> get1(T1 key) {
		Set<T2> result = map1.get(key);
		if(result == null) {
			result = new HashSet<T2>();
			map1.put(key, result);
		}
		return result;
	}
	public Set<T1> get2(T2 key) {
		Set<T1> result = map2.get(key);
		if(result == null) {
			result = new HashSet<T1>();
			map2.put(key, result);
		}
		return result;
	}
	public Set<T1> get1() {
		return map1.keySet();
	}
	public Set<T2> get2() {
		return map2.keySet();
	}

	public boolean put1(T1 o1, T2 o2) {
		if(!map1.containsKey(o1))
			map1.put(o1, new HashSet<T2>());
		return map1.get(o1).add(o2);
	}
	public boolean put2(T2 o1, T1 o2) {
		if(!map2.containsKey(o1))
			map2.put(o1, new HashSet<T1>());
		return map2.get(o1).add(o2);
	}
	
	public void remove(T1 o1, T2 o2) {
		Set<T2> s1 = get1(o1);
		if(s1 != null)
			s1.remove(o2);
		Set<T1> s2 = get2(o2);
		if(s2 != null)
			s2.remove(o1);
	}
	public void remove1(T1 o) {
		map1.remove(o);
		for(Set<T1> s : map2.values()) {
			s.remove(o);
		}
	}
	public void remove2(T2 o) {
		map2.remove(o);
		for(Set<T2> s : map1.values()) {
			s.remove(o);
		}		
	}

	public boolean containsKey1(T1 key) {
		return map1.containsKey(key);
	}
	public boolean containsKey2(T2 key) {
		return map2.containsKey(key);
	}
	
	public int maxSize() {
		return Math.max(map1.size(), map2.size());
	}
	
}

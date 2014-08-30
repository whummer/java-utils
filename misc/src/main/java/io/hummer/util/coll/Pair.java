package io.hummer.util.coll;

public class Pair<T1, T2> {
	private T1 t1;
	private T2 t2;
	
	public static class ComparablePair<T1 extends Comparable<T1>, T2 extends Comparable<T2>>
	extends Pair<T1, T2> implements Comparable<Pair<T1, T2>> {
		public ComparablePair(T1 t1, T2 t2) {
			super(t1, t2);
		}
		
		@Override
		public int compareTo(Pair<T1, T2> other) {
			if(getFirst().compareTo(other.getFirst()) == 0)
				return getSecond().compareTo(other.getSecond());
			return getFirst().compareTo(other.getFirst());
		}
		
	}
	

	public Pair(T1 t1, T2 t2) {
		this.t1 = t1;
		this.t2 = t2;
	}

	public T1 getFirst() {
		return t1;
	}

	public T2 getSecond() {
		return t2;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((t1 == null) ? 0 : t1.hashCode());
		result = prime * result + ((t2 == null) ? 0 : t2.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		Pair<?, ?> other = (Pair<?, ?>) obj;
		if(t1 == null) {
			if(other.t1 != null)
				return false;
		} else if(!t1.equals(other.t1))
			return false;
		if(t2 == null) {
			if(other.t2 != null)
				return false;
		} else if(!t2.equals(other.t2))
			return false;
		return true;
	}

	public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>> ComparablePair<T1, T2> create(
			T1 t1, T2 t2) {
		return new ComparablePair<T1, T2>(t1, t2);
	}

	public static <T1, T2> Pair<T1, T2> create(T1 t1, T2 t2) {
		return new Pair<T1, T2>(t1, t2);
	}

	@Override
	public String toString() {
		return "Pair(" + t1 + "," + t2 + ")";
	}
}

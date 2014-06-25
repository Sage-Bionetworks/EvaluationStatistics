package org.sagebionetworks;

import java.util.SortedMap;
import java.util.TreeMap;

public class Util {
	
	/**
	 * ensures that the sequence of keys in the returned map include all intermediate key values,
	 * based on the given increment function.
	 * 
	 * @param in the input map, which is sorted but may have missing key values
	 * @param incrementer the function which returns the next higher value for a given value of K
	 * @param fillValue the fill value for intermediate, missing values
	 * @return
	 */
	public static <K extends Comparable<K>, T> SortedMap<K,T> 
		fillInMissing(SortedMap<K,T> in, Incrementer<K> incrementer, T fillValue) {
		SortedMap<K,T> result = new TreeMap<K,T>();
		K highestKeyInResult = null;
		for (K k : in.keySet()) {
			if (highestKeyInResult==null) {
				result.put(k, in.get(k));
				highestKeyInResult = k;
				continue;
			}
			while (highestKeyInResult.compareTo(k)<0) {
				K next = incrementer.increment(highestKeyInResult);
				int c = next.compareTo(k);
				if (c<0) {
					result.put(next, fillValue);
				} else if (c==0) {
					result.put(next,in.get(k)); // we've reached the next value
				} else {
					throw new IllegalStateException(); // should not reach here, inc didn't work right
				}
				highestKeyInResult = next;
			}
		}
		return result;
	}

}

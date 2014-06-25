package org.sagebionetworks;

/**
 * provides a function which increments a value of type T, returning the next possible value
 * 
 * @author brucehoff
 *
 */
public interface Incrementer<T> {
	public T increment(T in);
}

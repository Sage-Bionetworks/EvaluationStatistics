package org.sagebionetworks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Test;

public class UtilTest {
	
	@Test
	public void testFillInMissing() throws Exception {
		TreeMap<Integer, Integer> in = new TreeMap<Integer, Integer>();
		in.put(0, 1);
		in.put(1,  2);
		in.put(5, 3);
		int fillValue = -1;
		SortedMap<Integer, Integer> filledIn = Util.fillInMissing(
				in, 
				new Incrementer<Integer>() {
					public Integer increment(Integer in) {
						return new Integer(in+1);
					}
				}, fillValue);
		Iterator<Integer> keys = filledIn.keySet().iterator();
		assertEquals(0, (int)keys.next()); assertEquals(1, (int)filledIn.get(0));
		assertEquals(1, (int)keys.next()); assertEquals(2, (int)filledIn.get(1));
		assertEquals(2, (int)keys.next()); assertEquals(fillValue, (int)filledIn.get(2));
		assertEquals(3, (int)keys.next()); assertEquals(fillValue, (int)filledIn.get(3));
		assertEquals(4, (int)keys.next()); assertEquals(fillValue, (int)filledIn.get(4));
		assertEquals(5, (int)keys.next()); assertEquals(3, (int)filledIn.get(5));
		assertFalse(keys.hasNext());
	}

	
}

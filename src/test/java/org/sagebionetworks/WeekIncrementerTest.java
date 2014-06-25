package org.sagebionetworks;

import static org.junit.Assert.assertEquals;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class WeekIncrementerTest {


	@Test
	public void test() throws Exception {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date date = dateFormat.parse("2014-06-25");
		DateTime dateTime = new DateTime(date, DateTimeZone.UTC);
		DateTime beginningOfWeek = dateTime.weekOfWeekyear().roundFloorCopy();
		date = beginningOfWeek.toDate();
		
		Date oneWeekLater = (new WeekIncrementer()).increment(date);
		assertEquals(7*24*3600*1000L, oneWeekLater.getTime()-date.getTime());
	}

}

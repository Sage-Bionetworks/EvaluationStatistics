package org.sagebionetworks;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class WeekIncrementer implements Incrementer<Date> {

	public Date increment(Date date) {
		DateTime dateTime = new DateTime(date, DateTimeZone.UTC);
		dateTime.plusWeeks(1);
		return dateTime.toDate();
	}

}

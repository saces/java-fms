package plugins.FMS;

import java.sql.Date;
import java.util.Calendar;
import java.util.TimeZone;

public class Util {
	public static final String msgBase = "fms";
	public static int MaxIdentityRequests = 5;

	public static Date getSQLTimeOffset(int field, int amount) {
		Calendar utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		return new Date(utcDate.getTimeInMillis());
	}

	public static Date getSQLToday() {
		Calendar utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		long utcMSec = utcDate.getTimeInMillis();
		utcDate.setTimeInMillis(utcMSec - utcMSec % (86400 * 1000));
		return new Date(utcDate.getTimeInMillis());
	}
}

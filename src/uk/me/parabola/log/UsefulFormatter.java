/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 08-Sep-2007
 */
package uk.me.parabola.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Prints the message all on one line, which amazingly is not the default
 * behaviour in j.u.l, no wonder no one uses it.
 *
 * MUST be public whatever crazy static analyzers might say.
 *
 * @author Steve Ratcliffe
 */
public class UsefulFormatter extends Formatter {
	private boolean showTime = true;

	public String format(LogRecord record) {
		StringBuffer sb = new StringBuffer();

		if (showTime) {
			long millis = record.getMillis();
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(millis);
			sb.append(cal.get(Calendar.YEAR));
			sb.append('/');
			sb.append(fmt2(cal.get(Calendar.MONTH)+1));
			sb.append('/');
			sb.append(fmt2(cal.get(Calendar.DAY_OF_MONTH)));
			sb.append(' ');
			sb.append(fmt2(cal.get(Calendar.HOUR_OF_DAY)));
			sb.append(':');
			sb.append(fmt2(cal.get(Calendar.MINUTE)));
			sb.append(':');
			sb.append(fmt2(cal.get(Calendar.SECOND)));
			sb.append(' ');
		}
		
		sb.append(record.getLevel().getLocalizedName());
		sb.append(" (");
		sb.append(shortName(record.getLoggerName()));
		sb.append("): ");

		sb.append(record.getMessage());

		sb.append('\n');

		Throwable t = record.getThrown();
		if (t != null) {
			StringWriter out = new StringWriter();
			PrintWriter pw = new PrintWriter(out);
			t.printStackTrace(pw);
			sb.append(out.toString());
		}
		return sb.toString();
	}

	public void setShowTime(boolean showTime) {
		this.showTime = showTime;
	}

	private String fmt2(int i) {
		String res = String.valueOf(i);
		while (res.length() < 2) {
			res = '0' + res;
		}
		return res;
	}

	private String shortName(String name) {
		int end = name.lastIndexOf('.');
		if (end > 0) {
			return name.substring(end+1);
		} else
			return name;
	}
}

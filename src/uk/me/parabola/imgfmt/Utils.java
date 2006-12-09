/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Calendar;

/**
 * @author Steve Ratcliffe
 */
public class Utils {
	/**
	 * Routine to convert a string to bytes and pad with a character up
	 * to a given length.
	 * TODO: character set issues.
	 *
	 * @param s The original string.
	 * @param len The length to pad to.
	 * @param pad The byte used to pad.
	 * @return An array created from the string.
	 */
	public static byte[] toBytes(String s, int len, byte pad) {
		if (s == null)
			throw new IllegalArgumentException("null string provided");
		
		byte[] out = new byte[len];
		for (int i = 0; i < len; i++) {
			if (i > s.length()) {
				out[i] = pad;
			} else {
				out[i] = (byte) s.charAt(i);
			}
		}
		return out;
	}

	public static byte[] toBytes(String s) {
		return toBytes(s, s.length(), (byte) 0);
	}

	/**
	 * Set the creation date.  Note that the year is encoded specially.
	 * 
	 * @param buf The buffer to write into.  It must have been properly positioned
	 * beforehand.
	 * @param date The date to set.
	 */
	public static void setCreationTime(ByteBuffer buf, Date date) {
		Calendar cal = Calendar.getInstance();

		Date d = date;
		if (d == null)
			d = new Date();
		cal.setTime(d);

		buf.putChar((char) cal.get(Calendar.YEAR));
		buf.put((byte) (cal.get(Calendar.MONTH)));
		buf.put((byte) cal.get(Calendar.DAY_OF_MONTH));
		buf.put((byte) cal.get(Calendar.HOUR));
		buf.put((byte) cal.get(Calendar.MINUTE));
		buf.put((byte) cal.get(Calendar.SECOND));
	}

	public static int toMapUnit(double l) {
		return (int) (l * (1 << 24)/360);
	}

	public static byte[] makeCreationTime(Date date) {
		Calendar cal = Calendar.getInstance();

		Date d = date;
		if (d == null)
			d = new Date();
		cal.setTime(d);

		byte[] ret = new byte[7];
		ByteBuffer buf = ByteBuffer.wrap(ret);
		buf.putChar((char) cal.get(Calendar.YEAR));
		buf.put((byte) (cal.get(Calendar.MONTH)));
		buf.put((byte) cal.get(Calendar.DAY_OF_MONTH));
		buf.put((byte) cal.get(Calendar.HOUR));
		buf.put((byte) cal.get(Calendar.MINUTE));
		buf.put((byte) cal.get(Calendar.SECOND));
		
		return ret;
	}
}

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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.GZIPInputStream;

/**
 * Some miscellaneous functions that are used within the .img code.
 *
 * @author Steve Ratcliffe
 */
public class Utils {
	/**
	 * Routine to convert a string to bytes and pad with a character up
	 * to a given length.
	 * Only to be used for strings that are expressible in latin1.
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
	 * Convert from bytes to a string.  Only to be used when the character set
	 * is ascii or latin1.
	 *
	 * @param buf A byte buffer to get the bytes from.  Should be ascii or latin1.
	 * @param off The offset into buf.
	 * @param len The length to get.
	 * @return A string.
	 */
	public static String bytesToString(ByteBuffer buf, int off, int len) {
		if (buf == null)
			throw new IllegalArgumentException("null byte buffer provided");

		byte[] bbuf = new byte[len];
		buf.position(off);
		buf.get(bbuf);
		char[] cbuf = new char[len];
		for (int i = 0; i < bbuf.length; i++) {
			cbuf[i] = (char) bbuf[i];
		}

		return new String(cbuf);
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

		if (date != null)
			cal.setTime(date);

		fillBufFromTime(buf, cal);
	}

	/**
	 * A map unit is an integer value that is 1/(2^24) degrees of latitude or
	 * longitude.
	 *
	 * @param l The lat or long as decimal degrees.
	 * @return An integer value in map units.
	 */
	public static int toMapUnit(double l) {
		double DELTA = 360.0D / (1 << 24) / 2; //Correct rounding
		if (l > 0)
			return (int) ((l + DELTA) * (1 << 24)/360);
		else
			return (int) ((l - DELTA) * (1 << 24)/360);
	}

	/**
	 * Convert a date into the in-file representation of a date.
	 *
	 * @param date The date.
	 * @return A byte stream in .img format.
	 */
	public static byte[] makeCreationTime(Date date) {
		Calendar cal = Calendar.getInstance();

		if (date != null)
			cal.setTime(date);

		byte[] ret = new byte[7];

		ByteBuffer buf = ByteBuffer.wrap(ret);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		fillBufFromTime(buf, cal);
		
		return ret;
	}

	private static void fillBufFromTime(ByteBuffer buf, Calendar cal) {
		buf.putChar((char) cal.get(Calendar.YEAR));
		buf.put((byte) (cal.get(Calendar.MONTH)+1));
		buf.put((byte) cal.get(Calendar.DAY_OF_MONTH));
		buf.put((byte) cal.get(Calendar.HOUR_OF_DAY));
		buf.put((byte) cal.get(Calendar.MINUTE));
		buf.put((byte) cal.get(Calendar.SECOND));
	}

	/**
	 * Make a date from the garmin representation.
	 * @param date The bytes representing the date.
	 * @return A java date.
	 */
	public static Date makeCreationTime(byte[] date) {
		Calendar cal = Calendar.getInstance();

		int y = ((date[1] & 0xff) << 8) + (date[0] & 0xff);
		cal.set(y, date[2]-1, date[3], date[4], date[5], date[6]);

		return cal.getTime();
	}

	/**
	 * Convert an angle in map units to degrees.
	 */
	public static double toDegrees(int val) {
		return (double) val * (360.0 / (1 << 24));
	}

	/**
	 * Convert an angle in map units to radians.
	 */
	public static double toRadians(int mapunits) {
		return toDegrees(mapunits) * (Math.PI / 180);
	}

	public static void closeFile(Closeable f) {
		if (f != null) {
			try {
				f.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Open a file and apply filters necessary for reading it such as
	 * decompression.
	 *
	 * @param name The file to open.
	 * @return A stream that will read the file, positioned at the beginning.
	 * @throws FileNotFoundException If the file cannot be opened for any reason.
	 */
	public static InputStream openFile(String name) throws FileNotFoundException {
		InputStream is = new FileInputStream(name);
		if (name.endsWith(".gz")) {
			try {
				is = new GZIPInputStream(is);
			} catch (IOException e) {
				throw new FileNotFoundException( "Could not read as compressed file");
			}
		}
		return is;
	}

	public static String joinPath(String dir, String basename, String ext) {
		return joinPath(dir, basename + "." + ext);
	}
	public static String joinPath(String dir, String basename) {
		File file = new File(dir, basename);
		return file.getAbsolutePath();
	}

	/**
	 * Rounds an integer up to the nearest multiple of {@code 2^shift}.
	 * Works with both positive and negative integers.
	 * @param val the integer to round up.
	 * @param shift the power of two to round up to.
	 * @return the rounded integer.
	 */
	public static int roundUp(int val, int shift) {
		return (val + (1 << shift) - 1) >>> shift << shift;
	} 
	
	
}


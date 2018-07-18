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

import java.awt.geom.Line2D;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.imgfmt.app.Coord;
/**
 * Some miscellaneous functions that are used within the .img code.
 *
 * @author Steve Ratcliffe
 */
public class Utils {
	public static final int MIN_LAT_MAP_UNITS = toMapUnit(-90);
	public static final int MAX_LAT_MAP_UNITS = toMapUnit(90);
	public static final int MIN_LON_MAP_UNITS = toMapUnit(-180);
	public static final int MAX_LON_MAP_UNITS = toMapUnit(180); 

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
	
	/**
	 * Calculates the angle between the two segments (c1,c2),(c2,c3).
	 * It is assumed that the segments are rhumb lines, not great circle paths.
	 * @param c1 first point
	 * @param c2 second point
	 * @param c3 third point
	 * @return angle between the two segments in degree [-180;180]
	 */
	public static double getAngle(Coord c1, Coord c2, Coord c3) {
		double a = c2.bearingTo(c1);
		double b = c2.bearingTo(c3);
		double angle = b - (a - 180);
		while(angle > 180)
			angle -= 360;
		while(angle < -180)
			angle += 360;
		
		return angle;
	}
	
	/**
	 * Calculates the angle between the two segments (c1,c2),(c2,c3)
	 * using the coords in map units.
	 * @param c1 first point
	 * @param c2 second point
	 * @param c3 third point
	 * @return angle between the two segments in degree [-180;180]
	 */
	public static double getDisplayedAngle(Coord c1, Coord c2, Coord c3) {
		return getAngle(c1.getDisplayedCoord(), c2.getDisplayedCoord(), c3.getDisplayedCoord());
	}

	public final static int NOT_STRAIGHT = 0;
	public final static int STRAIGHT_SPIKE = 1;
	public final static int STRICTLY_STRAIGHT = 2;
	/**
	 * Checks if the two segments (c1,c2),(c2,c3) form a straight line.
	 * @param c1 first point
	 * @param c2 second point
	 * @param c3 third point
	 * @return NOT_STRAIGHT, STRAIGHT_SPIKE or STRICTLY_STRAIGHT 
	 */
	public static int isStraight(Coord c1, Coord c2, Coord c3) {
		if (c1.equals(c3))
			return STRAIGHT_SPIKE;
		long area;
		// calculate the area that is enclosed by the three points
		// (as if a closing line is drawn from c3 back to c1)
		area = ((long)c1.getLongitude() * c2.getLatitude() - 
				(long)c2.getLongitude() * c1.getLatitude());
		area += ((long)c2.getLongitude() * c3.getLatitude() - 
				(long)c3.getLongitude() * c2.getLatitude());
		area += ((long)c3.getLongitude() * c1.getLatitude() - 
				(long)c1.getLongitude() * c3.getLatitude());
		if (area == 0){
			// area is empty-> points lie on a straight line
			int delta1 = c1.getLatitude() - c2.getLatitude();
			int delta2 = c2.getLatitude() - c3.getLatitude();
			if (delta1 < 0 && delta2 > 0 || delta1 > 0 && delta2 < 0)
				return STRAIGHT_SPIKE;
			delta1 = c1.getLongitude() - c2.getLongitude();
			delta2 = c2.getLongitude() - c3.getLongitude();
			if (delta1 < 0 && delta2 > 0 || delta1 > 0 && delta2 < 0)
				return STRAIGHT_SPIKE;
			return STRICTLY_STRAIGHT;
		}
		// line is not straight
		return NOT_STRAIGHT;
		
	}

	/**
	 * Checks if the two segments (c1,c2),(c2,c3) form a straight line
	 * using high precision coordinates.
	 * @param c1 first point
	 * @param c2 second point
	 * @param c3 third point
	 * @return NOT_STRAIGHT, STRAIGHT_SPIKE or STRICTLY_STRAIGHT 
	 */
	public static int isHighPrecStraight(Coord c1, Coord c2, Coord c3) {
		if (c1.highPrecEquals(c3))
			return STRAIGHT_SPIKE;
		long area;
		long c1Lat = c1.getHighPrecLat();
		long c2Lat = c2.getHighPrecLat();
		long c3Lat = c3.getHighPrecLat();
		long c1Lon = c1.getHighPrecLon();
		long c2Lon = c2.getHighPrecLon();
		long c3Lon = c3.getHighPrecLon();
		// calculate the area that is enclosed by the three points
		// (as if a closing line is drawn from c3 back to c1)
		area = c1Lon * c2Lat - c2Lon * c1Lat;
		area += c2Lon * c3Lat - c3Lon * c2Lat;
		area += c3Lon * c1Lat - c1Lon * c3Lat;
		if (area == 0){
			// area is empty-> points lie on a straight line
			long delta1 = c1Lat - c2Lat;
			long delta2 = c2Lat - c3Lat;
			if (delta1 < 0 && delta2 > 0 || delta1 > 0 && delta2 < 0)
				return STRAIGHT_SPIKE;
			delta1 = c1Lon - c2Lon;
			delta2 = c2Lon - c3Lon;
			if (delta1 < 0 && delta2 > 0 || delta1 > 0 && delta2 < 0)
				return STRAIGHT_SPIKE;
			return STRICTLY_STRAIGHT;
		}
		// line is not straight
		return NOT_STRAIGHT;
		
	}

	/**
	 * approximate atan2, much faster than Math.atan2()
	 * Based on a 50-year old arctan approximation due to Hastings
	 */
	private final static double PI_BY_2 = Math.PI / 2;
	// |error| < 0.005
	public static double atan2_approximation( double y, double x )
	{
		if ( x == 0.0f )
		{
			if ( y > 0.0f ) return PI_BY_2 ;
			if ( y == 0.0f ) return 0.0f;
			return -PI_BY_2 ;
		}
		double atan;
		double z = y/x;
		if ( Math.abs( z ) < 1.0f )
		{
			atan = z/(1.0f + 0.28f*z*z);
			if ( x < 0.0f )
			{
				if ( y < 0.0f ) return atan - Math.PI;
				return atan + Math.PI;
			}
		}
		else
		{
			atan = PI_BY_2  - z/(z*z + 0.28f);
			if ( y < 0.0f ) return atan - Math.PI;
		}
		return atan;
	}
	
	/**
	 * calculate a long value for the latitude and longitude of a coord
	 * in high precision. 
	 * @param co
	 * @return a long that can be used as a key in HashMaps 
	 */
	public static long coord2Long(Coord co){
		int latHp = co.getHighPrecLat();
		int lonHp = co.getHighPrecLon();
		
		return (long)(latHp & 0xffffffffL) << 32 | (lonHp & 0xffffffffL);
	}
	
	/**
	 * TODO: This code may return true when the segments don't intersect, probably some extra checks are done in the calling code 
	 * Check if the line p1_1 to p1_2 cuts line p2_1 to p2_2 in two pieces and vice versa.
	 * This is a form of intersection check where it is allowed that one line ends on the
	 * other line or that the two lines overlap.
	 * @param p1_1 first point of line 1
	 * @param p1_2 second point of line 1
	 * @param p2_1 first point of line 2
	 * @param p2_2 second point of line 2
	 * @return true if both lines intersect somewhere in the middle of each other
	 */
	public static boolean linesCutEachOther(Coord p1_1, Coord p1_2, Coord p2_1, Coord p2_2) {
		int width1 = p1_2.getHighPrecLon() - p1_1.getHighPrecLon();
		int width2 = p2_2.getHighPrecLon() - p2_1.getHighPrecLon();

		int height1 = p1_2.getHighPrecLat() - p1_1.getHighPrecLat();
		int height2 = p2_2.getHighPrecLat() - p2_1.getHighPrecLat();

		int denominator = ((height2 * width1) - (width2 * height1));
		if (denominator == 0) {
			// the lines are parallel
			// they might overlap but this is ok for this test
			return false;
		}
		
		int x1Mx3 = p1_1.getHighPrecLon() - p2_1.getHighPrecLon();
		int y1My3 = p1_1.getHighPrecLat() - p2_1.getHighPrecLat();

		double isx = (double)((width2 * y1My3) - (height2 * x1Mx3))
				/ denominator;
		if (isx <= 0 || isx >= 1) {
			return false;
		}
		
		double isy = (double)((width1 * y1My3) - (height1 * x1Mx3))
				/ denominator;

		if (isy <= 0 || isy >= 1) {
			return false;
		} 

		return true;
	}

	public static int numberToPointerSize(int n) {
	// moved from imgfmt/app/mdr/MdrSection.java and app/typ/TYPFile.java
		if (n <= 0xff)
			return 1;
		else if (n <= 0xffff)
			return 2;
		else if (n <= 0xffffff)
			return 3;
		else
			return 4;
	}

	public static void put3sLongitude(ImgFileWriter writer, int longitude) {
		// handle special case, write -180/-8388608 instead of 180/8388608 to avoid assertion
		if (longitude == Utils.MAX_LON_MAP_UNITS)
			writer.put3s(Utils.MIN_LON_MAP_UNITS);
		else
			writer.put3s(longitude);
	}

    /**
     */
	/**
	 * Code taken from JOSM class Geometry. TODO: Licence ?
     * Finds the intersection of two line segments.
     * @param p1_1 the coordinates of the start point of the first specified line segment
     * @param p1_2 the coordinates of the end point of the first specified line segment
     * @param p2_1 the coordinates of the start point of the second specified line segment
     * @param p2_2 the coordinates of the end point of the second specified line segment
     * @return null if no intersection was found, a new Coord instance with the coordinates of the intersection otherwise
	 */
	public static Coord getSegmentSegmentIntersection(Coord p1_1, Coord p1_2, Coord p2_1, Coord p2_2) {
		
		double x1 = p1_1.getHighPrecLon();
		double y1 = p1_1.getHighPrecLat();
		double x2 = p1_2.getHighPrecLon();
		double y2 = p1_2.getHighPrecLat();
		double x3 = p2_1.getHighPrecLon();
		double y3 = p2_1.getHighPrecLat();
		double x4 = p2_2.getHighPrecLon();
		double y4 = p2_2.getHighPrecLat();

		// TODO: do this locally.
		// TODO: remove this check after careful testing
		if (!Line2D.linesIntersect(x1, y1, x2, y2, x3, y3, x4, y4))
			return null;

		// solve line-line intersection in parametric form:
		// (x1,y1) + (x2-x1,y2-y1)* u = (x3,y3) + (x4-x3,y4-y3)* v
		// (x2-x1,y2-y1)*u - (x4-x3,y4-y3)*v = (x3-x1,y3-y1)
		// if 0<= u,v <=1, intersection exists at ( x1+ (x2-x1)*u, y1 +
		// (y2-y1)*u )

		double a1 = x2 - x1;
		double b1 = x3 - x4;
		double c1 = x3 - x1;

		double a2 = y2 - y1;
		double b2 = y3 - y4;
		double c2 = y3 - y1;

		// Solve the equations
		double det = a1 * b2 - a2 * b1;

		double uu = b2 * c1 - b1 * c2;
		double vv = a1 * c2 - a2 * c1;
		double mag = Math.abs(uu) + Math.abs(vv);

		if (Math.abs(det) > 1e-12 * mag) {
			double u = uu / det, v = vv / det;
			if (u > -1e-8 && u < 1 + 1e-8 && v > -1e-8 && v < 1 + 1e-8) {
				if (u < 0)
					u = 0;
				if (u > 1)
					u = 1.0;
				return Coord.makeHighPrecCoord((int)Math.round(y1 + a2 * u), (int)Math.round(x1 + a1 * u));
			} else {
				return null;
			}
		} else {
			// parallel lines
			return null;
		}
	}
}

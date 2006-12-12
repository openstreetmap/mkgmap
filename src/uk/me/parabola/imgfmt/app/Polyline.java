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
 * Create date: 11-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.ArrayList;
import java.util.BitSet;

import uk.me.parabola.imgfmt.Utils;

/**
 * Represents a multi-segment line.  Eg for a road. As with all map objects
 * it can only exist as part of a subdivision.
 *
 * Writing these out is particularly tricky as deltas between points are packed
 * into the smallest number of bits possible.
 *
 * I am not trying to make the smallest map, so it may not be totally optimum.
 * 
 * @author Steve Ratcliffe
 */
public class Polyline extends MapObject {
	static private Logger log = Logger.getLogger(Polyline.class);

	// Set if it is a one-way street for example.
	private boolean direction;

	// The actual points that make up the line.
	private List<Coord> points = new ArrayList<Coord>();

	public Polyline(Subdivision div, int type) {
		setSubdiv(div);
		setType(type);
	}

	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFile file) {
		// If there is nothing to do, then do nothing.
		if (points.size() < 2)
			return;

		// Prepare for writing by doing all the required calculations.
		Work w = prepare();
		byte[] bs = w.getBitStream();

		// The type of feature, also contains a couple of flags hidden inside.
		byte b1 = (byte) getType();
		if (direction)
			b1 |= 0x40;  // Polylines only.
		if (bs.length > 255)
			b1 |= 0x80;

		file.put(b1);

		// The label, contains a couple of flags within it.
		int loff = getLabel().getOffset();
		if (w.isExtraBit())
			loff |= 0x400000;
		file.put3(loff);

		// The delta of the longitude from the subdivision centre point
		// note that this has already been calculated.
		file.putChar((char) getLongitude());
		file.putChar((char) getLatitude());

		if (bs.length > 255) {
			file.putChar((char) (bs.length & 0xffff));
		} else {
			file.put((byte) (bs.length & 0xff));
		}

		file.put(bs);
	}

	public void addCoord(Coord co) {
		points.add(co);
	}

	private Work prepare() {

		// Prepare the information that we need.
		Work w = new Work();
		return w;
	}


	public void setDirection(boolean direction) {
		this.direction = direction;
	}

	public class Work {
		private boolean extraBit;
		private boolean dataInNet;

		private boolean xSameSign;
		private boolean xSign;     // Set if all negative

		private boolean ySameSign;
		private boolean ySign;     // Set if all negative

		// The base number of bits
		private int xBase;
		private int yBase;

		private int[] deltas;

		public Work() {
			calcLatLong();
			calcDeltas();
		}

		public byte[] getBitStream() {
			BitSet bs = new BitSet();

			int ind = 0;

			bs.set(ind++, xSameSign);
			if (xSameSign)
				bs.set(ind++, xSign);

			bs.set(ind++, ySameSign);
			if (ySameSign)
				bs.set(ind++, ySign);

			int xbits = 2;
			if (xBase < 10)
				xbits += xBase;
			else
				xbits += (2 * xBase) -9;
			if (extraBit)
				xbits++;

			int ybits = 2;
			if (yBase < 10)
				ybits += yBase;
			else
				ybits += (2 * yBase) -9;
			if (extraBit)
				ybits++;

			// Note no sign included.
			log.debug("xbits" + xbits + ", y=" + ybits);


			return new byte[0];
		}

		/**
		 * Calculate the correct lat and long points.  They must be shifted if
		 * required by the zoom level.  The point that is taken to be the
		 * location is just the first point in the line.
		 */
		private void calcLatLong() {
			Subdivision div = getSubdiv();

			Coord co = points.get(0);

			int shift = div.getShift();
			log.debug("shift is " + shift);

			int lat = (co.getLatitude() - div.getLatitude()) >> shift;
			int lng = (co.getLongitude() - div.getLongitude()) >> shift;
			log.debug("lat/long " + Utils.toDegrees(lat) + "/" + Utils.toDegrees(lng));
			log.debug("lat/long " + lat + "/" + lng);

			setLatitude(lat);
			setLongitude(lng);
		}

		/**
		 * Calculate the deltas of one point to the other.  While we are doing
		 * this we must save more information about the maximum sizes, if they
		 * are all the same sign etc.  This must be done separately for both
		 * the lat and long values.
		 */
		private void calcDeltas() {
			int shift = getSubdiv().getShift();

			int lastLat = 0;
			int lastLong = 0;

			boolean xDiffSign = false; // The long values have different sign
			boolean yDiffSign = false; // The lat values have different sign

			int xSign = 0;  // If all the same sign, then this 1 or -1 depending
			                // on +ve or -ve
			int ySign = 0;  // As above for lat.

			int xBits = 0;  // Number of bits needed for long
			int yBits = 0;  // Number of bits needed for lat.

			// Space to hold the deltas
			int[] deltas = new int[2 * points.size()];
			int   off = 0;

			boolean first = true;

			// OK go through the points
			for (Coord co : points) {
				int lat = co.getLatitude() >> shift;
				int lng = co.getLongitude() >> shift;
				if (first) {
					lastLat = lat;
					lastLong = lng;
					first = false;
					continue;
				}

				int dx = lng - lastLong;
				int dy = lat - lastLat;

				// See if they can all be the same sign.
				if (!xDiffSign) {
					int thisSign = (dx >= 0)? 1: -1;
					if (xSign == 0) {
						xSign = thisSign;
					} else if (thisSign != xSign) {
						// The signs are different
						xDiffSign = true;
					}
				}
				if (!yDiffSign) {
					int thisSign = (dy >= 0)? 1: -1;
					if (ySign == 0) {
						ySign = thisSign;
					} else if (thisSign != ySign) {
						// The signs are different
						yDiffSign = true;
					}
				}

				// Find the maximum number of bits required to hold the value.
				int nbits = bitsNeeded(dx);
				if (nbits > xBits)
					xBits = nbits;

				nbits = bitsNeeded(dy);
				if (nbits > yBits)
					yBits = nbits;

				// Save the deltas
				deltas[off] = dx;
				deltas[off + 1] = dy;
				off += 2;
			}

			// Now we need to know the 'base' number of bits used to represent
			// the value.  In decoding you start with that number and add various
			// adjustments to get the final value.  We need to try and work
			// backwards from this.
			//
			// I don't care about getting the smallest possible file size so
			// err on the side of caution.
			//
			// Note that the sign bit is already not included so there is
			// no adjustment needed for it.
			
			this.extraBit = false;  // Keep simple for now

			int tmp = xBits - 2;
			if (tmp > 10)
				tmp = 9 + (tmp - 9) / 2;
			this.xBase = tmp;

			tmp = yBits - 2;
			if (tmp > 10)
				tmp = 9 + (tmp - 9) / 2;
			this.yBase = tmp;

			// Set flags for same sign etc.
			this.xSameSign = !xDiffSign;
			this.ySameSign = !yDiffSign;
			this.xSign = xSign > 0;
			this.ySign = ySign > 0;

			// Save the deltas
			this.deltas = deltas;
		}

		/**
		 * The bits needed to represent a number.
		 *
		 * @param val The number for bit couting.
		 * @return The number of bits required.
		 */
		private int bitsNeeded(int val) {
			if (val == 0)
				return 0;
			if (val < 0) {
				int n = -val;
				return Integer.bitCount(n) + 1;
			} else {
				return Integer.bitCount(val);
			}
		}


		public boolean isExtraBit() {
			return extraBit;
		}

		public boolean isDataInNet() {
			return dataInNet;
		}

		public boolean isXSameSign() {
			return xSameSign;
		}

		public boolean isXSign() {
			return xSign;
		}

		public boolean isYSameSign() {
			return ySameSign;
		}

		public boolean isYSign() {
			return ySign;
		}

		public int getXBase() {
			return xBase;
		}

		public int getYBase() {
			return yBase;
		}
	}

}

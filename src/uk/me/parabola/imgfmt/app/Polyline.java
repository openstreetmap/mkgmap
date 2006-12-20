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
	private static final Logger log = Logger.getLogger(Polyline.class);

	// Set if it is a one-way street for example.
	private boolean direction;

	// The actual points that make up the line.
	private final List<Coord> points = new ArrayList<Coord>();

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
		BitWriter bw = w.makeBitStream();

		// The type of feature, also contains a couple of flags hidden inside.
		byte b1 = (byte) getType();
		if (direction)
			b1 |= 0x40;  // Polylines only.
		
		int blen = bw.getLength() - 1; // allow for the sizes
		if (blen > 255)
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

		if (blen > 255) {
			file.putChar((char) (blen & 0xffff));
		} else {
			file.put((byte) (blen & 0xff));
		}

		file.put(bw.getBytes(), 0, blen+1);
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

	private class Work {
		private boolean extraBit;

		private boolean xSameSign;
		private boolean xSignNegative;     // Set if all negative

		private boolean ySameSign;
		private boolean ySignNegative;     // Set if all negative

		// The base number of bits
		private int xBase;
		private int yBase;

		private int xNum;    // Number of bits for the x coord
		private int yNum;    // Number of bits for the y coord

		private int[] deltas;

		Work() {
			calcLatLong();
			calcDeltas();
		}

		public BitWriter makeBitStream() {

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
			log.debug("xNum" + xNum + ", y=" + yNum);

			// Write the bitstream
			BitWriter bw = new BitWriter();

			// Pre bit stream info
			bw.putn(xBase, 4);
			bw.putn(yBase, 4);

			bw.put1(xSameSign);
			if (xSameSign)
				bw.put1(xSignNegative);

			bw.put1(ySameSign);
			if (ySameSign)
				bw.put1(ySignNegative);

			log.debug("x same is " + xSameSign + ", sign is " + xSignNegative);
			log.debug("y same is " + ySameSign + ", sign is " + ySignNegative);

			int dx, dy;
			for (int i = 0; i < deltas.length; i+=2) {
				dx = deltas[i];
				log.debug("x delta " + dx + ", " + xNum);
				if (xSameSign) {
					bw.putn(abs(dx), xNum);
				} else {
					assert dx == 0 || ((dx & ((1 << xNum) - 1)) != 0);
					bw.putn(dx, xNum);
					bw.put1(dx < 0);
				}

				dy = deltas[i + 1];
				log.debug("y delta " + dy + ", " + yNum);
				if (ySameSign) {
					bw.putn(abs(dy), yNum);
				} else {
					assert dy == 0 || ((dy & ((1<<yNum) - 1)) != 0);
					bw.putn(dy, yNum);
					bw.put1(dy < 0);
				}
			}

			log.debug(bw);
			return bw;
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
			int lon = (co.getLongitude() - div.getLongitude()) >> shift;
			log.debug("lat/long " + Utils.toDegrees(lat) + '/' + Utils.toDegrees(lon));
			log.debug("lat/long " + lat + '/' + lon);

			setLatitude(lat);
			setLongitude(lon);
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
			deltas = new int[2 * (points.size() - 1)];
			int   off = 0;

			boolean first = true;

			// OK go through the points
			for (Coord co : points) {
				int lat = co.getLatitude() >> shift;
				int lon = co.getLongitude() >> shift;
				if (first) {
					lastLat = lat;
					lastLong = lon;
					first = false;
					continue;
				}

				int dx = lon - lastLong;
				int dy = lat - lastLat;

				lastLong = lon;
				lastLat = lat;
				
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
			this.xNum = xBits;

			tmp = yBits - 2;
			if (tmp > 10)
				tmp = 9 + (tmp - 9) / 2;
			this.yBase = tmp;
			this.yNum = yBits;

			// Set flags for same sign etc.
			this.xSameSign = !xDiffSign;
			this.ySameSign = !yDiffSign;
			this.xSignNegative = xSign < 0;
			this.ySignNegative = ySign < 0;

			// Save the deltas
			this.deltas = deltas;
		}

		/**
		 * The bits needed to hold a number without truncating it.
		 *
		 * @param val The number for bit couting.
		 * @return The number of bits required.
		 */
		private int bitsNeeded(int val) {
			int n = abs(val);

			int count = val < 0? 1: 0;
			while (n != 0) {
				n >>>= 1;
				count++;
			}
			return count;
		}

		private int abs(int val) {
			if (val < 0)
				return -val;
			else
				return val;
		}

		public boolean isExtraBit() {
			return extraBit;
		}

	}

}

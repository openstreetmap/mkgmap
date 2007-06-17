/**
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
 * Author: steve
 * Date: 24-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import java.util.List;

import uk.me.parabola.log.Logger;

/**
 * This class holds all of the calculations needed to encode a line into
 * the garmin format.
 */
class LinePreparer {
	private static final Logger log = Logger.getLogger(LinePreparer.class);

	// These are our inputs.
	private final Polyline polyline;

	private boolean extraBit;
	private boolean xSameSign;
	private boolean xSignNegative;     // Set if all negative

	private boolean ySameSign;
	private boolean ySignNegative;     // Set if all negative

	// The base number of bits
	private int xBase;
	private int yBase;

	// The delta changes between the points.
	private int[] deltas;

	LinePreparer(Polyline line) {
		polyline = line;
		calcLatLong();
		calcDeltas();
	}

	/**
	 * Write the bit stream to a BitWriter and return it.
	 *
	 * @return A class containing the writen byte stream.
	 */
	public BitWriter makeBitStream() {

		assert xBase >= 0 && yBase >= 0;

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
		if (log.isDebugEnabled()) {
//			log.debug("xNum" + xNum + ", y=" + yNum);
			log.debug("xbits" + xbits + ", y=" + ybits);
		}

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

		if (log.isDebugEnabled()) {
			log.debug("x same is " + xSameSign + ", sign is " + xSignNegative);
			log.debug("y same is " + ySameSign + ", sign is " + ySignNegative);
		}

		int dx, dy;
		for (int i = 0; i < deltas.length; i+=2) {
			dx = deltas[i];
			dy = deltas[i + 1];
			if (dx == 0 && dy == 0)
				continue;
			
			if (log.isDebugEnabled())
				log.debug("x delta " + dx + ", " + xbits);
			if (xSameSign) {
				bw.putn(abs(dx), xbits);
			} else {
				assert dx == 0 || ((dx & ((1 << xbits) - 1)) != 0);
				bw.putn(dx, xbits);
				bw.put1(dx < 0);
			}

			if (log.isDebugEnabled())
				log.debug("y delta " + dy + ", " + ybits);
			if (ySameSign) {
				bw.putn(abs(dy), ybits);
			} else {
				assert dy == 0 || ((dy & ((1<<ybits) - 1)) != 0);
				bw.putn(dy, ybits);
				bw.put1(dy < 0);
			}
		}

		if (log.isDebugEnabled())
			log.debug(bw);
		return bw;
	}

	/**
	 * Calculate the correct lat and long points.  They must be shifted if
	 * required by the zoom level.  The point that is taken to be the
	 * location is just the first point in the line.
	 */
	private void calcLatLong() {
		Coord co = polyline.getPoints().get(0);

		polyline.setLatitude(co.getLatitude());
		polyline.setLongitude(co.getLongitude());
	}

	/**
	 * Calculate the deltas of one point to the other.  While we are doing
	 * this we must save more information about the maximum sizes, if they
	 * are all the same sign etc.  This must be done separately for both
	 * the lat and long values.
	 */
	private void calcDeltas() {
		int shift = polyline.getSubdiv().getShift();
		List<Coord> points = polyline.getPoints();

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
			log.debug("shifted pos", lat, lon);
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

		if (xBits < 2)
			xBits = 2;
		int tmp = xBits - 2;
		if (tmp > 10)
			tmp = 9 + (tmp - 9) / 2;
		this.xBase = tmp;

		if (yBits < 2)
			yBits = 2;
		tmp = yBits - 2;
		if (tmp > 10)
			tmp = 9 + (tmp - 9) / 2;
		this.yBase = tmp;

		// Set flags for same sign etc.
		this.xSameSign = !xDiffSign;
		this.ySameSign = !yDiffSign;
		this.xSignNegative = xSign < 0;
		this.ySignNegative = ySign < 0;
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

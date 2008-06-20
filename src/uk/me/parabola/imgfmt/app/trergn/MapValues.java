/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 18-Jun-2008
 */
package uk.me.parabola.imgfmt.app.trergn;

/**
 * Class to calculate the values that occur at offset 9a in the TRE header.
 * As I don't know the purpose of these the naming is a bit arbitary here...
 *
 * This was worked out in the display project, so see the TreCalc file
 * there for more history.  This is a cleaned up version of what was
 * written there.
 *
 * @author Steve Ratcliffe
 * @see <a href="http://svn.parabola.me.uk/display/trunk/src/test/display/TreCalc.java">TreCalc.java</a>
 */
public class MapValues {
	private int mapId;
	private int length;

	private byte[][] values = new byte[4][8];

	// Converts the digits in the map id to the values seen in this section.
	private static byte[] mapIdCodeTable = new byte[] {
			0, 1, 0xf, 5,
			0xd, 4, 7, 6,
			0xb, 9, 0xe, 8,
			2, 0xa, 0xc, 3
	};

	// Used to work out the required offset that is applied to all the
	// digits of the values.
	private int[] offsetMap = new int[] {
			6, 7, 5, 11,
			3, 10, 13, 12,
			1, 15, 4, 14,
			8, 0, 2, 9
	};

	public MapValues(int mapId, int headerLength) {
		this.mapId = mapId;
		this.length = headerLength;
	}

	/**
	 * There are four values.  Get value n.
	 * @param n Get value n, starting at 0 up to four.
	 */
	public int value(int n) {
		byte[] out = values[n];

		int res = 0;
		for (int i = 0; i < 8; i++) {
			res |= ((out[i] & 0xf) << (4 * (7 - i)));
		}
		return res;
	}

	public void calculate() {
		// Done in this order because the first and second depend on things
		// we have already calculated in three.
		calcThird();
		calcFourth();
		calcFirst();
		calcSecond();

		addOffset();
	}

	/**
	 * Add an offset to all previously calculated values.
	 */
	private void addOffset() {
		// To get the offset value we add up all the even nibbles of the map
		// number and transform via a table.
		int n = mapIdDigit(1) + mapIdDigit(3) + mapIdDigit(5) + mapIdDigit(7);

		int offset = offsetMap[n & 0xf];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 8; j++) {
				values[i][j] += offset;
			}
		}
	}

	/**
	 * This value is made from the third value, combined with the raw
	 * map id values.
	 */
	private void calcFirst() {
		byte[] out = values[0];
		byte[] v3 = values[3];

		// First bytes are the low bytes of the mapId, with the corresponding
		// value from value[3] added.
		out[0] = (byte) (mapIdDigit(4) + v3[0]);
		out[1] = (byte) (mapIdDigit(5) + v3[1]);
		out[2] = (byte) (mapIdDigit(6) + v3[2]);
		out[3] = (byte) (mapIdDigit(7) + v3[3]);

		// Copies of v3
		out[4] = v3[4];
		out[5] = v3[5];
		out[6] = v3[6];

		// Always (?) one more.  The one likely comes from some other
		// part of the header, but we don't know if or where.
		out[7] = (byte) (v3[7] + 1);
	}

	/**
	 * This is made from various parts of the third value and the raw digits
	 * from the map id.  There are two digits where the header length digits
	 * are used (or that could be a coincidence, but it holds up well so far).
	 */
	private void calcSecond() {
		byte[] out = values[1];
		byte[] v3 = values[3];

		// Just same as in v3
		out[0] = v3[0];
		out[1] = v3[1];

		int h1 = length >> 4;
		int h2 = length;
		out[2] = (byte) ((v3[2] + h1) & 0xf);
		out[3] = (byte) ((v3[3] + h2) & 0xf);

		// The following are the sum of individual nibbles in U3 and the
		// corresponding nibble in the top half of mapId.
		out[4] = (byte) (v3[4] + mapIdDigit(0));
		out[5] = (byte) (v3[5] + mapIdDigit(1));
		out[6] = (byte) (v3[6] + mapIdDigit(2));
		out[7] = (byte) (v3[7] + mapIdDigit(3));
	}

	/**
	 * This is made of the hex digits of the map id in a given order
	 * translated according to a given table of values.
	 */
	private void calcThird() {
		byte[] out = values[2];
		for (int i = 0; i < 8; i++) {
			int n = mapIdDigit(i);
			out[(i ^ 1)] = mapIdCodeTable[n];
		}
	}

	/**
	 * This is just a copy of the third value.
	 */
	private void calcFourth() {
		System.arraycopy(values[2], 0, values[3], 0, values[3].length);
	}

	/**
	 * Extract the given nibble of the map id.  0 is the highest four bits.
	 * @param i The nibble number, 0 most significant, 7 the least.
	 * @return The given nibble of the map id.
	 */
	private int mapIdDigit(int i) {
		return (mapId >>> (4 * (7 - i))) & 0xf;
	}
}

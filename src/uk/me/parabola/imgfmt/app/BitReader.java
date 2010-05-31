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
 * Create date: 29-Aug-2008
 */
package uk.me.parabola.imgfmt.app;

/**
 * @author Steve Ratcliffe
 */
public class BitReader {
	private final byte[] buf;
	private int bitPosition;

	public BitReader(byte[] buf) {
		this.buf = buf;
	}

	public boolean get1() {
		int off = bitPosition % 8;
		byte b = buf[bitPosition / 8];

		bitPosition++;
		return ((b >> off) & 1) == 1;
	}

	public int get(int n) {
		int res = 0;

		int pos = 0;
		while (pos < n) {
			int index = bitPosition / 8;
			int off = bitPosition % 8;

			byte b = buf[index];
			b >>= off;
			int nbits = n - pos;
			if (nbits > (8-off))
				nbits = 8 - off;

			int mask = ((1 << nbits) - 1);
			res |= ((b & mask) << pos);
			pos += nbits;
			bitPosition += nbits;
		}

		return res;
	}

	/**
	 * Get a signed n-bit value, treating 1 << (n-1) as a
	 * flag to read another signed n-bit value for extended
	 * range (mysteriously only in the negative direction).
	 *
	 * At least two levels of recursion show up in the wild;
	 * current code computes correctly in that example.
	 */
	public int sget2(int n) {
		int res = get(n);
		int top = 1 << (n - 1);
		if ((res & top) != 0) {
			int mask = top - 1;
			if ((res & mask) == 0) {
				int res2 = sget2(n);
				res = (~mask | res) + 1 + res2;
			} else {
				res = ~mask | res;
			}
		}
		return res;
	}

	public int getBitPosition() {
		return bitPosition;
	}
}

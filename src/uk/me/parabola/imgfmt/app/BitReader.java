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
	private byte[] buf;
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
	 * A signed get.  Treats the top bit in the given bit field as a
	 * sign bit.
	 */
	public int sget(int n) {
		int res = get(n);
		int top = 1 << (n - 1);
		if ((res & top) != 0) {
			int mask = top - 1;
			res = ~mask | res;
		}
		return res;
	}
	
	public int getBitPosition() {
		return bitPosition;
	}

	public static void main(String[] args) {
		BitReader br = new BitReader(new byte[] {
				(byte) 0xf1, 0x73, (byte) 0xc2, 0x5
		});

		System.out.printf("1bit %b\n", br.get1());
		System.out.printf("bits %x\n", br.get(5));
		System.out.printf("0xf %x\n", br.get(4));
		System.out.printf("0x %x\n", br.get(16));
	}
}

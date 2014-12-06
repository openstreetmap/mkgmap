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
 * Create date: 30-Nov-2008
 */
package uk.me.parabola.imgfmt.app;

import org.junit.Test;

import static org.junit.Assert.*;


public class BitReaderTest {

	/**
	 * Very simple test that the bit reader is working.
	 */
	@Test
	public void testGetBits() {
		// Add your code here
		BitReader br = new BitReader(new byte[]{
				(byte) 0xf1, 0x73, (byte) 0xc2, 0x5
		});

		assertTrue("first bit", br.get1());
		assertEquals("five bits", 0x18, br.get(5));
		assertEquals("four bits", 0xf, br.get(4));
		assertEquals("sixteen bits", 0x709c, br.get(16));
	}

	@Test
	public void testSpecialNegative() {
		BitReader br = new BitReader(new byte[]{0x24, 0xb});

		int s = br.sget2(3);
		assertEquals(-12, s);
	}

	@Test
	public void testSpecialPositive() {
		BitReader br = new BitReader(new byte[]{(byte) 0xa4, 0});

		int s = br.sget2(3);
		assertEquals(8, s);
	}
}

/*
 * Copyright (C) 2012.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.app.BitWriter;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 * @author Steve Ratcliffe
 */
public class VarBitWriterTest {

	private final BitWriter bw = new BitWriter();

	@Test
	public void testPositive() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);

		// should be able to write numbers up to 7
		vbw.write(7);

		byte b = bw.getBytes()[0];
		assertEquals(b, 7);
	}

	@Test
	public void testPositiveWithWidth() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.bitWidth = 1;

		// should be able to write numbers up to 15
		vbw.write(15);

		byte b = bw.getBytes()[0];
		assertEquals(b, 15);
	}

	@Test(expected = Abandon.class)
	public void testPositiveWithWidthFail() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.bitWidth = 1;

		// should be able to write numbers up to 15
		vbw.write(16);
		assertTrue(false);
	}

	@Test(expected=Abandon.class)
	public void testPositiveFail() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);

		// should be able to write numbers up to 7
		vbw.write(8);
		assertTrue(false);
	}

	@Test
	public void testNegative() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.negative = true;

		// write up to -7
		vbw.write(-7);
		byte b = bw.getBytes()[0];
		assertEquals(b, 7);
	}

	@Test(expected = Abandon.class)
	public void testNegativeWithPositive() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.negative = true;

		// positive numbers are invalid
		vbw.write(7);
		assertTrue(false);
	}

	@Test(expected = Abandon.class)
	public void testNegativeTooBig() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.negative = true;

		// number too large
		vbw.write(8);
		assertTrue(false);
	}

	@Test
	public void testSignedPositive() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.signed = true;

		// up to 7
		vbw.write(7);
		byte b = bw.getBytes()[0];
		assertEquals(b, 7);
	}

	@Test
	public void testSignedNegative() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.signed = true;

		// up to -8
		vbw.write(-8);
		byte b = bw.getBytes()[0];
		assertEquals(b, 0x8);
	}

	@Test(expected = Abandon.class)
	public void testSignedPositiveTooBig() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.signed = true;

		// up to 7, 8 too big
		vbw.write(8);
		assertTrue(false);
	}

	@Test(expected = Abandon.class)
	public void testSignedNegativeTooBig() {
		VarBitWriter vbw = new VarBitWriter(bw, 3);
		vbw.signed = true;

		// up to -8, -9 is too big
		vbw.write(-9);
		assertTrue(false);
	}
}

/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
/* Create date: 15-Feb-2009 */
package uk.me.parabola.imgfmt.app.net;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import uk.me.parabola.imgfmt.app.BitWriter;

import static org.junit.Assert.*;
import org.junit.Test;


public class RoadDefTest {

	/**
	 * Compares the result of the loop in writeNod2 with using a BitWriter.
	 *
	 * You have to copy the code to here for this test to mean anything.
	 */
	@Test
	public void testBitArray() {

		Random r = new Random();

		final int MAX_BITS = 26;
		boolean[] bits = new boolean[MAX_BITS];
		for (int i = 0; i < bits.length; i++)
			bits[i] = r.nextBoolean();

		ByteArrayOutputStream writer = new ByteArrayOutputStream();

		// This is the loop taken from the code
		for (int i = 0; i < bits.length; i += 8) {
			int b = 0;
            for (int j = 0; j < 8 && j < bits.length - i; j++)
				if (bits[i+j])
					b |= 1 << j;
			writer.write((byte) b);
		}
		// End of loop

		BitWriter bw = new BitWriter();
		for (boolean b : bits)
			bw.put1(b);

		byte[] loopResult = writer.toByteArray();

		// Get the bit writer result and trim the array to the correct size
		byte[] bwResult = new byte[bw.getLength()];
		System.arraycopy(bw.getBytes(), 0, bwResult, 0, bwResult.length);

		assertArrayEquals("Loop against bitwriter", bwResult, loopResult);
	}
}

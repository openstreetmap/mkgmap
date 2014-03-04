/*
 * Copyright (C) 2014.
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
package uk.me.parabola.imgfmt.app.labelenc;

import org.junit.Test;

import static org.junit.Assert.*;

public class LabelEncTest {

	private static final char[] EMPTY_CHARS = new char[0];
	private static final byte[] EMPTY_BYTES = new byte[0];

	@Test
	public void testHashForNull() {
		EncodedText enc = new EncodedText(null, 0, null);
		assertEquals(0, enc.hashCode());
	}

	@Test
	public void testHashForEmpty() {
		EncodedText enc = new EncodedText(EMPTY_BYTES, 0, EMPTY_CHARS);
		assertEquals(0, enc.hashCode());
	}

	@Test
	public void testEmptyEqualsNull() {
		EncodedText e1 = new EncodedText(null, 0, null);
		EncodedText e2 = new EncodedText(EMPTY_BYTES, 0, EMPTY_CHARS);

		assertEquals(e1, e2);
	}
}

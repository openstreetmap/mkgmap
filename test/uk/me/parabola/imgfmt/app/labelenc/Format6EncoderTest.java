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
import org.junit.Before;
import org.junit.After;
import org.junit.Assert;

import static org.junit.Assert.*;
import org.junit.Test;

public class Format6EncoderTest {
	/**
	 * Note that this is essentially a special case. We need a zero length input to map to the first
	 * empty entry in the table.
	 */
	@Test
	public void testEmptyGivesZeroResult() {
		Format6Encoder fmt = new Format6Encoder();

		EncodedText enc = fmt.encodeText("");
		assertEquals(0, enc.getLength());
	}

	@Test
	public void testEmptyGivesNullChars() {
		Format6Encoder fmt = new Format6Encoder();

		EncodedText enc = fmt.encodeText("");
		assertNull(enc.getChars());
	}

	@Test
	public void testEmptyGivesNullCtext() {
		Format6Encoder fmt = new Format6Encoder();

		EncodedText enc = fmt.encodeText("");
		assertNull(enc.getCtext());
	}
}

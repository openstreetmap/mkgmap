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
package uk.me.parabola.imgfmt.app.srt;

import java.text.Collator;

import uk.me.parabola.mkgmap.srt.SrtTextReader;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class UnicodeCollatorTest {

	private Collator collator;

	@Before
	public void setUp() throws Exception {
		Sort sort = SrtTextReader.sortForCodepage(65001);

		collator = sort.getCollator();
		collator.setStrength(Collator.TERTIARY);
	}

	@Test
	public void testSimpleLessThan() {
		assertEquals(-1, collator.compare("G", "Ò"));
		assertEquals(-1, collator.compare("G", "Γ"));
	}

	@Test
	public void testExpand() {
		assertEquals(-1, collator.compare("!", "ß"));
		assertEquals(-1, collator.compare("A:", "Ǣ"));
	}
}

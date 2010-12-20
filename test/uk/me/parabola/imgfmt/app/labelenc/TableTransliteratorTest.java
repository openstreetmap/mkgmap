/*
 * Copyright (C) 2009.
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

public class TableTransliteratorTest {

	/**
	 * Basic test to ascii.
	 */
	@Test
	public void testToAscii() {
		TableTransliterator tr = new TableTransliterator("ascii");

		String s = tr.transliterate("aéćsõ\u0446");
		assertEquals("to ascii", "aecsots", s);
	}

	/**
	 * Quick check that the latin characters on input survive in the output
	 * when latin1 is requested.
	 */
	@Test
	public void testToLatin() {
		TableTransliterator tr = new TableTransliterator("latin1");

		String s = tr.transliterate("aéćsõ\u0446");
		assertEquals("to latin", "aécsõts", s);
	}

	/**
	 * Characters in the latin table override those in the ascii table, when
	 * it is requested.
	 */
	@Test
	public void testLatinOverride() {
		TableTransliterator tr = new TableTransliterator("latin1");

		String s = tr.transliterate("\u0401");
		assertEquals("to latin with override", "Ë", s);
	}

	/**
	 * Not overridden by latin, when it is not requested.
	 */
	@Test
	public void testNotOverriden() {
		TableTransliterator tr = new TableTransliterator("ascii");

		String s = tr.transliterate("\u0401");
		assertEquals("to latin with override", "Yo", s);
	}
}

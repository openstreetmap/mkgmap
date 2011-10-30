/*
 * Copyright (C) 2011.
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

import java.util.Locale;

import uk.me.parabola.log.Logger;

/**
 * A sparse character-based transliterator that leaves most characters unchanged.
 *
 */
public class SparseTransliterator implements Transliterator {
	private static final Logger log = Logger.getLogger(SparseTransliterator.class);

	private final boolean useNoMacron;
	private boolean forceUppercase;

	public SparseTransliterator(String targetCharset) {
		useNoMacron = (targetCharset.equals("nomacron")) ? true : false;
	}

	/**
	 * Convert a string into a string that uses only acceptable characters.
	 *
	 * @param s The original string.  It can use any unicode character. Can be null in which case null will
	 * be returned.
	 * @return A string that uses only acceptable characters.
	 */
	public String transliterate(String s) {
		if (s == null)
			return null;

		StringBuilder sb = new StringBuilder(s.length() + 5);
		for (char c : s.toCharArray()) {
			if (useNoMacron) {
				// Only macrons are modified, all other chars (including non-ascii) are left unchanged
				if (c == 0x101) // Unicode Character 'LATIN SMALL LETTER A WITH MACRON' (U+0101)
					c = 'a';
				if (c == 0x113) // Unicode Character 'LATIN SMALL LETTER E WITH MACRON' (U+0113)
					c = 'e';
				if (c == 0x12b) // Unicode Character 'LATIN SMALL LETTER I WITH MACRON' (U+012B)
					c = 'i';
				if (c == 0x14d) // Unicode Character 'LATIN SMALL LETTER O WITH MACRON' (U+014D)
					c = 'o';
				if (c == 0x16b) // Unicode Character 'LATIN SMALL LETTER U WITH MACRON' (U+016B)
					c = 'u';
			}
			sb.append(c);			
		}

		String text = sb.toString();
		if (forceUppercase)
			text = text.toUpperCase(Locale.ENGLISH);
		return text;
	}

	public void forceUppercase(boolean uc) {
		forceUppercase = uc;
	}
}

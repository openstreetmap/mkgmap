/*
 * Copyright (C) 2015.
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
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.imgfmt.app.labelenc.TableTransliterator;
import uk.me.parabola.imgfmt.app.labelenc.Transliterator;
import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Transliterate the string to ascii or latin.
 */
public class TransliterateFilter extends ValueFilter {
	private static final Transliterator ASCII = new TableTransliterator("ascii");
	private static final Transliterator LATIN1 = new TableTransliterator("latin1");

	private final Transliterator trans;

	public TransliterateFilter(String charset) {
		if ("latin1".equals(charset))
			trans = LATIN1;
		else
			trans = ASCII;
	}

	protected String doFilter(String value, Element el) {
		return trans.transliterate(value);
	}
}

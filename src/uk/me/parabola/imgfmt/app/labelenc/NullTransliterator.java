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

/**
 * A transliterator that does not touch the string at all.
 *
 * @author Steve Ratcliffe
 */
public class NullTransliterator implements Transliterator {

	/**
	 * Return the string unchanged.
	 * @return The original string, not a copy.
	 */
	public String transliterate(String s) {
		return s;
	}

	/**
	 * Do not ever upper case.
	 * @param uc Ignored parameter.
	 */
	public void forceUppercase(boolean uc) {
	}
}

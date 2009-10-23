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

/**
 * @author Steve Ratcliffe
 */
public interface Transliterator {
	/**
	 * Convert a string into a string that uses only ascii characters.
	 *
	 * @param s The original string.  It can use any unicode character.
	 * @return A string that uses only ascii characters that is a transcription or
	 *         transliteration of the original string.
	 */
	String transliterate(String s);
}

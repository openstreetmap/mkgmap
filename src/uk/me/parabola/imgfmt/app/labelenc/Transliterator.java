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
 * Interface for transliterator functions.
 *
 * @author Steve Ratcliffe
 */
public interface Transliterator {
	/**
	 * Convert a string into a string that uses only ascii or latin1 characters.
	 *
	 * @param s The original string.  It can use any unicode character. Can be null in which
	 * case null will be returned.
	 * @return A string that uses a restricted subset of characters (ascii or
	 * latin) that is a transliterated form of the input string.
	 */
	public String transliterate(String s);

	/**
	 * Force the use of uppercase in this transliterator.
	 * Note that it is normal to set this.
	 */
	public void forceUppercase(boolean uc);
}

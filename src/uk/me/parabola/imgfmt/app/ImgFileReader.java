/*
 * Copyright (C) 2006 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 07-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import java.io.Closeable;

import uk.me.parabola.imgfmt.ReadFailedException;

/**
 * For reading subfiles from the img.  The focus of mkgmap is on writing,
 * but some limited reading is needed for several operations.
 *
 * @author Steve Ratcliffe
 */
public interface ImgFileReader extends Closeable {

	/**
	 * Get the position.  Needed because may not be reflected in the underlying
	 * file if being buffered.
	 *
	 * @return The logical position within the file.
	 */
	public long position();

	/**
	 * Set the position of the file.
	 * @param pos The new position in the file.
	 */
	void position(long pos);

	/**
	 * Read in a single byte.
	 * @return The byte that was read.
	 */
	public byte get() throws ReadFailedException;

	/**
	 * Read in two bytes.  Done in the correct byte order.
	 * @return The 2 byte integer that was read.
	 */
	public char getChar() throws ReadFailedException;

	/**
	 * Get a 3byte signed quantity.
	 *
	 * @return The value read.
	 * @throws ReadFailedException When the file cannot be read.
	 */
	public int get3() throws ReadFailedException;

	/**
	 * Get a 3byte unsigned quantity.
	 *
	 * @return The value read.
	 * @throws ReadFailedException When the file cannot be read.
	 */
	public int getu3() throws ReadFailedException;

	/**
	 * Read in a 4 byte value.
	 * @return A 4 byte integer.
	 */
	public int getInt() throws ReadFailedException;

	/**
	 * Read a variable sized integer.  The size is given.
	 * @param n The size of the integer to read. Must be 1 to 4.
	 * @return The integer which will not be sign extended if it is less
	 * than 4 bytes long.
	 */
	public int getUint(int n) throws ReadFailedException;

	/**
	 * Read in an arbitrary length sequence of bytes.
	 *
	 * @param len The number of bytes to read.
	 */
	public byte[] get(int len) throws ReadFailedException;

	/**
	 * Read a zero terminated string from the file.
	 * @return A string
	 * @throws ReadFailedException For failures.
	 */
	public byte[] getZString() throws ReadFailedException;

	/**
	 * Read in a string of digits in the compressed base 11 format that is used
	 * for phone numbers in the POI section.
	 * @param delimiter This will replace all digit 11 characters.  Usually a
	 * '-' to separate numbers in a telephone.  No doubt there is a different
	 * standard in each country.
	 * @return A phone number possibly containing the delimiter character.
	 */
	public String getBase11str(byte firstChar, char delimiter);
}

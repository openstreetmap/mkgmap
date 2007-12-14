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

import uk.me.parabola.imgfmt.ReadFailedException;

import java.io.IOException;

/**
 * Read strategy interface.  Since we have a write strategy, have this for
 * balance.  It does split up reading and writing quite nicely though.
 *
 * @author Steve Ratcliffe
 */
public interface ReadStrategy {

	/**
	 * Called when the stream is closed.  Any resources can be freed.
	 * @throws IOException When there is an error in closing.
	 */
	public void close() throws IOException;

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
	 * Read in a 4 byte value.
	 * @return A 4 byte integer.
	 */
	public int getInt() throws ReadFailedException;

	/**
	 * Read in an arbitary length sequence of bytes.
	 *
	 * @param len The number of bytes to read.
	 */
	public byte[] get(int len) throws ReadFailedException;
}

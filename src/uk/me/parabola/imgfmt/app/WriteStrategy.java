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

import java.io.IOException;

/**
 * To allow different strategies of writing to the file.
 * I want to be able to experiment with different schemes to write out.  It
 * may be possible to mix blocks between different files for example.
 *
 * @author Steve Ratcliffe
 */
public interface WriteStrategy {
	/**
	 * Called to write out any saved buffers.  The strategy may write
	 * directly to the file in which case this would have nothing or
	 * little to do.
	 * @throws IOException If there is an error writing.
	 */
	public void sync() throws IOException;

	/**
	 * Called when the stream is closed.  Any resources can be freed.
	 */
	public void close();

	/**
	 * Get the position.  Needed because may not be reflected in the underlying
	 * file if being buffered.
	 *
	 * @return The logical position within the file.
	 */
	public int position();

	/**
	 * Write out a single byte.
	 * @param b The byte to write.
	 */
	public void put(byte b);

	/**
	 * Write out two bytes.  Done in the correct byte order.
	 * @param c The value to write.
	 */
	public void putChar(char c);

	/**
	 * Write out 4 byte value.
	 * @param val The value to write.
	 */
	public void putInt(int val);

	/**
	 * Write out an arbitary length sequence of bytes.
	 *
	 * @param val The values to write.
	 */
	void put(byte[] val);
}

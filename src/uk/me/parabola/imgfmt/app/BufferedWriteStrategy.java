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

import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.log4j.Logger;

/**
 * A straight forward implementation that just keeps all the data in a buffer
 * until the file needs to be written to disk.
 *
 * @author Steve Ratcliffe
 */
public class BufferedWriteStrategy implements WriteStrategy {
	private static final Logger log = Logger.getLogger(BufferedWriteStrategy.class);
	
	private static final int MAX_SIZE = 1024 * 127; // TODO: grow it automatically later
	private final ByteBuffer buf = ByteBuffer.allocate(MAX_SIZE);
	private final ImgChannel chan;
	private int maxSize;


	public BufferedWriteStrategy(ImgChannel chan) {
		this.chan = chan;
		buf.order(ByteOrder.LITTLE_ENDIAN);
	}

	/**
	 * Called to write out any saved buffers.  The strategy may write
	 * directly to the file in which case this would have nothing or
	 * little to do.
	 */
	public void sync() throws IOException {
		buf.limit(maxSize);
		buf.position(0);
		log.debug("syncing to pos " + chan.position() + ", size" + buf.limit());
		chan.write(buf);
	}

	/**
	 * Get the position.  Needed because may not be reflected in the underlying
	 * file if being buffered.
	 *
	 * @return The logical position within the file.
	 */
	public int position() {
		return buf.position();
	}

	/**
	 * Set the position of the file.
	 *
	 * @param pos The new position in the file.
	 */
	public void position(int pos) {
		int cur = position();
		if (cur > maxSize)
			maxSize = cur;
		buf.position(pos);
	}

	/**
	 * Called when the stream is closed.  Any resources can be freed.
	 */
	public void close() throws IOException {
		chan.close();
	}

	/**
	 * Write out a single byte.
	 *
	 * @param b The byte to write.
	 */
	public void put(byte b) {
		buf.put(b);
	}

	/**
	 * Write out two bytes.  Done in the correct byte order.
	 *
	 * @param c The value to write.
	 */
	public void putChar(char c) {
		buf.putChar(c);
	}

	/**
	 * Write out 4 byte value.
	 *
	 * @param val The value to write.
	 */
	public void putInt(int val) {
		buf.putInt(val);
	}

	/**
	 * Write out an arbitary length sequence of bytes.
	 *
	 * @param val The values to write.
	 */
	public void put(byte[] val) {
		buf.put(val);
	}

	/**
	 * Write out part of a byte array.
	 *
	 * @param src	The array to take bytes from.
	 * @param start  The start position.
	 * @param length The number of bytes to write.
	 */
	public void put(byte[] src, int start, int length) {
		log.debug("start+len " + start + ", " + length);
		buf.put(src, start, length);
	}
}

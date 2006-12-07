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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Steve Ratcliffe
 */
public class BufferedWriteStrategy implements WriteStrategy {
	private ByteArrayOutputStream buf3;

	private static int MAX_SIZE = 1024 * 50; //XXX will grow it automatically later
	private ByteBuffer buf = ByteBuffer.allocate(MAX_SIZE);
	private ImgChannel chan;


	public BufferedWriteStrategy(ImgChannel chan) {
		this.chan = chan;
	}

	/**
	 * Called to write out any saved buffers.  The strategy may write
	 * directly to the file in which case this would have nothing or
	 * little to do.
	 */
	public void sync() throws IOException {
		chan.write(buf);
	}

	/**
	 * Called when the stream is closed.  Any resources can be freed.
	 */
	public void close() {
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
}

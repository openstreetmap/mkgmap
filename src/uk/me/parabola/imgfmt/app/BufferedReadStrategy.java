/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Read from an img file via a buffer.
 *
 * @author Steve Ratcliffe
 */
public class BufferedReadStrategy implements ReadStrategy {
	private static final Logger log = Logger.getLogger(BufferedReadStrategy.class);

	// Buffer size, must be a power of 2
	private static final int BUF_SIZE = 0x1000;

	private final ImgChannel chan;

	// The buffer that we read out of
	private final ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
	private long bufStart;
	private int bufSize = -1;

	// We keep our own idea of the file position.
	private long position;

	public BufferedReadStrategy(ImgChannel chan) {
		this.chan = chan;
	}

	/**
	 * Called when the stream is closed.  Any resources can be freed.
	 *
	 * @throws IOException When there is an error in closing.
	 */
	public void close() throws IOException {
		chan.close();
	}

	/**
	 * Get the position.  Needed because may not be reflected in the underlying
	 * file if being buffered.
	 *
	 * @return The logical position within the file.
	 */
	public long position() {
		return position;
	}

	/**
	 * Set the position of the file.
	 *
	 * @param pos The new position in the file.
	 */
	public void position(long pos) {
		position = pos;
	}

	/**
	 * Read in a single byte from the current position.
	 *
	 * @return The byte that was read.
	 */
	public byte get() throws ReadFailedException {
		// Check if the current position is within the buffer
		fillBuffer();

		int pos = (int) (position - bufStart);
		assert pos < bufSize;

		position++;
		return buf.get(pos);
	}

	/**
	 * Read in two bytes.  Done in the correct byte order.
	 *
	 * @return The 2 byte integer that was read.
	 */
	public char getChar() throws ReadFailedException {
		// Slow but sure implementation
		byte b1 = get();
		byte b2 = get();
		return (char) (((b2 & 0xff) << 8) + (b1 & 0xff));
	}

	/**
	 * Read a three byte signed quantity.
	 * @return The read value.
	 * @throws ReadFailedException
	 */
	public int get3() throws ReadFailedException {
		// Slow but sure implementation
		byte b1 = get();
		byte b2 = get();
		byte b3 = get();

		return (b1 & 0xff)
				| ((b2 & 0xff) << 8)
				| (b3 << 16)
				;
	}

	/**
	 * Read in a 4 byte value.
	 *
	 * @return A 4 byte integer.
	 */
	public int getInt() throws ReadFailedException {
		// Slow but sure implementation
		byte b1 = get();
		byte b2 = get();
		byte b3 = get();
		byte b4 = get();
		return (b1 & 0xff)
				| ((b2 & 0xff) << 8)
				| ((b3 & 0xff) << 16)
				| ((b4 & 0xff) << 24)
				;
	}

	/**
	 * Read in an arbitary length sequence of bytes.
	 *
	 * @param len The number of bytes to read.
	 */
	public byte[] get(int len) throws ReadFailedException {
		byte[] bytes = new byte[len];

		// Slow but sure implementation.
		for (int i = 0; i < len; i++) {
			bytes[i] = get();
		}
		return bytes;
	}

	/**
	 * Check to see if the buffer contains the byte at the current position.
	 * If not then it is re-read so that it does.
	 *
	 * @throws ReadFailedException If the buffer needs filling and the file cannot be
	 * read.
	 */
	private void fillBuffer() throws ReadFailedException {
		// If we are no longer inside the buffer, then re-read it.
		if (position < bufStart || position >= bufStart + bufSize) {

			// Get channel position on a block boundry.
			bufStart = position & ~(BUF_SIZE - 1);
			chan.position(bufStart);
			log.debug("reading in a buffer start=", bufStart);

			// Fill buffer
			buf.clear();
			bufSize = 0;
			try {
				bufSize = chan.read(buf);
			} catch (IOException e) {
				throw new ReadFailedException("failed to fill buffer", e);
			}

			log.debug("there were", bufSize, "bytes read");
		}
	}
}

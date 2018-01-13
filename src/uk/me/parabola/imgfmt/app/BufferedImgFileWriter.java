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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileLink;
import uk.me.parabola.log.Logger;

/**
 * A straight forward implementation that just keeps all the data in a buffer
 * until the file needs to be written to disk.
 *
 * @author Steve Ratcliffe
 */
public class BufferedImgFileWriter implements ImgFileWriter {
	private static final Logger log = Logger.getLogger(BufferedImgFileWriter.class);

	private static final int KBYTE = 1024;
	private static final int INIT_SIZE = 16 * KBYTE;
	private static final int GROW_SIZE = 128 * KBYTE;
	private static final int GUARD_SIZE = KBYTE;

	private final ImgChannel chan;

	private ByteBuffer buf = ByteBuffer.allocate(INIT_SIZE);
	private int bufferSize = INIT_SIZE;

	// The size of the file.  Note that for this to be set properly, the
	// position must be set to a low value after the full file is written. This
	// always happens because we go back and write the header after all is
	// written.
	private int maxSize;

	// The maximum allowed file size.
	private long maxAllowedSize = 0xffffff;

	public BufferedImgFileWriter(ImgChannel chan) {
		this.chan = chan;
		buf.order(ByteOrder.LITTLE_ENDIAN);
		if (chan instanceof FileLink) {
			((FileLink) chan).link(this::getSize, this::sync);
		}
	}

	/**
	 * Called to write out any saved buffers.  The strategy may write
	 * directly to the file in which case this would have nothing or
	 * little to do.
	 */
	public void sync() throws IOException {
		buf.limit(maxSize);
		buf.position(0);
		log.debug("syncing to pos", chan.position(), ", size", buf.limit());
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
	public void position(long pos) {
		int cur = position();
		if (cur > maxSize)
			maxSize = cur;
		buf.position((int) pos);
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
		ensureSize(1);
		buf.put(b);
	}

	/**
	 * Write out two bytes.  Done in the correct byte order.
	 *
	 * @param c The value to write.
	 */
	public void putChar(char c) {
		ensureSize(2);
		buf.putChar(c);
	}

	/**
	 * Write out int in range 0..255 as single byte.
	 * Use instead of put() for unsigned for clarity.
	 * @param val The value to write.
	 */
	public void put1(int val) {
		assert val >= 0 && val <= 255 : val;
		ensureSize(1);
		buf.put((byte)val);
	}

	/**
	 * Write out int in range 0..65535 as two bytes in correct byte order.
	 * Use instead of putChar() for unsigned for clarity.
	 * @param val The value to write.
	 */
	public void put2(int val) {
		assert val >= 0 && val <= 65535 : val;
		ensureSize(2);
		buf.putShort((short)val);
	}

	/**
	 * Write out a 3 byte value in the correct byte order etc.
	 *
	 * @param val The value to write.
	 */
	public void put3(int val) {
		ensureSize(3);
		buf.put((byte) (val & 0xff));
		buf.putChar((char) (val >> 8));
	}

	/**
	 * Write out 4 byte value.
	 *
	 * @param val The value to write.
	 */
	public void putInt(int val) {
		ensureSize(4);
		buf.putInt(val);
	}

	/**
	 * Write out 1-4 bytes.  Done in the correct byte order.
	 *
	 * @param nBytes The number of bytes to write.
	 * @param val The value to write.
	 */
	public void putN(int nBytes, int val) {
		ensureSize(nBytes);
		switch (nBytes) {
		case 1:
			buf.put((byte)val);
			break;
		case 2:
			buf.putShort((short)val);
			break;
		case 3:
			buf.put((byte)val);
			buf.putShort((short)(val >> 8));
			break;
		case 4:
			buf.putInt(val);
			break;
		default:
			assert false : nBytes;
		}
	}

	/**
	 * Write out an arbitrary length sequence of bytes.
	 *
	 * @param val The values to write.
	 */
	public void put(byte[] val) {
		ensureSize(val.length);
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
		ensureSize(length);
		buf.put(src, start, length);
	}

	public void put(ByteBuffer src) {
		ensureSize(src.limit());
		buf.put(src);
	}

	/**
	 * Get the size of the file as written.
	 *
	 * NOTE: that calling this is only valid at certain times.
	 * 
	 * @return The size of the file, if it is available.
	 */
	public long getSize() {
		return maxSize;
	}

	public ByteBuffer getBuffer() {
		return buf;
	}

	/**
	 * Make sure there is enough room for the data we are about to write.
	 *
	 * @param length The amount of data.
	 */
	private void ensureSize(int length) {
		int needed = buf.position() + length;
		if (needed > (bufferSize - GUARD_SIZE)) {
			while(needed > (bufferSize - GUARD_SIZE))
				bufferSize += GROW_SIZE;
			if (bufferSize > maxAllowedSize) {
				// Previous message was confusing people, although it is difficult to come
				// up with something that is strictly true in all situations.
				throw new MapFailedException(
						"There is not enough room in a single garmin map for all the input data." +
								" The .osm file should be split into smaller pieces first.");
			}
			ByteBuffer newb = ByteBuffer.allocate(bufferSize);
			newb.order(ByteOrder.LITTLE_ENDIAN);
			buf.flip();
			newb.put(buf);
			buf = newb;
		}
	}

	public void setMaxSize(long maxSize) {
		this.maxAllowedSize = maxSize;
	}
}

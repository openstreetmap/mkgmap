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
package uk.me.parabola.imgfmt.app;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * Write img file data to a temporary file. On a call to sync() the data
 * is copied to the output channel.
 *
 * @author Steve Ratcliffe
 */
public class FileBackedImgFileWriter implements ImgFileWriter{
	private final ImgChannel outputChan;
	private final File tmpFile;
	private final BufferedOutputStream file;
	private final FileChannel tmpChannel;

	public FileBackedImgFileWriter(ImgChannel chan) {
		this.outputChan = chan;

		try {
			tmpFile = File.createTempFile("img", null, new File("."));
			tmpFile.deleteOnExit();

			FileOutputStream out = new FileOutputStream(tmpFile);
			tmpChannel = out.getChannel();
			file = new BufferedOutputStream(out, 16*1024);
		} catch (IOException e) {
			throw new MapFailedException("Could not create mdr temporary file");
		}
	}

	/**
	 * Maps the temporary file and copies to the output channel.
	 *
	 * @throws IOException If there is an error writing.
	 */
	public void sync() throws IOException {
		file.close();
		FileInputStream is = null;
		try {
			is = new FileInputStream(tmpFile);
			FileChannel channel = is.getChannel();
			ByteBuffer buffer = channel.map(MapMode.READ_ONLY, 0, channel.size());
			outputChan.write(buffer);
		} finally {
			Utils.closeFile(is);
			tmpFile.delete();
		}
	}

	/**
	 * Get the position.  Have to flush the buffer before getting the position.
	 *
	 * @return The logical position within the file.
	 */
	public int position() {
		try {
			file.flush();
			return (int) tmpChannel.position();
		} catch (IOException e) {
			return 0;
		}
	}

	/**
	 * Set the position of the file.
	 * The buffer has to be flushed first.
	 *
	 * @param pos The new position in the file.
	 */
	public void position(long pos) {
		try {
			file.flush();
			tmpChannel.position(pos);
		} catch (IOException e) {
			throw new MapFailedException("Could not set position in mdr tmp file");
		}
	}

	/**
	 * Write out a single byte.
	 *
	 * @param b The byte to write.
	 */
	public void put(byte b) {
		try {
			file.write(b);
		} catch (IOException e) {
			throw new MapFailedException("could not write byte to mdr tmp file");
		}
	}

	/**
	 * Write out two bytes. Can't use writeChar() since need to reverse the byte
	 * order.
	 *
	 * @param c The value to write.
	 */
	public void putChar(char c) {
		try {
			file.write(c);
			file.write(c >> 8);
		} catch (IOException e) {
			throw new MapFailedException("could not write char to mdr tmp file");
		}
	}

	/**
	 * Write out three bytes.  Done in the little endian byte order.
	 *
	 * @param val The value to write, only the bottom three bytes will be written.
	 */
	public void put3(int val) {
		try {
			file.write(val);
			file.write(val >> 8);
			file.write(val >> 16);
		} catch (IOException e) {
			throw new MapFailedException("could not write3 to mdr tmp file");
		}
	}

	/**
	 * Write out 4 byte value.
	 *
	 * @param val The value to write.
	 */
	public void putInt(int val) {
		try {
			file.write(val);
			file.write(val >> 8);
			file.write(val >> 16);
			file.write(val >> 24);
		} catch (IOException e) {
			throw new MapFailedException("could not write int to mdr tmp file");
		}
	}

	/**
	 * Write out an arbitrary length sequence of bytes.
	 *
	 * @param val The values to write.
	 */
	public void put(byte[] val) {
		try {
			file.write(val);
		} catch (IOException e) {
			throw new MapFailedException("could not write bytes to mdr tmp file");
		}
	}

	/**
	 * Write out part of a byte array.
	 *
	 * @param src The array to take bytes from.
	 * @param start The start position.
	 * @param length The number of bytes to write.
	 */
	public void put(byte[] src, int start, int length) {
		try {
			file.write(src, start, length);
		} catch (IOException e) {
			throw new MapFailedException("could not write bytes to mdr tmp file");
		}
	}

	/**
	 * Write out a complete byte buffer.
	 *
	 * @param src The buffer to write.
	 */
	public void put(ByteBuffer src) {
		try {
			if (src.hasArray()) {
				file.write(src.array());
			} else {
				file.flush();
				tmpChannel.write(src);
			}
		} catch (IOException e) {
			throw new MapFailedException("could not write buffer to mdr tmp file");
		}
	}

	/**
	 * Returns the size of the file.
	 *
	 * @return The file size in bytes.
	 */
	public long getSize() {
		try {
			return tmpChannel.size();
		} catch (IOException e) {
			throw new MapFailedException("could not get size of mdr tmp file");
		}
	}

	/**
	 * Closes this stream and releases any system resources associated with it. If the stream is already closed then
	 * invoking this method has no effect.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public void close() throws IOException {
		outputChan.close();
	}
}

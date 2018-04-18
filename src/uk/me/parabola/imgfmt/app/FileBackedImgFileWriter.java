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

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Sized;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileLink;

/**
 * Write img file data to a temporary file. On a call to sync() the data
 * is copied to the output channel.
 *
 * @author Steve Ratcliffe
 */
public class FileBackedImgFileWriter implements ImgFileWriter, Sized {
	private final ImgChannel outputChan;
	private final File tmpFile;
	private final BufferedOutputStream file;
	private final FileChannel tmpChannel;
	private long finalSize;

	public FileBackedImgFileWriter(ImgChannel chan, File outputDir) {
		this.outputChan = chan;

		try {
			tmpFile = File.createTempFile("img", null, outputDir);
			tmpFile.deleteOnExit();

			FileOutputStream out = new FileOutputStream(tmpFile);
			tmpChannel = out.getChannel();
			file = new BufferedOutputStream(out, 16*1024);
		} catch (IOException e) {
			throw new MapFailedException("Could not create temporary file");
		}

		if (chan instanceof FileLink) {
			((FileLink) chan).link(this, this);
		}
	}

	/**
	 * Maps the temporary file and copies to the output channel.
	 *
	 * @throws IOException If there is an error writing.
	 */
	public void sync() throws IOException {
		finalSize = getSize();
		file.close();

		try (FileInputStream is = new FileInputStream(tmpFile); FileChannel channel = is.getChannel()) {
			channel.transferTo(0, channel.size(), outputChan);
		} finally {
			if (!tmpFile.delete())
				System.err.println("could not delete temporary file " + tmpFile.getPath());
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
			throw new MapFailedException("could not set position in temporary file " + tmpFile.getPath());
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
	 * Write out int in range -128..127 as single byte.
	 * @param val The byte to write.
	 */
	public void put1s(int val) {
		assert val >= -128 && val <= 127 : val;
		try {
			file.write(val);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out int in range -32768..32767 as two bytes in little endian byte order.
	 * @param val The value to write.
	 */
	public void put2s(int val) {
		assert val >= -32768 && val <= 32767 : val;
		try {
			file.write(val);
			file.write(val >> 8);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out int in range -0x800000..0x7fffff in little endian byte order.
	 * @param val The value to write.
	 */
	public void put3s(int val) {
		assert val >= -0x800000 && val <= 0x7fffff : val;
		try {
			file.write(val);
			file.write(val >> 8);
			file.write(val >> 16);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out int in range 0..255 as single byte.
	 * @param val The value to write.
	 */
	public void put1u(int val) {
		assert val >= 0 && val <= 255 : val;
 		try {
			file.write(val);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out int in range 0..65535 as two bytes in little endian byte order.
	 * @param val The value to write.
	 */
	public void put2u(int val) {
		assert val >= 0 && val <= 65535 : val;
 		try {
			file.write(val);
			file.write(val >> 8);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out int in range 0..0xffffff as three bytes in little endian byte order.
	 * @param val The value to write.
	 */
	public void put3u(int val) {
		assert val >= 0 && val <= 0xffffff : val;
		try {
			file.write(val);
			file.write(val >> 8);
			file.write(val >> 16);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out int as 1-4 bytes in little endian byte order.
	 *
	 * @param nBytes The number of bytes to write.
	 * @param val The value to write. Unsigned
	 */
	public void putNu(int nBytes, int val) {
		try {
			file.write(val);
			if (nBytes <= 1) {
				assert val >= 0 && val <= 255 : val;
				return;
			}
			file.write(val >> 8);
			if (nBytes <= 2) {
				assert val >= 0 && val <= 65535 : val;
				return;
			}
			file.write(val >> 16);
			if (nBytes <= 3) {
				assert val >= 0 && val <= 0xffffff : val;
				return;
			}
			file.write(val >> 24);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out 4 byte (signed or unsigned) value.
	 *
	 * @param val The value to write.
	 */
	public void put4(int val) {
		try {
			file.write(val);
			file.write(val >> 8);
			file.write(val >> 16);
			file.write(val >> 24);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
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
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
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
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Write out a complete byte buffer.
	 *
	 * @param src The buffer to write.
	 */
	public void put(ByteBuffer src) {
		try {
			file.flush();
			tmpChannel.write(src);
		} catch (IOException e) {
			throw new MapFailedException("could not write to temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Returns the size of the file.
	 *
	 * @return The file size in bytes.
	 */
	public long getSize() {
		if (finalSize > 0)
			return finalSize;

		try {
			file.flush();
			return tmpChannel.size();
		} catch (IOException e) {
			throw new MapFailedException("could not get size of temporary file " + tmpFile.getPath());
		}
	}

	/**
	 * Closes this stream with the result that the contents of the temporary file are written to the
	 * real output.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public void close() throws IOException {
		sync();
	}
}

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
 * Create date: 23-Sep-2007
 */
package uk.me.parabola.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream used to make reading little endian integers and strings from
 * the tdb file.
 *
 * @author Steve Ratcliffe
 */
public class StructuredInputStream extends InputStream {
	private final InputStream in;
	private boolean eof;

	public StructuredInputStream(InputStream in) {
		this.in = in;
	}

	/**
	 * Inplementation of read that delegates to the underlying stream.  It however
	 * also keeps track of the end of file status.
	 *
	 * @return The next byte, or -1 on eof.
	 * @throws IOException For problems reading.
	 */
	public int read() throws IOException {
		int r = in.read();
		if (r == -1)
			eof = true;
		return r;
	}

	/**
	 * Read a 2 byte little endian integer.
	 *
	 * @return The integer.
	 * @throws IOException If the stream could not be read.
	 */
	public int read2() throws IOException {
		int a = read() & 0xff;
		int b = read() & 0xff;

		if (isEof())
			throw new EndOfFileException();

		return (b << 8) + a;
	}

	/**
	 * Read a 4 byte integer quantity.  As always this is little endian.
	 *
	 * @return The integer.
	 * @throws IOException If the stream could not be read.
	 */
	public int read4() throws IOException {
		int a = read() & 0xff;
		int b = read() & 0xff;
		int c = read() & 0xff;
		int d = read() & 0xff;

		return (d << 24) | (c << 16) | (b << 8) | a;
	}

	/**
	 * Read a nul terminated string from the input stream.
	 *
	 * @return A string, without the null terminator.
	 * @throws IOException If the stream cannot be read.
	 */
	public String readString() throws IOException {
		StringBuffer name = new StringBuffer();
		int b;
		while ((b = read()) != '\0' && b != -1) {
			name.append((char) (b & 0xff));
		}
		return name.toString();
	}

	boolean isEof() {
		return eof;
	}

	/**
	 * Test if we are at the end of the file by marking the position and trying
	 * to read the next byte.  If not at the end then the stream position is
	 * reset and all is as before.
	 *
	 * @return True if we are at the end of the stream.
	 */
	public boolean testEof() {
		assert in.markSupported();
		in.mark(1);
		try {
			int b = in.read();
			if (b == -1) {
				eof = true;
			} else {
				in.reset();
			}
			return isEof();
		} catch (IOException e) {
			return true;
		}
	}

}

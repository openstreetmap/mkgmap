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
package uk.me.parabola.tdbfmt;

import java.io.InputStream;
import java.io.IOException;

/**
 * @author Steve Ratcliffe
 */
public class StructuredInputStream extends InputStream {
	private InputStream in;

	public StructuredInputStream(InputStream in) {
		this.in = in;
	}

	public int read() throws IOException {
		return in.read();
	}

	public int read2() throws IOException {
		int a = in.read();
		if (a == -1)
			throw new EndOfFileException();
		int b = in.read();
		if (b == -1)
			throw new EndOfFileException();

		return (b & 0xff) << 8 | (a & 0xff);
	}

	/**
	 * Read a nul terminated string from the input stream.
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
}

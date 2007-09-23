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

import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/**
 * @author Steve Ratcliffe
 */
public class StructuredOutputStream extends OutputStream {
	private OutputStream out;

	public StructuredOutputStream(OutputStream out) {
		this.out = out;
	}

	public void write(int b) throws IOException {
		out.write(b);
	}

	public void write2(int b) throws IOException {
		out.write(b);
		out.write(b >> 8);
	}

	public void write4(int b) throws IOException {
		out.write(b);
		out.write(b >> 8);
		out.write(b >> 16);
		out.write(b >> 24);
	}

	public void writeString(String s) throws IOException {
		for (char c : s.toCharArray()) {
			out.write((byte) c);
		}

		out.write('\0');
	}

	public byte[] toByteArray() {
		if (out instanceof ByteArrayOutputStream) {
			return ((ByteArrayOutputStream) out).toByteArray();
		}
		return null;
	}
}

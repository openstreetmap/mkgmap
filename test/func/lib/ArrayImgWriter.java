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
package func.lib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * A writer that just writes to an array for testing.
 *
 * @author Steve Ratcliffe
 */
public class ArrayImgWriter implements ImgFileWriter {
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	public void sync() throws IOException {
	}

	public int position() {
		return out.size();
	}

	public void position(long pos) {
		throw new UnsupportedOperationException();
	}

	public void put(byte b) {
		out.write(b);
	}

	public void putChar(char c) {
		out.write(c & 0xff);
		out.write((c >> 8) & 0xff);
	}

	public void put1(int val) {
		out.write(val & 0xff);
	}

	public void put2(int val) {
		out.write(val & 0xff);
		out.write((val >> 8) & 0xff);
	}

	public void put3(int val) {
		out.write(val & 0xff);
		out.write((val >> 8) & 0xff);
		out.write((val >> 16) & 0xff);
	}

	public void putN(int nBytes, int val) {
		out.write(val & 0xff);
		if (nBytes <= 1)
			return;
		out.write((val >> 8) & 0xff);
		if (nBytes <= 2)
			return;
		out.write((val >> 16) & 0xff);
		if (nBytes <= 3)
			return;
		out.write((val >> 24) & 0xff);
	}

	public void putInt(int val) {
		out.write(val & 0xff);
		out.write((val >> 8) & 0xff);
		out.write((val >> 16) & 0xff);
		out.write((val >> 24) & 0xff);
	}

	public void put(byte[] val) {
		out.write(val, 0, val.length);
	}

	public void put(byte[] src, int start, int length) {
		out.write(src, start, length);
	}

	public void put(ByteBuffer src) {
		byte[] array = src.array();
		out.write(array, 0, src.limit());
	}

	public long getSize() {
		return out.size();
	}

	public void close() throws IOException {
		out.close();
	}

	public byte[] getBytes() {
		return out.toByteArray();
	}
}

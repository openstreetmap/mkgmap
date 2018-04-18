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

	public void put1s(int val) {
		assert val >= -128 && val <= 127 : val;
		out.write(val);
	}

	public void put2s(int val) {
		assert val >= -32768 && val <= 32767 : val;
		out.write(val);
		out.write(val >> 8);
	}

	public void put3s(int val) {
		assert val >= -0x800000 && val <= 0x7fffff : val;
		out.write(val);
		out.write(val >> 8);
		out.write(val >> 16);
	}

	public void put1u(int val) {
		assert val >= 0 && val <= 255 : val;
		out.write(val);
	}

	public void put2u(int val) {
		assert val >= 0 && val <= 65535 : val;
		out.write(val);
		out.write(val >> 8);
	}

	public void put3u(int val) {
		assert val >= 0 && val <= 0xffffff : val;
		out.write(val);
		out.write(val >> 8);
		out.write(val >> 16);
	}

	public void putNu(int nBytes, int val) {
		out.write(val);
		if (nBytes <= 1) {
			assert val >= 0 && val <= 255 : val;
			return;
		}
		out.write(val >> 8);
		if (nBytes <= 2) {
			assert val >= 0 && val <= 65535 : val;
			return;
		}
		out.write(val >> 16);
		if (nBytes <= 3) {
			assert val >= 0 && val <= 0xffffff : val;
			return;
		}
		out.write(val >> 24);
	}

	public void put4(int val) {
		out.write(val);
		out.write(val >> 8);
		out.write(val >> 16);
		out.write(val >> 24);
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

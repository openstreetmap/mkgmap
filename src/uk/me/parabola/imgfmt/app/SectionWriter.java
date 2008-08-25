/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 19-Jul-2008
 */
package uk.me.parabola.imgfmt.app;

import java.io.IOException;

/**
 * A section writer wraps a regular writer so that all the offsets
 * are relative to the start of a section.
 *
 * @author Steve Ratcliffe
 */
public class SectionWriter implements ImgFileWriter {

	private final ImgFileWriter writer;
	private Section section;
	private final int secStart;

	public SectionWriter(ImgFileWriter writer, Section section) {
		this.writer = writer;
		this.secStart = section.getPosition();
		this.section = section;
	}

	public void sync() throws IOException {
		writer.sync();
	}

	/**
	 * Note that this does not close the underlying file.
	 */
	public void close() {
		if (section != null)
			section.setSize(writer.position() - secStart);
		//writer.close();
	}

	public int position() {
		return writer.position() - secStart;
	}

	public void position(long pos) {
		writer.position(pos + secStart);
	}

	public void put(byte b) {
		writer.put(b);
	}

	public void putChar(char c) {
		writer.putChar(c);
	}

	public void put3(int val) {
		writer.put3(val);
	}

	public void putInt(int val) {
		writer.putInt(val);
	}

	public void put(byte[] val) {
		writer.put(val);
	}

	public void put(byte[] src, int start, int length) {
		writer.put(src, start, length);
	}

}

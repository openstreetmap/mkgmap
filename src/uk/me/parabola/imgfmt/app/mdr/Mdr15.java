/*
 * Copyright (C) 2009.
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
package uk.me.parabola.imgfmt.app.mdr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * The string table. This is not used by the device.
 *
 * There is a compressed and non-compressed version of this section.
 * We are starting with the regular string version.
 *
 * @author Steve Ratcliffe
 */
public class Mdr15 extends MdrSection {
	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	public Mdr15(MdrConfig config) {
		setConfig(config);

		// reserve the string at offset 0 to be the empty string.
		buffer.write(0);
	}

	public void writeSectData(ImgFileWriter writer) {
		writer.put(buffer.toByteArray());
	}

	public int getItemSize() {
		// variable sized records.
		return 0;
	}

	public int createString(String str) {
		int off = buffer.size();

		try {
			buffer.write(str.getBytes("latin1"));
			buffer.write(0);
		} catch (IOException e) {
			// Can't convert, return empty string instead.
			off = 0;
		}

		return off;
	}

	/**
	 * The meaning of number of items for this section is the largest string
	 * offset possible.  We are taking the total size of the string section
	 * for this.
	 */
	public int getPointerSize() {
		return numberToPointerSize(buffer.size());
	}
}

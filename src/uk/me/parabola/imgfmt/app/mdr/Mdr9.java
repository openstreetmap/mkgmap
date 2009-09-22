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

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Unknown.
 * Appears to be structured as a single byte followed by a three
 * byte quantity.
 *
 * @author Steve Ratcliffe
 */
public class Mdr9 extends MdrSection {
	public Mdr9(MdrConfig config) {
		setConfig(config);
	}

	public void writeSectData(ImgFileWriter writer) {
		//writer.putInt(0x101);
		writer.put((byte) 1);
		writer.put3(1);
	}

	public int getItemSize() {
		return 0;
	}
}
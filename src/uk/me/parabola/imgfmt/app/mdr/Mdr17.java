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
package uk.me.parabola.imgfmt.app.mdr;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Name indexes consisting of a prefix of the string and the record
 * number at which that prefix first occurs. So it is like 8 and 12
 * except that they are all combined together in this one section.
 *
 * @author Steve Ratcliffe
 */
public class Mdr17 extends MdrSection {
	public Mdr17(MdrConfig config) {
		setConfig(config);
	}

	public void writeSectData(ImgFileWriter writer) {
	}

	public int getItemSize() {
		return 0;
	}

	protected int numberOfItems() {
		return 0;
	}
}

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
 * @author Steve Ratcliffe
 */
public class Mdr1Record extends RecordBase {
	private final int mapNumber;
	private Mdr1MapIndex mdrMapIndex;
	private int indexOffset;

	public Mdr1Record(int mapNumber, MdrConfig config) {
		this.mapNumber = mapNumber;
	}

	public int getMapNumber() {
		return mapNumber;
	}

	public Mdr1MapIndex getMdrMapIndex() {
		return mdrMapIndex;
	}

	public void setMdrMapIndex(Mdr1MapIndex mdrMapIndex) {
		this.mdrMapIndex = mdrMapIndex;
	}

	public int getIndexOffset() {
		return indexOffset;
	}

	public void setIndexOffset(int indexOffset) {
		this.indexOffset = indexOffset;
	}
}

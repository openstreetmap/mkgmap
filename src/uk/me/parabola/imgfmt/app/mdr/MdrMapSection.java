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

/**
 * @author Steve Ratcliffe
 */
public abstract class MdrMapSection extends MdrSection {
	private Mdr1 index;

	public void setMapIndex(Mdr1 index) {
		this.index = index;
	}

	public void init(int sectionNumber) {
		int n = getNumberOfItems();
		int psize;
		if (n > 0xffff)
			psize = 3;
		else if (n > 0xff)
			psize = 2;
		else
			psize = 1;

		index.setPointerSize(sectionNumber, psize);
	}

	public void addPointer(int mapNumber, int recordNumber) {
		index.addPointer(mapNumber, recordNumber);
	}

	public abstract int getNumberOfItems();
}

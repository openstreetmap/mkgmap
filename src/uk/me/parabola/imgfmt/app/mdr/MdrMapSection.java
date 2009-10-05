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
	private int sectionNumber;

	public void setMapIndex(Mdr1 index) {
		this.index = index;
	}

	public void init(int sectionNumber) {
		this.sectionNumber = sectionNumber;
		int n = getNumberOfItems();
		int psize = 2;
		if (n > 0xffff)
			psize = 3;

		index.setPointerSize(sectionNumber, psize);
		System.out.printf("for section %d, use pointer size of %d\n", sectionNumber, psize);
	}

	public void addPointer(int mapNumber, int recordNumber) {
		index.addPointer(mapNumber, recordNumber);
	}

	public abstract int getNumberOfItems();
}

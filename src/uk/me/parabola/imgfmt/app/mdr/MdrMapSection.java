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
 * Super class of all sections that contain items that belong to a particular
 * map.
 *
 * @author Steve Ratcliffe
 */
public abstract class MdrMapSection extends MdrSection implements HasHeaderFlags {
	private Mdr1 index;

	public void setMapIndex(Mdr1 index) {
		this.index = index;
	}

	public void init(int sectionNumber) {
		// Set the size required to store the record numbers for this section.
		// There are no flags or minimums required here, unlike in setPointerSize()
		// which does a similar thing.
		int n = getNumberOfItems();
		index.setPointerSize(sectionNumber, numberToPointerSize(n));
	}

	/**
	 * Add a pointer to the reverse index for this section.
	 * @param recordNumber A record number in this section, belonging to the
	 * given map.
	 */
	public void addIndexPointer(int mapNumber, int recordNumber) {
		index.addPointer(mapNumber, recordNumber);
	}

	/**
	 * The number of records in this section.
	 * @return The number of items in the section.
	 */
	public abstract int getNumberOfItems();

	/**
	 * Get the size of an integer that is sufficient to store a record number
	 * from this section.  If the pointer has a flag(s) then this must be
	 * taken into account too.
	 * @return A number between 1 and 4 giving the number of bytes required
	 * to store the largest record number in this section.
	 */
	public abstract int getPointerSize();

	protected void putCityIndex(ImgFileWriter writer, int cityIndex, boolean isNew) {
		int flag = (isNew && cityIndex > 0)? getSizes().getCityFlag(): 0;
		putN(writer, getSizes().getCitySize(), cityIndex | flag);
	}

	protected void putRegionIndex(ImgFileWriter writer, int region) {
		putN(writer, getSizes().getCitySize(), region);
	}

	protected void putPoiIndex(ImgFileWriter writer, int poiIndex, boolean isNew) {
		int flag = isNew? getSizes().getPoiFlag(): 0;
		putN(writer, getSizes().getPoiSize(), poiIndex | flag);
	}
}

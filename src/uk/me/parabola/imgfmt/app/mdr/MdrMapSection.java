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

import java.util.List;

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

	/**
	 * This is called before the sections are written out, but after all the
	 * data is read into them.
	 * @param sectionNumber The one-based section number.
	 */
	public final void initIndex(int sectionNumber) {
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
		if (!isForDevice())
			index.addPointer(mapNumber, recordNumber);
	}

	protected void putCityIndex(ImgFileWriter writer, int cityIndex, boolean isNew) {
		int flag = (isNew && cityIndex > 0)? getSizes().getCityFlag(): 0;
		putN(writer, getSizes().getCitySizeFlagged(), cityIndex | flag);
	}

	protected void putRegionIndex(ImgFileWriter writer, int region) {
		// This is only called when putCityIndex might also be called and so has to be
		// the same size (probably ;)
		putN(writer, getSizes().getCitySizeFlagged(), region);
	}

	protected void putPoiIndex(ImgFileWriter writer, int poiIndex, boolean isNew) {
		int flag = isNew? getSizes().getPoiFlag(): 0;
		putN(writer, getSizes().getPoiSizeFlagged(), poiIndex | flag);
	}

	protected boolean hasFlag(int val) {
		return (getExtraValue() & val) != 0;
	}

	public abstract void relabelMaps(Mdr1 maps);

	/**
	 * Relabel every map-index in the given set of records.
	 *
	 * The maps must be in sorted order, but because of the incremental way that we build the
	 * index, this isn't known until the end.  So we get rewrite the mapIndex from the
	 * initial to the final ordering.
	 *
	 * @param maps The final ordering of the maps.
	 * @param records The set of records to be relabeled.
	 * @param <T> The type of the record. Must be a subclass of RecordBase.
	 */
	protected <T extends RecordBase> void relabel(Mdr1 maps, List<T> records) {
		for (T r : records) {
			int n = r.getMapIndex();
			int newIndex = maps.sortedMapIndex(n);
			r.setMapIndex(newIndex);
		}
	}
}

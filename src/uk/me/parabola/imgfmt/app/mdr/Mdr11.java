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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.imgfmt.app.trergn.Point;

/**
 * Holds all the POIs, including cities.  Arranged alphabetically by
 * the name.
 *
 * @author Steve Ratcliffe
 */
public class Mdr11 extends MdrMapSection {
	private final List<Mdr11Record> pois = new ArrayList<Mdr11Record>();

	public Mdr11(MdrConfig config) {
		setConfig(config);
	}

	public Mdr11Record addPoi(int mapIndex, Point point, String name, int strOff) {
		Mdr11Record poi = new Mdr11Record();
		poi.setMapIndex(mapIndex);
		poi.setPointIndex(point.getNumber());
		poi.setSubdiv(point.getSubdiv().getNumber());
		poi.setLblOffset(point.getLabel().getOffset());
		poi.setName(name);
		poi.setStrOffset(strOff);

		pois.add(poi);
		return poi;
	}

	public void writeSectData(ImgFileWriter writer) {
		List<SortKey<Mdr11Record>> keys = MdrUtils.sortList(getConfig().getSort(), pois);

		int count = 1;
		for (SortKey<Mdr11Record> k : keys) {
			Mdr11Record poi = k.getObject();
			addIndexPointer(poi.getMapIndex(), count);
			poi.setRecordNumber(count++);

			putMapIndex(writer, poi.getMapIndex());
			writer.put((byte) poi.getPointIndex());
			writer.putChar((char) poi.getSubdiv());
			writer.put3(poi.getLblOffset());
			if (poi.isCity())
				putRegionIndex(writer, poi.getRegionIndex());
			else
				putCityIndex(writer, poi.getCityIndex(), true);
			putStringOffset(writer, poi.getStrOffset());
		}
	}

	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize() + 6 + sizes.getCitySize() + sizes.getStrOffSize();
	}

	public int getNumberOfItems() {
		return pois.size();
	}

	public int getNumberOfPois() {
		return pois.size();
	}

	/**
	 * Get the size of an integer that is sufficient to store a record number
	 * from this section.
	 * @return A number between 1 and 4 giving the number of bytes required
	 * to store the largest record number in this section.
	 */
	public int getPointerSize() {
		return numberToPointerSize(pois.size() << 1);
	}

	public int getExtraValue() {
		int mdr11flags = 0x13;
		PointerSizes sizes = getSizes();

		// two bit field for city bytes.  minimum size of 2
		int citySize = sizes.getCitySize();
		if (citySize > 2)
			mdr11flags |= (citySize-2) << 2;

		return mdr11flags;
	}
}

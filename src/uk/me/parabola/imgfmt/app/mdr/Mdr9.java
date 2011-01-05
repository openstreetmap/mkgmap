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

import java.util.LinkedHashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * An index into mdr10.  There is a single byte group number followed by
 * the first record in mdr10 that belongs to that group.
 *
 * @author Steve Ratcliffe
 */
public class Mdr9 extends MdrSection {
	private final Map<Integer, Integer> index = new LinkedHashMap<Integer, Integer>();

	public Mdr9(MdrConfig config) {
		setConfig(config);
	}

	public void writeSectData(ImgFileWriter writer) {
		int poiSize = getSizes().getPoiSize();
		for (Map.Entry<Integer, Integer> ent : index.entrySet()) {
			int group = ent.getKey();
			writer.put((byte) group);
			putN(writer, poiSize, ent.getValue());
		}
	}

	/**
	 * The item size is one byte for the group and then enough bytes for the
	 * index into mdr10.
	 * @return Just return 4 for now.
	 */
	public int getItemSize() {
		return 1 + getSizes().getPoiSize();
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return index.size();
	}

	public void setGroups(Map<Integer, Integer> groupSizes) {
		int offset = 1;
		for (Map.Entry<Integer, Integer> ent : groupSizes.entrySet()) {
			index.put(ent.getKey(), offset);
			offset += ent.getValue();
		}
	}
}

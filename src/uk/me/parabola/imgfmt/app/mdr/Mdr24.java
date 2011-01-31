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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * Countries sorted by name.  Same number of entries as 14.
 *
 * @author Steve Ratcliffe
 */
public class Mdr24 extends MdrSection {
	private final List<Mdr14Record> countries = new ArrayList<Mdr14Record>();

	public Mdr24(MdrConfig config) {
		setConfig(config);
	}

	public void sortCountries(List<Mdr14Record> list) {
		Sort sort = getConfig().getSort();
		List<SortKey<Mdr14Record>> keys = new ArrayList<SortKey<Mdr14Record>>();
		for (Mdr14Record c : list) {
			SortKey<Mdr14Record> sortKey = sort.createSortKey(c, c.getName(), c.getMapIndex());
			keys.add(sortKey);
		}

		Collections.sort(keys);

		for (SortKey<Mdr14Record> key : keys) {
			countries.add(key.getObject());
		}
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		String lastName = "";
		for (Mdr14Record c : countries) {
			putMapIndex(writer, c.getMapIndex());

			int flag = 0;
			String name = c.getName();
			if (!name.equals(lastName)) {
				flag = 0x800000;
				lastName = name;
			}

			writer.putChar((char) c.getCountryIndex());
			writer.put3(c.getLblOffset() | flag);
		}
	}

	/**
	 * The size of a record in the section.  This is not a constant and might vary
	 * on various factors, such as the file version, if we are preparing for a
	 * device, the number of maps etc.
	 *
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize() + 2 + 3;
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return countries.size();
	}
}

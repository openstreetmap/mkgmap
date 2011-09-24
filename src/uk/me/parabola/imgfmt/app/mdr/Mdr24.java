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

	/**
	 * Sort the countries by name. Duplicates are kept.
	 * @param list The full list of countries.
	 */
	public void sortCountries(List<Mdr14Record> list) {
		Sort sort = getConfig().getSort();
		List<SortKey<Mdr14Record>> keys = MdrUtils.sortList(sort, list);

		Collections.sort(keys);

		String lastName = null;
		int lastMapIndex = 0;
		int record = 0;
		for (SortKey<Mdr14Record> key : keys) {
			Mdr14Record c = key.getObject();

			// If this is a new name, then we prepare a mdr29 record for it.
			String name = c.getName();

			if (lastMapIndex != c.getMapIndex() || !name.equals(lastName)) {
				record++;
				c.getMdr29().setMdr24(record);
				countries.add(c);

				lastName = name;
				lastMapIndex = c.getMapIndex();
			}
		}
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		String lastName = null;
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
	protected int numberOfItems() {
		return countries.size();
	}
}

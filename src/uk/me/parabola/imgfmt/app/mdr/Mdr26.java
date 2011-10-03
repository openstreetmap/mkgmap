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
 * An index into mdr28, sorted by country name.
 *
 * @author Steve Ratcliffe
 */
public class Mdr26 extends MdrSection {
	private final List<Mdr28Record> index = new ArrayList<Mdr28Record>();

	public Mdr26(MdrConfig config) {
		setConfig(config);
	}

	public void sortMdr28(List<Mdr28Record> in) {
		Sort sort = getConfig().getSort();

		List<SortKey<Mdr28Record>> sortList = new ArrayList<SortKey<Mdr28Record>>();
		int record = 0;
		for (Mdr28Record mdr28 : in) {
			SortKey<Mdr28Record> key = sort.createSortKey(mdr28, mdr28.getMdr14().getName(), ++record);
			sortList.add(key);
		}
		Collections.sort(sortList);

		addToIndex(sortList);
	}

	private void addToIndex(List<SortKey<Mdr28Record>> sortList) {
		String lastName = null;
		int record26 = 0;
		for (SortKey<Mdr28Record> key : sortList) {
			record26++;
			Mdr28Record mdr28 = key.getObject();
			Mdr14Record mdr14 = mdr28.getMdr14();
			assert mdr14 != null;

			// For each new name, set up the mdr29 record for it.
			String name = mdr14.getName();
			if (!name.equals(lastName)) {
				Mdr29Record mdr29 = mdr14.getMdr29();
				mdr29.setMdr26(record26);
				lastName = name;
			}
			index.add(mdr28);
		}
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		int size = getSizes().getSize(28);
		for (Mdr28Record record : index) {
			putN(writer, size, record.getIndex());
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
		return getSizes().getSize(28);
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	protected int numberOfItems() {
		return index.size();
	}
}

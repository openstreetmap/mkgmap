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
 * Regions sorted by name. Same number of records as mdr13.
 *
 * @author Steve Ratcliffe
 */
public class Mdr23 extends MdrSection {
	private final List<Mdr13Record> regions = new ArrayList<Mdr13Record>();

	public Mdr23(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * Takes the region list and sorts it according to the name.
	 * @param list Original region list.
	 */
	public void sortRegions(List<Mdr13Record> list) {
		Sort sort = getConfig().getSort();
		List<SortKey<Mdr13Record>> keys = new ArrayList<SortKey<Mdr13Record>>();
		for (Mdr13Record reg : list) {
			SortKey<Mdr13Record> key = sort.createSortKey(reg, reg.getName(), reg.getMapIndex());
			keys.add(key);
		}

		Collections.sort(keys);

		String lastName = "";
		int record = 0;
		Mdr28Record mdr28 = null;
		for (SortKey<Mdr13Record> key : keys) {
			record++;
			Mdr13Record reg = key.getObject();

			// If this is new name, then create a mdr28 record for it. This
			// will be further filled in when the other sections are prepared.
			String name = reg.getName();
			if (!name.equals(lastName)) {
				mdr28 = new Mdr28Record();
				mdr28.setName(name);
				mdr28.setStrOffset(reg.getStrOffset());
				mdr28.setMdr14(reg.getMdr14());
				mdr28.setMdr23(record);
				lastName = name;
			}

			assert mdr28 != null;
			reg.setMdr28(mdr28);

			regions.add(reg);
		}
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		String lastName = "";
		for (Mdr13Record reg : regions) {
			putMapIndex(writer, reg.getMapIndex());

			int flag = 0;
			String name = reg.getName();
			if (!name.equals(lastName)) {
				flag = 0x800000;
				lastName = name;
			}

			writer.putChar((char) reg.getRegionIndex());
			writer.putChar((char) reg.getCountryIndex());
			writer.put3(reg.getLblOffset() | flag);
		}
	}

	/**
	 * There is a map index followed by file region and country indexes.
	 * Then there is a label offset for the name, which strangely appears
	 * to be three bytes always, although that many would rarely (or never?) be
	 * required.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize() + 2 + 2 + 3;
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return regions.size();
	}

	public List<Mdr13Record> getRegions() {
		return Collections.unmodifiableList(regions);
	}
}

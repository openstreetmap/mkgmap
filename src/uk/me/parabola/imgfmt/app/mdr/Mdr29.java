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
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * An index of countries sorted by name with pointers to the other country related sections.
 * There is only one per name, not per name and map.
 *
 * @author Steve Ratcliffe
 */
public class Mdr29 extends MdrSection implements HasHeaderFlags {
	private final List<Mdr29Record> index = new ArrayList<>();
	private int max17;

	public Mdr29(MdrConfig config) {
		setConfig(config);
	}

	public void buildFromCountries(List<Mdr14Record> countries) {
		Sort sort = getConfig().getSort();
		List<SortKey<Mdr14Record>> keys = MdrUtils.sortList(sort, countries);

		// Sorted by name, for every new name we allocate a new 29 record and set the same one in every
		// country with the same name.
		String lastName = null;
		Mdr29Record mdr29 = null;
		for (SortKey<Mdr14Record> key : keys) {
			Mdr14Record country = key.getObject();

			String name = country.getName();
			if (!name.equals(lastName)) {
				mdr29 = new Mdr29Record();
				mdr29.setName(name);
				mdr29.setStrOffset(country.getStrOff());
				index.add(mdr29);
				lastName = name;
			}

			assert mdr29 != null;
			country.setMdr29(mdr29);
		}
	}

	protected void preWriteImpl() {
		if (!index.isEmpty()) {
			Mdr29Record r = index.get(index.size() - 1);
			this.max17 = r.getMdr17();
		}
	}
	
	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		int magic = getExtraValue();

		boolean hasString = (magic & 1) != 0;
		boolean has26 = (magic & 0x8) != 0;
		boolean has17 = (magic & 0x30) != 0;

		PointerSizes sizes = getSizes();
		int size24 = sizes.getSize(24);
		int size22 = sizes.getSize(22);
		int size25 = sizes.getSize(5);  // NB appears to be size of 5 (cities), not 25 (cities with country).
		int size26 = has26? sizes.getSize(26): 0;
		int size17 = numberToPointerSize(max17);
		for (Mdr29Record record : index) {
			putN(writer, size24, record.getMdr24());
			if (hasString)
				putStringOffset(writer, record.getStrOffset());
			putN(writer, size22, record.getMdr22());
			putN(writer, size25, record.getMdr25());
			if (has26)
				putN(writer, size26, record.getMdr26());
			if (has17)
				putN(writer, size17, record.getMdr17());
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
		int size = sizes.getSize(24)
				+ sizes.getSize(22)
				+ sizes.getSize(5)  // NB: not 25
				;
		if (isForDevice()) {
			if (!getConfig().getSort().isMulti())
				size += numberToPointerSize(max17);
		} else {
			size += sizes.getStrOffSize();
			size += sizes.getSize(26);
		}
		return size;
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	protected int numberOfItems() {
		return index.size();
	}

	/**
	 * Unknown flags.
	 *
	 * As there are 4 bits set and 4 extra fields, that might be it. Compare
	 * to mdr28 where there are 3 extra fields and 3 bits set. Just a guess...
	 */
	public int getExtraValue() {
		if (isForDevice()) {
			int magic = 0x6; // 22 and 25
			if (!getConfig().getSort().isMulti())
				magic |= numberToPointerSize(max17) << 4;
			return magic; // +17, -26, -strings
		}
		else
			return 0xf;  // strings, 22, 25 and 26
	}
}

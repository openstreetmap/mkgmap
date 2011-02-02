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

/**
 * An index of countries with pointers to the other country related sections.
 *
 * @author Steve Ratcliffe
 */
public class Mdr29 extends MdrSection implements HasHeaderFlags {
	private final List<Mdr29Record> index = new ArrayList<Mdr29Record>();

	public Mdr29(MdrConfig config) {
		setConfig(config);
	}

	public void buildFromCountries(List<Mdr14Record> countries) {
		for (Mdr14Record country : countries) {
			Mdr29Record mdr29 = country.getMdr29();
			if (mdr29 != null)
				index.add(mdr29);
		}
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		PointerSizes sizes = getSizes();
		int size24 = sizes.getSize(24);
		int size22 = sizes.getSize(22);
		int size25 = sizes.getSize(25);
		int size26 = sizes.getSize(26);
		for (Mdr29Record record : index) {
			putN(writer, size24, record.getMdr24());
			putStringOffset(writer, record.getStrOffset());
			putN(writer, size22, record.getMdr22());
			putN(writer, size25, record.getMdr25());
			putN(writer, size26, record.getMdr26());
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
		return sizes.getSize(24)
				+ sizes.getStrOffSize()
				+ sizes.getSize(22)
				+ sizes.getSize(25)
				+ sizes.getSize(26)
				;
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return index.size();
	}

	/**
	 * Unknown flags.
	 *
	 * As there are 4 bits set and 4 extra fields, that might be it. Compare
	 * to mdr28 where there are 3 extra fields and 3 bits set. Just a guess...
	 */
	public int getExtraValue() {
		return 0xf;
	}
}

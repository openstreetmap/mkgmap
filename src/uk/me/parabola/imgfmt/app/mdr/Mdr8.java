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
 * This section is a simple index into the streets section (mdr7).
 *
 * @author Steve Ratcliffe
 */
public class Mdr8 extends MdrSection implements HasHeaderFlags {
	private static final int STRING_WIDTH = 4;

	private List<Mdr8Record> index = new ArrayList<Mdr8Record>();

	public Mdr8(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		if (index.size() <= 1)
			return;
		int size = associatedSize();
		for (Mdr8Record s : index) {
			for (int i = 0; i< STRING_WIDTH; i++) {
				writer.put1u(s.getPrefix()[i]);
			}
			writer.putNu(size, s.getRecordNumber());
		}
	}

	protected int associatedSize() {
		return getSizes().getStreetSize();
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	protected int numberOfItems() {
		return index.size() > 1 ? 0: index.size();
	}

	/**
	 * The size of a record in the section.  This is not a constant and might vary
	 * on various factors, such as the file version, if we are preparing for a
	 * device, the number of maps etc.
	 *
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		return STRING_WIDTH + associatedSize();
	}

	public void setIndex(List<Mdr8Record> index) {
		this.index = index;
	}

	/**
	 * The header flags for the section.
	 * Possible values are not known.
	 *
	 * @return The correct value based on the contents of the section.  Zero if
	 *         nothing needs to be done.
	 */
	public int getExtraValue() {
		// this value is likely to depend on the size of the max record number.
		return (STRING_WIDTH << 8) + 0x0a;
	}
}

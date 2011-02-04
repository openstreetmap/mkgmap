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
 * Common code for 20, 21, 22 which are all lists of streets ordered in
 * different ways.
 * 
 * @author Steve Ratcliffe
 */
public abstract class Mdr2x extends MdrMapSection implements HasHeaderFlags {
	protected final List<Mdr7Record> streets = new ArrayList<Mdr7Record>();

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		String lastName = "";

		int size = getSizes().getStreetSizeFlagged();

		int recordNumber = 0;
		for (Mdr7Record street : streets) {
			assert street.getMapIndex() == street.getCity().getMapIndex() : street.getMapIndex() + "/" + street.getCity().getMapIndex();
			addIndexPointer(street.getMapIndex(), ++recordNumber);

			int index = street.getIndex();
			String name = street.getName();
			int flag = 1;
			if (name.equals(lastName)) {
				flag = 0;
			} else {
				lastName = name;
			}

			putN(writer, size, (index << 1) | flag);
		}
	}

	/**
	 * The size of a record in the section. For these sections there is
	 * one field that is an index into the streets with an extra bit for
	 * a flag.
	 */
	public int getItemSize() {
		return getSizes().getStreetSizeFlagged();
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return streets.size();
	}
}

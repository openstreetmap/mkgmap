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
import uk.me.parabola.imgfmt.app.Label;

/**
 * Common code for 20, 21, 22 which are all lists of streets ordered in
 * different ways.
 * 
 * @author Steve Ratcliffe
 */
public abstract class Mdr2x extends MdrMapSection implements HasHeaderFlags {
	protected List<Mdr7Record> streets = new ArrayList<Mdr7Record>();

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		String lastName = null;

		int size = getSizes().getStreetSizeFlagged();

		boolean hasLabel = hasFlag(0x2);
		int recordNumber = 0;
		for (Mdr7Record street : streets) {
			assert street.getMapIndex() == street.getCity().getMapIndex() : street.getMapIndex() + "/" + street.getCity().getMapIndex();
			addIndexPointer(street.getMapIndex(), ++recordNumber);

			int index = street.getIndex();
			String name = Label.stripGarminCodes(street.getName());
			
			int flag = 1;
			if (name.equals(lastName)) {
				flag = 0;
			} else {
				lastName = name;
			}

			if (hasLabel) {
				putMapIndex(writer, street.getMapIndex());
				int offset = street.getLabelOffset();
				if (flag != 0)
					offset |= 0x800000;
				writer.put3(offset);
				writer.put((byte) flag);
			}
			else
				putN(writer, size, (index << 1) | flag);
		}
	}

	/**
	 * The size of a record in the section.
	 * 
	 * For these sections there is one field that is an index into the
	 * streets with an extra bit for a flag.
	 * 
	 * In the device configuration, then there is a label and a flag, just like
	 * for mdr7.
	 */
	public int getItemSize() {
		int size;
		if (isForDevice()) {
			size = getSizes().getMapSize() + 3 + 1;
		} else {
			size = getSizes().getStreetSizeFlagged();
		}

		return size;
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	protected int numberOfItems() {
		return streets.size();
	}

	protected void releaseMemory() {
		streets = null;
	}
}

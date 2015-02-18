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
	protected List<Mdr7Record> streets = new ArrayList<>();

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		String lastName = null;
		Mdr7Record prev = null;

		int size = getSizes().getStreetSizeFlagged();

		boolean hasLabel = hasFlag(0x2);

		String lastPartial = null;
		int recordNumber = 0;
		for (Mdr7Record street : streets) {
			assert street.getMapIndex() == street.getCity().getMapIndex() : street.getMapIndex() + "/" + street.getCity().getMapIndex();
			addIndexPointer(street.getMapIndex(), ++recordNumber);

			int index = street.getIndex();

			String name = street.getName();

			int repeat = 1;
			if (name.equals(lastName) && sameGroup(street, prev))
				repeat = 0;

			if (hasLabel) {
				putMapIndex(writer, street.getMapIndex());
				int offset = street.getLabelOffset();
				if (repeat != 0)
					offset |= 0x800000;

				int trailing = 0;
				String partialName = street.getPartialName();
				if (!partialName.equals(lastPartial)) {
					trailing |= 1;
					offset |= 0x800000;
				}

				writer.put3(offset);
				writer.put(street.getOutNameOffset());

				writer.put((byte) trailing);

				lastPartial = partialName;
			} else
				putN(writer, size, (index << 1) | repeat);

			lastName = name;
			prev = street;
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
			// map-index, label, name-offset, 1byte flag
			size = getSizes().getMapSize() + 3 + 1 + 1;
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

	/**
	 * These sections are divided into groups based on city, region or country. This routine is
	 * implemented to return true if the two streets are in the same group.
	 *
	 * It is not clear if this is needed for region or country.
	 * @param street1 The first street.
	 * @param street2 The street to compare against.
	 * @return True if the streets are in the same group (city, region etc).
	 */
	protected abstract boolean sameGroup(Mdr7Record street1, Mdr7Record street2);

	public void relabelMaps(Mdr1 maps) {
		// Nothing to do, since all streets are re-labeled in their own section.
	}
}

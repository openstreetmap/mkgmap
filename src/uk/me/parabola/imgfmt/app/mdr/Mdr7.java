/*
 * Copyright (C) 2009.
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
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * The MDR 7 section is a list of all streets.  Only street names are saved
 * and so I believe that the NET section is required to make this work.
 *
 * @author Steve Ratcliffe
 */
public class Mdr7 extends MdrMapSection {
	private final List<Mdr7Record> streets = new ArrayList<Mdr7Record>();

	public Mdr7(MdrConfig config) {
		setConfig(config);
	}

	public void addStreet(int mapId, String name, int lblOffset, int strOff) {
		Mdr7Record st = new Mdr7Record();
		st.setMapIndex(mapId);
		st.setLabelOffset(lblOffset);
		st.setStringOffset(strOff);
		st.setName(name);
		streets.add(st);
	}

	public void writeSectData(ImgFileWriter writer) {
		List<SortKey<Mdr7Record>> sortedStreets = MdrUtils.sortList(getConfig().getSort(), streets);
		
		int recordNumber = 0;
		for (SortKey<Mdr7Record> sk : sortedStreets) {
			Mdr7Record s  = sk.getObject();
			recordNumber++;
			addIndexPointer(s.getMapIndex(), recordNumber);

			putMapIndex(writer, s.getMapIndex());
			writer.put3(s.getLabelOffset() | 0x800000); // TODO set flag correctly
			putStringOffset(writer, s.getStringOffset());
		}
	}


	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize() + 3 + sizes.getStrOffSize();
	}

	public int getNumberOfItems() {
		return streets.size();
	}

	/**
	 * Get the size of an integer that is sufficient to store a record number
	 * from this section.
	 * @return A number between 1 and 4 giving the number of bytes required
	 * to store the largest record number in this section.
	 */
	public int getPointerSize() {
		return numberToPointerSize(streets.size());
	}

	/**
	 * Value of 3 possibly the existence of the lbl field.
	 */
	public int getExtraValue() {
		return 3;
	}
}

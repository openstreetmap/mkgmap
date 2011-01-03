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
		if (name.length() < 4)
			return;
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
		int lastLab = -1;
		for (SortKey<Mdr7Record> sk : sortedStreets) {
			Mdr7Record s  = sk.getObject();
			streets.set(recordNumber, s);
			recordNumber++;
			addIndexPointer(s.getMapIndex(), recordNumber);

			putMapIndex(writer, s.getMapIndex());
			int lab = s.getLabelOffset();
			if (lab != lastLab) {
				lastLab = lab;
				lab |= 0x800000;
			}
			writer.put3(lab);
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

	/**
	 * Must be called after the section data is written so that the streets
	 * array is already sorted.
	 * @return List of index records.
	 */
	public List<Mdr8Record> getIndex() {
		List<Mdr8Record> list = new ArrayList<Mdr8Record>();
		for (int number = 0; number < streets.size(); number += 10240) {
			Mdr7Record record = streets.get(number);
			int endIndex = 4;
			String name = record.getName();
			if (endIndex > name.length()) {
				StringBuilder sb = new StringBuilder(name);
				while (sb.length() < endIndex)
					sb.append('\0');
				name = sb.toString();
			}
			String prefix = name.substring(0, endIndex);

			Mdr8Record indexRecord = new Mdr8Record();
			indexRecord.setPrefix(prefix);
			indexRecord.setRecordNumber(number);
			list.add(indexRecord);
		}
		return list;
	}
}

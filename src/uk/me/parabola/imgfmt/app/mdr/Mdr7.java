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
import java.util.Collections;
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
	private List<Mdr7Record> allStreets = new ArrayList<Mdr7Record>();
	private List<Mdr7Record> streets = new ArrayList<Mdr7Record>();

	public Mdr7(MdrConfig config) {
		setConfig(config);
	}

	public void addStreet(int mapId, String name, int lblOffset, int strOff, Mdr5Record mdrCity) {
		Mdr7Record st = new Mdr7Record();
		st.setMapIndex(mapId);
		st.setLabelOffset(lblOffset);
		st.setStringOffset(strOff);
		st.setName(name);
		st.setCity(mdrCity);
		allStreets.add(st);
	}

	/**
	 * Since we change the number of records by removing some after sorting,
	 * we sort and de-duplicate here.
	 */
	public void finish() {
		List<SortKey<Mdr7Record>> sortedStreets = MdrUtils.sortList(getConfig().getSort(), allStreets);

		// De-duplicate the street names so that there is only one entry
		// per map for the same name.
		int recordNumber = 0;
		Mdr7Record last = new Mdr7Record();
		for (SortKey<Mdr7Record> sk : sortedStreets) {
			Mdr7Record r = sk.getObject();
			if (r.getMapIndex() != last.getMapIndex() || !r.getName().equals(last.getName())) {
				recordNumber++;
				last = r;
				r.setIndex(recordNumber);
				streets.add(r);
			} else {
				// This has the same name (and map number) as the previous one. Save the pointer to that one
				// which is going into the file.
				r.setIndex(recordNumber);
			}
		}
		MDRFile.printMem("finish 7", sortedStreets);
	}

	public void writeSectData(ImgFileWriter writer) {
		String lastName = null;
		for (Mdr7Record s : streets) {
			addIndexPointer(s.getMapIndex(), s.getIndex());

			putMapIndex(writer, s.getMapIndex());
			int lab = s.getLabelOffset();
			String name = s.getName();
			int trailingFlags = 0;
			if (!name.equals(lastName)) {
				lab |= 0x800000;
				lastName = name;
				trailingFlags = 1;
			}
			writer.put3(lab);
			putStringOffset(writer, s.getStringOffset());
			
			writer.put((byte) trailingFlags);
		}
	}

	/**
	 * For the map number, label, string (opt), and trailing flags (opt).
	 * The trailing flags are variable size. We are just using 1 now.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize()
				+ 3
				+ sizes.getStrOffSize()
				+ 1;
	}

	protected int numberOfItems() {
		return streets.size();
	}

	/**
	 * Value of 3 possibly the existence of the lbl field.
	 */
	public int getExtraValue() {
		return 0x43;
	}

	protected void releaseMemory() {
		allStreets = null;
		streets = null;
	}

	/**
	 * Must be called after the section data is written so that the streets
	 * array is already sorted.
	 * @return List of index records.
	 */
	public List<Mdr8Record> getIndex() {
		List<Mdr8Record> list = new ArrayList<Mdr8Record>();
		for (int number = 1; number <= streets.size(); number += 10240) {
			String prefix = getPrefixForRecord(number);

			// need to step back to find the first...
			int rec = number;
			while (rec > 1) {
				String p = getPrefixForRecord(rec);
				if (!p.equals(prefix)) {
					rec++;
					break;
				}
				rec--;
			}

			Mdr8Record indexRecord = new Mdr8Record();
			indexRecord.setPrefix(prefix);
			indexRecord.setRecordNumber(rec);
			list.add(indexRecord);
		}
		return list;
	}

	/**
	 * Get the prefix of the name at the given record.
	 * @param number The record number.
	 * @return The first 4 (or whatever value is set) characters of the street
	 * name.
	 */
	private String getPrefixForRecord(int number) {
		Mdr7Record record = streets.get(number-1);
		int endIndex = MdrUtils.STREET_INDEX_PREFIX_LEN;
		String name = record.getName();
		if (endIndex > name.length()) {
			StringBuilder sb = new StringBuilder(name);
			while (sb.length() < endIndex)
				sb.append('\0');
			name = sb.toString();
		}
		return name.substring(0, endIndex);
	}

	public List<Mdr7Record> getStreets() {
		return Collections.unmodifiableList(allStreets);
	}
}

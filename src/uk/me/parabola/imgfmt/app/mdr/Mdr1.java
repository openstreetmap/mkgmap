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
import java.util.Comparator;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * The section MDR 1 contains a list of maps and for each map
 * an offset to a reverse index for that map.
 *
 * The reverse index consists of a number of sections, that I call sub-sections
 * here.  The sub-sections are all lists of record numbers in other sections
 * in the MDR that contain records belonging to more than one map.
 *
 * Using the index you could extract records that belong to an individual map
 * from other MDR sections without having to go through them all and check
 * which map they belong to.
 *
 * The subsections are as follows:
 *
 * sub1 points into MDR 11 (POIs)
 * sub2 points into MDR 10 (POI types)
 * sub3 points into MDR 7 (street names)
 * sub4 points into MDR 5 (cities)
 * sub5 points into MDR 6 (zips)
 * sub6 points into MDR 20
 * sub7 points into MDR 21
 * sub8 points into MDR 22
 *
 * @author Steve Ratcliffe
 */
public class Mdr1 extends MdrSection implements HasHeaderFlags {
	private final List<Mdr1Record> maps = new ArrayList<Mdr1Record>();

	public Mdr1(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * Add a map.  Create an MDR1 record for it and also allocate its reverse
	 * index if this is not for a device.
	 * @param mapNumber The map index number.
	 */
	public void addMap(int mapNumber) {
		Mdr1Record rec = new Mdr1Record(mapNumber, getConfig());
		maps.add(rec);

		if (!isForDevice()) {
			Mdr1MapIndex mapIndex = new Mdr1MapIndex();
			rec.setMdrMapIndex(mapIndex);
		}
	}

	/**
	 * The maps must be sorted in numerical order.
	 */
	public void finish() {
		Collections.sort(maps, new Comparator<Mdr1Record>() {
			public int compare(Mdr1Record o1, Mdr1Record o2) {
				if (o1.getMapNumber() == o2.getMapNumber())
					return 0;
				else if (o1.getMapNumber() < o2.getMapNumber())
					return -1;
				else
					return 1;
			}
		});
	}

	public void writeSubSections(ImgFileWriter writer) {
		if (getConfig().isForDevice())
			return;
		for (Mdr1Record rec : maps) {
			rec.setIndexOffset(writer.position());
			Mdr1MapIndex mapIndex = rec.getMdrMapIndex();
			mapIndex.writeSubSection(writer);
		}
	}

	/**
	 * This is written right at the end after we know all the offsets in
	 * the MDR 1 record.
	 * @param writer The mdr 1 records are written out to this writer.
	 */
	public void writeSectData(ImgFileWriter writer) {
		for (Mdr1Record rec : maps)
			rec.write(writer);
	}

	public int getItemSize() {
		return isForDevice()? 4: 8;
	}

	public void setStartPosition(int sectionNumber) {
		for (Mdr1Record mi : maps)
			mi.getMdrMapIndex().startSection(sectionNumber);
	}

	public void setEndPosition(int sectionNumber) {
		for (Mdr1Record mi : maps) {
			mi.getMdrMapIndex().endSection(sectionNumber);
	}
}

	public void setPointerSize(int sectionSize, int recordSize) {
		for (Mdr1Record mi : maps) {
			Mdr1MapIndex mapIndex = mi.getMdrMapIndex();
			mapIndex.setPointerSize(sectionSize, recordSize);
		}
	}

	public void addPointer(int mapNumber, int recordNumber) {
		Mdr1MapIndex mi = maps.get(mapNumber - 1).getMdrMapIndex();
		mi.addPointer(recordNumber);
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	protected int numberOfItems() {
		return maps.size();
	}

	public int getExtraValue() {
		return 0x01;
	}
}

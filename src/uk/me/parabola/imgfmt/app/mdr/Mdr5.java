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
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * Section containing cities.
 *
 * We need: map number, city index in map, offset in LBL, flags
 * and pointer into MDR 15 for the string name.
 * 
 * @author Steve Ratcliffe
 */
public class Mdr5 extends MdrMapSection {
	private final List<Mdr5Record> cities = new ArrayList<Mdr5Record>();
	private int[] mdr20;
	private int maxCityIndex;
	private int localCitySize;

	public Mdr5(MdrConfig config) {
		setConfig(config);
	}

	public void addCity(Mdr5Record record) {
		assert record.getMapIndex() != 0;
		cities.add(record);
		if (record.getCityIndex() > maxCityIndex)
			maxCityIndex = record.getCityIndex();
	}

	/**
	 * Called after all cities to sort and number them.
	 */
	public void finish() {
		localCitySize = numberToPointerSize(maxCityIndex + 1);

		List<SortKey<Mdr5Record>> sortKeys = new ArrayList<SortKey<Mdr5Record>>(cities.size());
		Sort sort = getConfig().getSort();
		for (Mdr5Record m : cities) {
			// Sorted also by map number, city index
			int second = (m.getMapIndex() << 16) + m.getCityIndex();
			SortKey<Mdr5Record> sortKey = sort.createSortKey(m, m.getName(), second);
			sortKeys.add(sortKey);
		}
		Collections.sort(sortKeys);

		cities.clear();
		int count = 0;
		int lastMapId = 0;
		String lastName = null;
		for (SortKey<Mdr5Record> key : sortKeys) {
			Mdr5Record c = key.getObject();
			if (c.getMapIndex() != lastMapId || !c.getName().equals(lastName)) {
				count++;
				c.setGlobalCityIndex(count);
				cities.add(c);

				lastName = c.getName();
				lastMapId = c.getMapIndex();
			} else {
				c.setGlobalCityIndex(count);
			}
		}
	}

	public void writeSectData(ImgFileWriter writer) {
		String lastName = null;

		int size20 = getSizes().getMdr20Size();
		for (Mdr5Record city : cities) {
			int gci = city.getGlobalCityIndex();
			addIndexPointer(city.getMapIndex(), gci);

			// Work out if the name is the same as the previous one and set
			// the flag if so.
			int flag = 0;
			int mapIndex = city.getMapIndex();
			int region = city.getRegionIndex();

			// Set flag only for a name that is different to the previous one
			if (lastName == null || !lastName.equals(city.getName())) {
				flag = 0x800000;
				lastName = city.getName();
			} else {
				// If this is a repeat name then the mdr20 value must
				// also be repeated.
				mdr20[gci] = mdr20[gci - 1];
			}

			// Write out the record
			putMapIndex(writer, mapIndex);
			putLocalCityIndex(writer, city.getCityIndex());
			writer.put3(flag | city.getLblOffset());
			writer.putChar((char) region);
			putStringOffset(writer, city.getStringOffset());
			putN(writer, size20, mdr20[gci]);
		}
	}

	/**
	 * Put the map city index.  This is the index within the individual map
	 * and not the global city index used in mdr11.
	 */
	private void putLocalCityIndex(ImgFileWriter writer, int cityIndex) {
		if (localCitySize == 2) // 3 probably not possible in actual maps.
			writer.putChar((char) cityIndex);
		else
			writer.put((byte) cityIndex);
	}

	/**
	 * Base size of 8, plus enough bytes to represent the map number
	 * and the city number.
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize()
				+ localCitySize
				+ 5
				+ sizes.getMdr20Size()
				+ sizes.getStrOffSize();
	}

	public int getNumberOfItems() {
		return cities.size();
	}

	/**
	 * Known structure:
	 * bits 0-1: size of local city index - 1 (all values appear to work)
	 * bit  3: has region
	 * bit  4: has string
	 * @return The value to be placed in the header.
	 */
	public int getExtraValue() {
		// 0x4 is region and we always set it
		int val = 0x04 | (localCitySize - 1);
		// String offset is only included for a mapsource index.
		if (!isForDevice())
			val |= 0x08;
		val |= 0x10;
		val |= 0x100; // mdr20 present
		return val;
	}

	public List<Mdr5Record> getCities() {
		return cities;
	}

	public void setMdr20(int[] mdr20) {
		this.mdr20 = mdr20;
	}
}

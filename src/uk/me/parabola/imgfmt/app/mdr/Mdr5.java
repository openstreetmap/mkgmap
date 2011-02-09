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
		String lastName = "";
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
		String lastName = "";

		int size20 = getSizes().getMdr20Size();
		for (Mdr5Record city : cities) {
			addIndexPointer(city.getMapIndex(), city.getGlobalCityIndex());

			// Work out if the name is the same as the previous one and set
			// the flag if so.
			int flag = 0x800000;
			int mapIndex = city.getMapIndex();
			int region = city.getRegionIndex();

			// Set flag only for a name that is different to the previous one
			if (lastName.equals(city.getName()))
				flag = 0;

			lastName = city.getName();

			fixMdr20(city);

			// Write out the record
			putMapIndex(writer, mapIndex);
			putLocalCityIndex(writer, city.getCityIndex());
			writer.put3(flag | city.getLblOffset());
			writer.putChar((char) region);
			putStringOffset(writer, city.getStringOffset());
			int mdr20index = mdr20[city.getGlobalCityIndex()];
			assert mdr20index != 0 : "before write";
			putN(writer, size20, mdr20index);
		}
	}

	/**
	 * Now check that there are no gaps in the mdr20 sequence. This will happen if a city has no streets.
	 * We have to find the next non-zero record and fill in all the zero records with that value.
	 */
	private void fixMdr20(Mdr5Record city) {
		int gci = city.getGlobalCityIndex();
		int mdr20Index = mdr20[gci];
		if (mdr20Index == 0) {
			// Step forward until we find the next non zero value
			assert mdr20[gci] == 0;
			for (int i = gci; ; i++) {
				int next = mdr20[i];

				if (next != 0) {
					// Now we have found it, fill in all the zero entries
					for (int j = gci; j < i; j++) {
						assert mdr20[j] == 0 : "should only be setting zero entries";
						mdr20[j] = next;
					}
					break;
				}
			}
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

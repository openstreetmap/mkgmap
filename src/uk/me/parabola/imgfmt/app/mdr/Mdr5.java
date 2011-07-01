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
import uk.me.parabola.imgfmt.app.srt.CombinedSortKey;
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
	private final List<Mdr5Record> allCities = new ArrayList<Mdr5Record>();
	private final List<Mdr5Record> cities = new ArrayList<Mdr5Record>();
	private int maxCityIndex;
	private int localCitySize;

	public Mdr5(MdrConfig config) {
		setConfig(config);
	}

	public void addCity(Mdr5Record record) {
		assert record.getMapIndex() != 0;
		allCities.add(record);
		if (record.getCityIndex() > maxCityIndex)
			maxCityIndex = record.getCityIndex();
	}

	/**
	 * Called after all cities to sort and number them.
	 */
	public void finish() {
		localCitySize = numberToPointerSize(maxCityIndex + 1);

		List<SortKey<Mdr5Record>> sortKeys = new ArrayList<SortKey<Mdr5Record>>(allCities.size());
		Sort sort = getConfig().getSort();
		for (Mdr5Record m : allCities) {
			// Sorted also by map number, city index
			if (m.getName() == null)
				continue;
			SortKey<Mdr5Record> sortKey = sort.createSortKey(m, m.getName(), m.getMapIndex());
			sortKey = new CombinedSortKey<Mdr5Record>(sortKey, m.getCityIndex(), m.getRegionIndex());
			sortKeys.add(sortKey);
		}
		Collections.sort(sortKeys);

		int count = 0;
		Mdr5Record lastCity = null;
		for (SortKey<Mdr5Record> key : sortKeys) {
			Mdr5Record c = key.getObject();
			if (c.isSameCity(lastCity)) {
				c.setGlobalCityIndex(count);
			} else {
				count++;
				c.setGlobalCityIndex(count);
				cities.add(c);

				lastCity = c;
			}
		}
	}

	public void writeSectData(ImgFileWriter writer) {
		int size20 = getSizes().getMdr20Size();
		Mdr5Record lastCity = null;
		for (Mdr5Record city : cities) {
			int gci = city.getGlobalCityIndex();
			addIndexPointer(city.getMapIndex(), gci);

			// Work out if the name is the same as the previous one and set
			// the flag if so.
			int flag = 0;
			int mapIndex = city.getMapIndex();
			int region = city.getRegionIndex();

			// Set the no-repeat flag if the name is the different
			if (lastCity == null ||
					(!city.getName().equals(lastCity.getName())
							|| city.getMdr20() != lastCity.getMdr20()))
			{
				flag = 0x800000;
				lastCity = city;
			}

			// Write out the record
			putMapIndex(writer, mapIndex);
			putLocalCityIndex(writer, city.getCityIndex());
			writer.put3(flag | city.getLblOffset());
			writer.putChar((char) region);
			putStringOffset(writer, city.getStringOffset());
			putN(writer, size20, city.getMdr20());
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
		return Collections.unmodifiableList(allCities);
	}
}

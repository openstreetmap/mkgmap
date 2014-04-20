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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.srt.MultiSortKey;
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
	private List<Mdr5Record> allCities = new ArrayList<>();
	private List<Mdr5Record> cities = new ArrayList<>();
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
	public void preWriteImpl() {
		localCitySize = numberToPointerSize(maxCityIndex + 1);

		List<SortKey<Mdr5Record>> sortKeys = new ArrayList<>(allCities.size());
		Sort sort = getConfig().getSort();
		for (Mdr5Record m : allCities) {
			if (m.getName() == null)
				continue;

			// Sort by city name, region name, country name and map index.
			SortKey<Mdr5Record> sortKey = sort.createSortKey(m, m.getName());
			SortKey<Mdr5Record> regionKey = sort.createSortKey(null, m.getRegionName());
			SortKey<Mdr5Record> countryKey = sort.createSortKey(null, m.getCountryName(), m.getMapIndex());
			sortKey = new MultiSortKey<>(sortKey, regionKey, countryKey);
			sortKeys.add(sortKey);
		}
		Collections.sort(sortKeys);

		Collator collator = getConfig().getSort().getCollator();

		int count = 0;
		Mdr5Record lastCity = null;

		// We need a common area to save the mdr20 values, since there can be multiple
		// city records with the same global city index
		int[] mdr20s = new int[sortKeys.size()+1];
		int mdr20count = 0;

		for (SortKey<Mdr5Record> key : sortKeys) {
			Mdr5Record c = key.getObject();
			c.setMdr20set(mdr20s);

			if (!c.isSameByName(collator, lastCity))
				mdr20count++;
			c.setMdr20Index(mdr20count);

			if (c.isSameByMapAndName(collator, lastCity)) {
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
		boolean hasString = hasFlag(0x8);
		boolean hasRegion = hasFlag(0x4);
		Collator collator = getConfig().getSort().getCollator();
		for (Mdr5Record city : cities) {
			int gci = city.getGlobalCityIndex();
			addIndexPointer(city.getMapIndex(), gci);

			// Work out if the name is the same as the previous one and set
			// the flag if so.
			int flag = 0;
			int mapIndex = city.getMapIndex();
			int region = city.getRegionIndex();

			// Set the no-repeat flag if the name/region is different
			if (!city.isSameByName(collator, lastCity)) {
				flag = 0x800000;
				lastCity = city;
			}

			// Write out the record
			putMapIndex(writer, mapIndex);
			putLocalCityIndex(writer, city.getCityIndex());
			writer.put3(flag | city.getLblOffset());
			if (hasRegion)
				writer.putChar((char) region);
			if (hasString)
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
		int size = sizes.getMapSize()
				+ localCitySize
				+ 3
				+ sizes.getMdr20Size();
		if (hasFlag(0x4))
			size += 2;
		if (hasFlag(0x8))
			size += sizes.getStrOffSize();
		return size;
	}

	protected int numberOfItems() {
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
		int val = (localCitySize - 1);
		// String offset is only included for a mapsource index.
		if (isForDevice()) {
			val |= 0x40; // not known, probably refers to mdr17.
		} else {
			val |= 0x04;  // region
			val |= 0x08; // string
		}
		val |= 0x10;
		val |= 0x100; // mdr20 present
		return val;
	}

	protected void releaseMemory() {
		allCities = null;
		cities = null;
	}

	/**
	 * Get a list of all the cities, including duplicate named ones.
	 * @return All cities.
	 */
	public List<Mdr5Record> getCities() {
		return Collections.unmodifiableList(allCities);
	}

	public List<Mdr5Record> getSortedCities() {
		return Collections.unmodifiableList(cities);
	}

	public void relabelMaps(Mdr1 maps) {
		relabel(maps, cities);
	}
}

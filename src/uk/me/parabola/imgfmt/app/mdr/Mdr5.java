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
	private int maxIndex;

	public Mdr5(MdrConfig config) {
		setConfig(config);
	}

	public void addCity(int mapIndex, Mdr5Record record, int lblOff, String name, int strOff) {
		record.setMapIndex(mapIndex);
		record.setLblOffset(lblOff);
		record.setName(name);
		record.setStringOffset(strOff);
		cities.add(record);
		if (record.getCityIndex() > maxIndex)
			maxIndex = record.getCityIndex();
	}

	/**
	 * Called after all cities to sort and number them.
	 */
	public void finishCities() {
		Collections.sort(cities);

		int count = 1;
		for (Mdr5Record c : cities)
			c.setGlobalCityIndex(count++);
	}

	public void writeSectData(ImgFileWriter writer) {
		int lastMap = 0;
		int lastName = 0;

		for (Mdr5Record city : cities) {
			addIndexPointer(city.getMapIndex(), city.getGlobalCityIndex());

			// Work out if the name is the same as the previous one and set
			// the flag if so.
			int flag = 0x800000;
			int mapIndex = city.getMapIndex();
			if (lastMap == mapIndex && lastName == city.getLblOffset())
				flag = 0;
			lastMap = mapIndex;

			// Write out the record
			lastName = city.getLblOffset();
			putMapIndex(writer, mapIndex);
			putLocalCityIndex(writer, city.getCityIndex());
			writer.put3(flag | city.getLblOffset());
			writer.putChar((char) city.getRegion());
			putStringOffset(writer, city.getStringOffset());
		}
	}

	/**
	 * Put the map city index.  This is the index within the individual map
	 * and not the global city index used in mdr11.
	 */
	private void putLocalCityIndex(ImgFileWriter writer, int cityIndex) {
		if (maxIndex > 256)
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
		int localCitySize = (maxIndex > 255)? 2: 1;
		return sizes.getMapSize() + localCitySize + 5 + sizes.getStrOffSize();
	}

	public int getNumberOfItems() {
		return cities.size();
	}

	/**
	 * Get the size of an integer that is sufficient to store a record number
	 * from this section.
	 * @return A number between 1 and 4 giving the number of bytes required
	 * to store the largest record number in this section.
	 */
	public int getPointerSize() {
		return numberToPointerSize(cities.size() << 1);
	}
}

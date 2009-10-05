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

	public void writeSectData(ImgFileWriter writer) {
		//Collections.sort(cities); TODO sort, but also need to fix allocation of city to points first.

		int lastMap = 0;
		int lastName = 0;

		int recordNumber = 1;
		for (Mdr5Record city : cities) {
			addPointer(city.getMapIndex(), recordNumber);

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
			putCityIndex(writer, city.getCityIndex());
			writer.put3(flag | city.getLblOffset());
			writer.putChar((char) 1); // TODO still don't know what this is
			writer.put3(city.getStringOffset());

			recordNumber++;
		}
	}

	public void addCity(int mapIndex, int cityIndex, int lblOff, String name, int strOff) {
		Mdr5Record rec = new Mdr5Record();
		rec.setMapIndex(mapIndex);
		rec.setCityIndex(cityIndex);
		rec.setLblOffset(lblOff);
		rec.setName(name);
		rec.setStringOffset(strOff);
		cities.add(rec);
		if (cityIndex > maxIndex)
			maxIndex = cityIndex;
	}

	/**
	 * Base size of 8, plus enough bytes to represent the map number
	 * and the city number.
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		return 9 + ((maxIndex > 256)? 2: 1);
	}

	public int getNumberOfItems() {
		return cities.size();
	}

	private void putCityIndex(ImgFileWriter writer, int cityIndex) {
		if (maxIndex > 256)
			writer.putChar((char) cityIndex);
		else
			writer.put((byte) cityIndex);
	}
}

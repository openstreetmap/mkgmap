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
public class Mdr5 extends MdrSection {

	private final List<Mdr5Record> cities = new ArrayList<Mdr5Record>();

	public Mdr5(MdrConfig config) {
		setConfig(config);
	}

	public void writeSectData(ImgFileWriter writer) {
		for (Mdr5Record city : cities) {
			putMapIndex(writer, city.getMapIndex());
			putCityIndex(writer, city.getCityIndex());
			writer.put3(0x800000 | city.getLblOffset());
			writer.putChar((char) 1);
			writer.put3(city.getStringOffset());
		}
	}

	private void putCityIndex(ImgFileWriter writer, int cityIndex) {
		writer.putChar((char) cityIndex);
	}

	/**
	 * Base size of 8, plus enough bytes to represent the map number
	 * and the city number.
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		return 8 + 2;
	}

	public void addCity(int mapIndex, int cityIndex, int lblOff, int strOff) {
		Mdr5Record rec = new Mdr5Record(getConfig());
		rec.setMapIndex(mapIndex);
		rec.setCityIndex(cityIndex);
		rec.setStringOffset(strOff);
		cities.add(rec);
	}
}

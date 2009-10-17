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
 * Holds all the regions for each map.
 *
 * @author Steve Ratcliffe
 */
public class Mdr13 extends MdrSection {
	private final List<Mdr13Record> regions = new ArrayList<Mdr13Record>();

	public Mdr13(MdrConfig config) {
		setConfig(config);
	}

	public void addRegion(int mapIndex, int countryIndex, int regionIndex, int strOff) {
		Mdr13Record rec = new Mdr13Record();
		rec.setMapIndex(mapIndex);
		rec.setCountryIndex(countryIndex);
		rec.setRegionIndex(regionIndex);
		rec.setStrOffset(strOff);
		regions.add(rec);
	}
	public void writeSectData(ImgFileWriter writer) {
		for (Mdr13Record region : regions) {
			putMapIndex(writer, region.getMapIndex());
			writer.putChar((char) region.getRegionIndex());
			writer.putChar((char) region.getCountryIndex());
			writer.put3(region.getStrOffset());
		}
	}

	public int getItemSize() {
		return 7 + getSizes().getMapSize();
	}
}

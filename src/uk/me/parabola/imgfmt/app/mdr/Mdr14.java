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
 * The countries that occur in each map.
 *
 * @author Steve Ratcliffe
 */
public class Mdr14 extends MdrSection {
	private final List<Mdr14Record> countries = new ArrayList<Mdr14Record>();

	public Mdr14(MdrConfig config) {
		setConfig(config);
	}

	public void writeSectData(ImgFileWriter writer) {
		Collections.sort(countries);
		
		for (Mdr14Record country : countries) {
			putMapIndex(writer, country.getMapIndex());
			writer.putChar((char) country.getCountryIndex());
			putStringOffset(writer, country.getStrOff());
		}
	}

	public void addCountry(int mapIndex, int countryIndex, int strOff) {
		Mdr14Record c = new Mdr14Record();
		c.setMapIndex(mapIndex);
		c.setCountryIndex(countryIndex);
		c.setStrOff(strOff);
		countries.add(c);
	}

	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize() + 2 + sizes.getStrOffSize();
	}
}

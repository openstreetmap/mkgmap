/*
 * Copyright (C) 2011.
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
 * Cities sorted by country.
 *
 * @author Steve Ratcliffe
 */
public class Mdr25 extends MdrSection {
	private final List<Mdr5Record> cities = new ArrayList<Mdr5Record>();

	public Mdr25(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * Cities are sorted by country and then by the mdr5 city record number.
	 * @param list The complete list of cities from mdr5.
	 */
	public void sortCities(List<Mdr5Record> list) {
		Sort sort = getConfig().getSort();

		List<SortKey<Mdr5Record>> keys = new ArrayList<SortKey<Mdr5Record>>();
		for (Mdr5Record c : list) {
			SortKey<Mdr5Record> key = sort.createSortKey(c, c.getMdrCountry().getName(), c.getGlobalCityIndex());
			keys.add(key);
		}

		Collections.sort(keys);

		String lastName = null;
		Mdr5Record lastCity = null;
		int record = 0;
		for (SortKey<Mdr5Record> key : keys) {
			record++;
			Mdr5Record city = key.getObject();

			// Record in the 29 index if there is one for this record
			Mdr14Record mdrCountry = city.getMdrCountry();
			Mdr29Record mdr29 = mdrCountry.getMdr29();
			String name = mdr29.getName();
			assert mdrCountry.getName().equals(name);
			if (!name.equals(lastName)) {
				mdr29.setMdr25(record);
				lastName = name;
			}

			if (lastCity == null || !city.getName().equals(lastCity.getName())) {
				cities.add(city);
				lastCity = city;
			}
		}
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		int size = getItemSize();
		for (Mdr5Record city : cities) {
			putN(writer, size,  city.getGlobalCityIndex());
		}
	}

	/**
	 * One field pointing to a city. Not flagged.
	 */
	public int getItemSize() {
		return getSizes().getCitySize();
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return cities.size();
	}
}

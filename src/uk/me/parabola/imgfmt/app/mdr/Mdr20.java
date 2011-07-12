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

import uk.me.parabola.imgfmt.app.srt.IntegerSortKey;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * This is a list of streets that belong to each city.
 *
 * It is sorted with each group of streets that belong to a city in the same
 * order as the cities, within each group the sort is by street id in mdr7.
 *
 * Streets that do not have an associated city are not included.
 *
 * There is a subsection in the mdr1 reverse index for this section, however
 * the map index is not saved as part of this record.
 *
 * @author Steve Ratcliffe
 */
public class Mdr20 extends Mdr2x {

	public Mdr20(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * We need to sort the streets by the name of the city. Within a city
	 * group the streets are ordered by their own index.
	 *
	 * Also have to set the record number of the first record in this section
	 * on the city.
	 *
	 * @param inStreets The list of streets from mdr7.
	 */
	public void buildFromStreets(List<Mdr7Record> inStreets, Mdr5 mdr5) {

		List<SortKey<Mdr7Record>> keys = new ArrayList<SortKey<Mdr7Record>>();
		for (Mdr7Record s : inStreets) {
			Mdr5Record city = s.getCity();
			if (city == null)
				continue;

			String cityName = city.getName();
			if (cityName == null)
				continue;

			// We are sorting the streets, but we are sorting primarily on the
			// city name associated with the street, then on the street name.
			// Since the cities are already sorted, we can use the city index.
			SortKey<Mdr7Record> key = new IntegerSortKey<Mdr7Record>(s, city.getGlobalCityIndex(), s.getIndex());
			keys.add(key);
		}
		Collections.sort(keys);

		int[] mdr20 = new int[mdr5.getNumberOfItems() + 2];

		String lastName = null;
		String lastCityName = null;
		int record = 0;
		int cityRecord = 0;
		for (SortKey<Mdr7Record> key : keys) {
			Mdr7Record street = key.getObject();

			String name = street.getName();
			Mdr5Record city = street.getCity();
			String cityName = city.getName();
			int gci = city.getGlobalCityIndex();

			if (!name.equals(lastName)) {
				record++;

				streets.add(street);
				lastName = name;
			}

			if (cityName.equals(lastCityName)) {
				mdr20[gci] = cityRecord;
			} else {
				cityRecord = record;
				mdr20[gci] = cityRecord;
				lastCityName = cityName;
			}
		}

		mdr20[mdr5.getNumberOfItems()+1] = cityRecord;
		mdr5.setMdr20(mdr20);
	}

	/**
	 * Unknown.
	 */
	public int getExtraValue() {
		return 0x8800;
	}
}

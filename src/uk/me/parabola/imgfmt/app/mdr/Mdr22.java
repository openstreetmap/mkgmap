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

import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * Index of streets by country.
 *
 * There is no pointer from the country section into this like there is with
 * cities.
 * 
 * @author Steve Ratcliffe
 */
public class Mdr22 extends Mdr2x {

	public Mdr22(MdrConfig config) {
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
	public void buildFromStreets(List<Mdr7Record> inStreets) {
		Sort sort = getConfig().getSort();

		List<SortKey<Mdr7Record>> keys = new ArrayList<SortKey<Mdr7Record>>();
		for (Mdr7Record s : inStreets) {
			Mdr5Record city = s.getCity();
			if (city == null) continue;

			Mdr14Record country = city.getMdrCountry();
			assert country != null;

			String name = country.getName();
			assert name != null;
			
			// We are sorting the streets, but we are sorting primarily on the
			// country name associated with the street.
			SortKey<Mdr7Record> key = sort.createSortKey(s, name, s.getIndex());
			keys.add(key);
		}
		Collections.sort(keys);

		int record = 0;
		String lastName = "";
		for (SortKey<Mdr7Record> key : keys) {
			record++;
			Mdr7Record street = key.getObject();

			// Include in the mdr29 index if we have one for this record.
			Mdr14Record mdrCountry = street.getCity().getMdrCountry();
			Mdr29Record mdr29 = mdrCountry.getMdr29();
			String name = mdrCountry.getName();
			if (!name.equals(lastName)) {
				mdr29.setMdr22(record);
				lastName = name;
			}

			streets.add(street);
		}
	}

	/**
	 * Unknown flag
	 */
	public int getExtraValue() {
		return 0x11000;
	}
}

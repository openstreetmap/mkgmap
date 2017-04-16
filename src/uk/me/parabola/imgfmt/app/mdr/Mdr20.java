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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
	 * @param inStreets The list of streets from mdr7, must have Mdr7.index set.
	 */
	public void buildFromStreets(List<Mdr7Record> inStreets) {
		ArrayList<Mdr7Record> sorted = new ArrayList<>(inStreets);
		Collections.sort(sorted, new Comparator<Mdr7Record>() {
			public int compare(Mdr7Record o1, Mdr7Record o2) {
				int d = Integer.compare(o1.getCity().getGlobalCityIndex(), o2.getCity().getGlobalCityIndex());
				if (d != 0)
					return d;
				return Integer.compare(o1.getIndex(), o2.getIndex());
			}
		});
		
		Collator collator = getConfig().getSort().getCollator();
		Mdr5Record lastCity = null;
		int record = 0;
		int cityRecord = 1;
		int lastIndex = -1;

		for (Mdr7Record street: sorted) {
			Mdr5Record city = street.getCity();

			// Only save a single copy of each street name.
			if (lastIndex != street.getIndex())
			{
				record++;
				streets.add(street);
				lastIndex = street.getIndex();

				boolean citySameByName = city.isSameByName(collator, lastCity);
				// The mdr20 value changes for each new city name
				if (citySameByName) {
					assert cityRecord!=0;
					city.setMdr20(cityRecord);
				} else {
					// New city name, this marks the start of a new section in mdr20
					assert cityRecord != 0;
					cityRecord = record;
					city.setMdr20(cityRecord);
					lastCity = city;
				}
			}
		}
	}

	/**
	 * Two streets are in the same group if they have the same mdr20 id.
	 */
	protected boolean sameGroup(Mdr7Record street1, Mdr7Record street2) {
		if (street2 != null && street1.getCity().getMdr20() == street2.getCity().getMdr20())
			return true;
		return false;
	}

	/**
	 * Unknown.
	 */
	public int getExtraValue() {
		return isForDevice() ? 0xe : 0x8800;
	}
}

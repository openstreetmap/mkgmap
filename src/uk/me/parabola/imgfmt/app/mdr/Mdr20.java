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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.srt.MultiSortKey;
import uk.me.parabola.imgfmt.app.srt.Sort;
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
	 * @param inStreets The list of streets from mdr7, must have Mdr7.index set.
	 */
	public void buildFromStreets(List<Mdr7Record> inStreets) {
		Sort sort = getConfig().getSort();

		// Use a key cache because there are a large number of street names but a much smaller number
		// of city, region and country names. Therefore we can reuse the memory needed for the keys
		// most of the time, particularly for the country and region names.
		Map<String, byte[]> cache = new HashMap<>();

		List<SortKey<Mdr7Record>> keys = new ArrayList<>();
		for (Mdr7Record s : inStreets) {
			Mdr5Record city = s.getCity();
			if (city == null)
				continue;

			String name = city.getName();
			if (name == null || name.isEmpty())
				assert false;

			// We are sorting the streets, but we are sorting primarily on the
			// city name associated with the street, then on the street name.
			SortKey<Mdr7Record> cityKey = sort.createSortKey(s, city.getName(), 0, cache);
			SortKey<Mdr7Record> regionKey = sort.createSortKey(null, city.getRegionName(), 0, cache);
			// The streets are already sorted, with the getIndex() method revealing the sort order
			SortKey<Mdr7Record> countryStreetKey = sort.createSortKey(null, city.getCountryName(), s.getIndex(),
					cache);

			// Combine all together so we can sort on it.
			SortKey<Mdr7Record> key = new MultiSortKey<>(cityKey, regionKey, countryStreetKey);

			keys.add(key);
		}
		Collections.sort(keys);

		Collator collator = getConfig().getSort().getCollator();

		String lastName = null;
		Mdr5Record lastCity = null;
		int record = 0;
		int cityRecord = 0;
		int lastMapNumber = 0;

		for (SortKey<Mdr7Record> key : keys) {
			Mdr7Record street = key.getObject();

			String name = street.getName();
			Mdr5Record city = street.getCity();

			boolean citySameByName = city.isSameByName(collator, lastCity);

			int mapNumber = city.getMapIndex();

			// Only save a single copy of each street name.
			if (!citySameByName || mapNumber != lastMapNumber || lastName == null || collator.compare(name, lastName) != 0) {
				record++;

				streets.add(street);
				lastName = name;
			}

			// The mdr20 value changes for each new city name
			if (citySameByName) {
				city.setMdr20(cityRecord);
			} else {
				// New city name, this marks the start of a new section in mdr20
				cityRecord = record;
				city.setMdr20(cityRecord);
				lastCity = city;
			}
			lastMapNumber = mapNumber;
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
		return isForDevice() ? 0xa : 0x8800;
	}
}

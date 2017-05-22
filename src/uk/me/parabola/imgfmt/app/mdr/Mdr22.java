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
import java.util.Comparator;
import java.util.List;

/**
 * Index of streets by country.
 *
 * There is no pointer from the country section into this like there is with
 * cities.
 * 
 * @author Steve Ratcliffe
 * @author Gerd Petermann
 */
public class Mdr22 extends Mdr2x {

	public Mdr22(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * We need to sort the streets by the name of the country. Within a country
	 * the streets are ordered by their own index.
	 *
	 * Also have to set the record number of the first record in this section
	 * on the country.
	 *
	 * @param inStreets The list of streets from mdr7.
	 */
	public void buildFromStreets(List<Mdr7Record> inStreets) {
		ArrayList<Mdr7Record> sorted = new ArrayList<>(inStreets.size());
		for (Mdr7Record street : inStreets) {
			if (street.getCity() != null) {
				assert street.getCity().getCountryName() != null;
				sorted.add(street);
			}
		}
		Collections.sort(sorted, new Comparator<Mdr7Record>() {
			public int compare(Mdr7Record o1, Mdr7Record o2) {
				int d = Integer.compare(o1.getCity().getMdr22SortPos(), o2.getCity().getMdr22SortPos());
				if (d != 0)
					return d;
				return Integer.compare(o1.getIndex(), o2.getIndex());
			}
		});


		int lastIndex = -1;
		int record = 0;
		for (Mdr7Record street : sorted) {
			if (street.getIndex() != lastIndex) {
				record++;
				streets.add(street);

				// Include in the mdr29 index if we have one for this record.
				Mdr14Record mdrCountry = street.getCity().getMdrCountry();
				if (mdrCountry != null) {
					Mdr29Record mdr29 = mdrCountry.getMdr29();
					mdr29.setMdr22(record);
				}

				lastIndex = street.getIndex();
			}
		}
		return;
	}

	protected boolean sameGroup(Mdr7Record street1, Mdr7Record street2) {
		return true;
	}

	public List<Mdr7Record> getStreets() {
		return Collections.unmodifiableList(streets);
	}

	/**
	 * Unknown flag
	 */
	public int getExtraValue() {
		int magic;
		if (isForDevice()) {
			magic = 0x0000e;
			if (!getConfig().getSort().isMulti())
				magic |= 0xc0000; // used to be 0x6000, maybe two different flags ? 
		} else {
			magic = 0x11000;
		}
		return magic;
	}
}

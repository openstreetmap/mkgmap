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
 * This section contains the streets sorted by region.
 * There is no pointer from region, unlike in the case with cities.
 * 
 * @author Steve Ratcliffe
 */
public class Mdr21 extends Mdr2x {

	public Mdr21(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * We need to sort the streets by the name of their region. Within a region
	 * group the streets are ordered by their own index.
	 *
	 * @param inStreets The list of streets from mdr7.
	 */
	public void buildFromStreets(List<Mdr7Record> inStreets) {
		Sort sort = getConfig().getSort();

		List<SortKey<Mdr7Record>> keys = new ArrayList<SortKey<Mdr7Record>>();
		for (Mdr7Record s : inStreets) {
			Mdr5Record city = s.getCity();
			if (city == null) continue;

			Mdr13Record region = city.getMdrRegion();
			if (region == null) continue;

			String name = region.getName();
			if (name != null) {
			
				// We are sorting the streets, but we are sorting primarily on the
				// region name associated with the street.
				SortKey<Mdr7Record> key = sort.createSortKey(s, name, s.getIndex());
				keys.add(key);
			}
		}

		Collections.sort(keys);

		int record = 0;
		for (SortKey<Mdr7Record> key : keys) {
			record++;
			Mdr7Record street = key.getObject();
			Mdr13Record mdrRegion = street.getCity().getMdrRegion();
			Mdr28Record mdr28 = mdrRegion.getMdr28();
			if (mdr28 != null) {
				mdr28.setMdr21(record);
			}
			streets.add(street);
		}
	}

	/**
	 * Not known what these flags signify.
	 */
	public int getExtraValue() {
		return 0x11800;
	}
}

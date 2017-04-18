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
 * This section contains the streets sorted by region.
 * There is no pointer from region, unlike in the case with cities.
 * 
 * @author Steve Ratcliffe
 * @author Gerd Petermann
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
		ArrayList<Mdr7Record> sorted = new ArrayList<>(inStreets.size());
		for (Mdr7Record street : inStreets) {
			if (street.getCity() != null && street.getCity().getMdrRegion() != null)
				sorted.add(street);
		}
		Collections.sort(sorted, new Comparator<Mdr7Record>() {
			public int compare(Mdr7Record o1, Mdr7Record o2) {
				int d = Integer.compare(o1.getCity().getMdr21SortPos(), o2.getCity().getMdr21SortPos());
				if (d != 0)
					return d;
				return Integer.compare(o1.getIndex(), o2.getIndex());
			}
		});


		int lastIndex = -1;
		int record = 0;
		for (Mdr7Record street : sorted) {
			if (lastIndex != street.getIndex()) {
				record++;
				streets.add(street);

				Mdr13Record mdrRegion = street.getCity().getMdrRegion();
				if (mdrRegion != null) {
					Mdr28Record mdr28 = mdrRegion.getMdr28();
					mdr28.setMdr21(record);
				}

				lastIndex = street.getIndex();
			}
		}
	}

	protected boolean sameGroup(Mdr7Record street1, Mdr7Record street2) {
		return true;
	}

	/**
	 * Not known what these flags signify.
	 */
	public int getExtraValue() {
		return 0x11800;
	}
}

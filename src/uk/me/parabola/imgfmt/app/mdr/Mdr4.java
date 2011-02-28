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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * POI types.  A simple list of the types that are used?
 * If you have this section, then the ability to select POI categories
 * goes away.
 * 
 * @author Steve Ratcliffe
 */
public class Mdr4 extends MdrSection implements HasHeaderFlags {
	private final Set<Mdr4Record> poiTypes = new HashSet<Mdr4Record>();

	public Mdr4(MdrConfig config) {
		setConfig(config);
	}

	
	public void writeSectData(ImgFileWriter writer) {
		List<Mdr4Record> list = new ArrayList<Mdr4Record>(poiTypes);
		Collections.sort(list);

		for (Mdr4Record r : list) {
			writer.put((byte) r.getType());
			writer.put((byte) r.getUnknown());
			writer.put((byte) r.getSubtype());
		}
	}

	public int getItemSize() {
		return 3;
	}

	public void addType(int type) {
		Mdr4Record r = new Mdr4Record();
		if (type <= 0xff)
			r.setType(type);
		else {
			r.setType((type >> 8) & 0xff);
			r.setSubtype(type & 0xff);
		}
		r.setUnknown(0);

		poiTypes.add(r);
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return poiTypes.size();
	}


	public int getExtraValue() {
		return 0x00;
	}
}

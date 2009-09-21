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
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * POI type with a reference to MDR11.
 * I don't really understand the purpose of this section.
 *
 * @author Steve Ratcliffe
 */
public class Mdr10 extends MdrSection {
	private final List<Mdr10Record> poiTypes = new ArrayList<Mdr10Record>();

	public Mdr10(MdrConfig config) {
		setConfig(config);
	}

	public void addPoiType(int type, Mdr11Record poi) {
		Mdr10Record t = new Mdr10Record();
		t.setType(type);
		t.setMdr11ref(poi);
		poiTypes.add(t);
	}
	
	public void writeSectData(ImgFileWriter writer) {
		for (Mdr10Record t : poiTypes) {
			writer.put((byte) t.getType());
			int offset = t.getMdr11ref().getOffset() + 1;
			offset |= 0x8000; // XXX only if a city (or not a city?)
			writer.putChar((char) offset);
		}
	}

	/**
	 * This does not have a record size.
	 * @return Always zero to indicate that there is not a record size.
	 */
	public int getItemSize() {
		return 0;
	}
}

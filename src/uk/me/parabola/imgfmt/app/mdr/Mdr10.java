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
	private int numberOfPois;

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
			int offset = t.getMdr11ref().getRecordNumber() + 1;

			boolean isCity = t.getMdr11ref().getCityIndex() > 0;
			if (numberOfPois < 0x80) {
				if (isCity)
					offset |= 0x80;
				writer.put((byte) offset);
			} else if (numberOfPois < 0x8000) {
				if (isCity)
					offset |= 0x8000;
				writer.putChar((char) offset);
			} else {
				if (isCity)
					offset |= 0x800000;
				writer.put3(offset);
			}
		}
	}

	/**
	 * This does not have a record size.
	 * @return Always zero to indicate that there is not a record size.
	 */
	public int getItemSize() {
		return 0;
	}

	public void setNumberOfPois(int numberOfPois) {
		this.numberOfPois = numberOfPois;
	}
}

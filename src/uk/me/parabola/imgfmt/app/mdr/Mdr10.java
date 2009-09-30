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

	public void addPoiType(int type, Mdr11Record poi, boolean indexed) {
		Mdr10Record t = new Mdr10Record();
		int t1 = (type>>8) & 0xff;
		int t2 = type & 0xff;

		// This must be set to the subtype, unless there isn't one
		// TODO this may not be totally correct yet as we don't save the fact
		// that there is a subtype anywhere.
		if (t2 == 0)
			t.setType(t1);
		else
			t.setType(t2);

		t.setMdr11ref(poi);
		poiTypes.add(t);
	}
	
	public void writeSectData(ImgFileWriter writer) {
		Collections.sort(poiTypes);

		for (Mdr10Record t : poiTypes) {
			writer.put((byte) t.getType());
			int offset = t.getMdr11ref().getRecordNumber() + 1;

			// Top bit actually represents a non-repeated name.  ie if
			// the bit is not set, then the name is the same as the previous
			// record.
			boolean isRepeated = false; // TODO set this properly
			if (numberOfPois < 0x80) {
				if (!isRepeated)
					offset |= 0x80;
				writer.put((byte) offset);
			} else if (numberOfPois < 0x8000) {
				if (!isRepeated)
					offset |= 0x8000;
				writer.putChar((char) offset);
			} else {
				if (!isRepeated)
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

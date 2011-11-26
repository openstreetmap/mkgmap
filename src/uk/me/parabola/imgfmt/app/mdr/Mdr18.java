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
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * This is an index into 19 showing the start of each new type.
 *
 * Section 19 creates the data for this, we just write it out here.
 *
 * @author Steve Ratcliffe
 */
public class Mdr18 extends MdrSection implements HasHeaderFlags {
	private List<Mdr18Record> poiTypes = new ArrayList<Mdr18Record>();

	public Mdr18(MdrConfig config) {
		setConfig(config);
	}

	public void writeSectData(ImgFileWriter writer) {
		int poiSize = getSizes().getSize(19);
		for (Mdr18Record pt : poiTypes) {
			writer.putChar((char) (pt.getType() | 0x4000));
			putN(writer, poiSize, pt.getRecord());
		}
	}

	public int getItemSize() {
		return 2 + getSizes().getSize(19);
	}

	protected int numberOfItems() {
		return poiTypes.size();
	}

	public void setPoiTypes(List<Mdr18Record> poiTypes) {
		this.poiTypes = poiTypes;
	}

	public int getExtraValue() {
		return 4 + getSizes().getSize(19)-1;
	}
}

/*
 * Copyright (C) 2007 Steve Ratcliffe
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *
 * Author: Steve Ratcliffe
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;

/**
 * Represent a POI index entry
 *
 * @author Mark Burton
 */
public class POIIndex implements Comparable {

	private final String name;
	private final byte poiIndex;
	private final Subdivision group;
	private final byte subType;

	public POIIndex(String name, byte poiIndex, Subdivision group, byte subType) {
		this.name = name;
		this.poiIndex = poiIndex;
		this.group = group;
		this.subType = subType;
	}

	void write(ImgFileWriter writer) {
		writer.put(poiIndex);
		writer.putChar((char)group.getNumber());
		writer.put(subType);
		//System.err.println("Writing POI Index " + name + " group = " + group.getNumber());
	}

	public int compareTo(Object o) {
		return name.compareTo(((POIIndex)o).name);
	}
}

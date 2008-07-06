/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 09-Dec-2006
 */
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;
import uk.me.parabola.imgfmt.app.lbl.POIRecord;

/**
 * Represents a particular point object on a map.  A point has a type (town
 * restaurant etc) and a location as well as a name.
 *
 * A point belongs to a particular subdivision and cannot be interpreted without
 * it as all details are relative to the subdivision.
 *
 * @author Steve Ratcliffe
 */
public class Point extends MapObject {
	private static final Logger log = Logger.getLogger(Point.class);

	// Points can have a subtype, eg for restaurant the subtype might be the
	// kind of food served.
	private int subtype;

	// Points can link to a POIRecord
	private POIRecord poi;

	public Point(Subdivision div) {
		setSubdiv(div);
	}

	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFileWriter file) {
		byte b = (byte) getType();
		file.put(b);
		log.debug("writing point: " + b);

		int off = getLabel().getOffset();
		if (poi != null) {
			off = poi.getOffset();
			off |= 0x400000;
		}
		if (subtype != 0)
			off |= 0x800000;

		file.put3(off);
		file.putChar((char) getDeltaLong());
		file.putChar((char) getDeltaLat());
		if (subtype != 0)
			file.put((byte) subtype);
	}

	public void setSubtype(int subtype) {
		this.subtype = subtype;
	}

	public void setPOIRecord(POIRecord poirecord) {
		this.poi = poirecord;
	}
}

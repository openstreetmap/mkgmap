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
		boolean hasSubtype = false;
		int type = getType();
		byte subtype = 0;
		if (type > 0xff) {
			hasSubtype = true;
			subtype = (byte) type;
			type >>= 8;
		}

		file.put((byte) type);

		int off = getLabel().getOffset();
		if (poi != null) {
			off = poi.getOffset();
			off |= 0x400000;
		}
		if (hasSubtype)
			off |= 0x800000;

		file.put3(off);
		file.putChar((char) getDeltaLong());
		file.putChar((char) getDeltaLat());
		if (hasSubtype)
			file.put(subtype);
	}

	public void setPOIRecord(POIRecord poirecord) {
		this.poi = poirecord;
	}
}

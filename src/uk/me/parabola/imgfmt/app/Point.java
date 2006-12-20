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
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.Utils;

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
	// Points can have a subtype, eg for restaurant the subtype might be the
	// kind of food served.
	private int subtype;

	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFile file) {
		byte b = (byte) getType();
		file.put(b);

		int off = getLabel().getOffset();
		if (subtype != 0)
			off |= 0x800000;

		file.put3(off);
		file.putChar((char) getLongitude());
		file.putChar((char) getLatitude());
		if (subtype != 0)
			file.put((byte) subtype);
	}


	public int getSubtype() {
		return subtype;
	}

	public void setSubtype(int subtype) {
		this.subtype = subtype;
	}
}

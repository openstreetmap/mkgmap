/**
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
 * Author: steve
 * Date: 24-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Coord;

/**
 * A point on the map.  This will appear as a symbol on the map and it will
 * normally be in the list of things that can be seen on the find menu.
 *
 * @author Steve Ratcliffe
 */
public class MapPoint extends MapElement {
	private int subType;
	private Coord location;

	/**
	 * Points have a subtype as well as a type.  This is the value that will
	 * be actually stored in the .img file.
	 *
	 * @return The subtype code.
	 */
	public int getSubType() {
		return subType;
	}

	public void setSubType(int subType) {
		this.subType = subType;
	}

	public Coord getLocation() {
		return location;
	}

	public void setLocation(Coord location) {
		this.location = location;
	}
}

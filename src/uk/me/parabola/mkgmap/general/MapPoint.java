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

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * A point on the map.  This will appear as a symbol on the map and it will
 * normally be in the list of things that can be seen on the find menu.
 *
 * @author Steve Ratcliffe
 */
public class MapPoint extends MapElement {
	private Coord location;

	public MapPoint() {
	}

	private MapPoint(MapPoint p) {
		super(p);
		location = p.location;
	}

	public MapPoint copy() {
		return new MapPoint(this);
	}

	public Coord getLocation() {
		return location;
	}

	public void setLocation(Coord location) {
		this.location = location;
	}

	/**
	 * Get the region that this element covers.
	 *
	 * @return The area that bounds this element.
	 */
	public Area getBounds() {
		return new Area(location.getLatitude(), location.getLongitude(),
				location.getLatitude(), location.getLongitude());
	}

    public boolean isCity() {
		int type = getType();
		return type >= 0x0100 && type <= 0x1100;
    }
}

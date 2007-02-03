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
 * Date: 26-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Area;

/**
 * A map element is a point, line or shape that appears on the map.  This
 * class holds all the common routines that are shared across all elements.
 * 
 * @author Steve Ratcliffe.
 */
public abstract class MapElement {
	private String name;
	private int type;
	private int minLat = Integer.MAX_VALUE, minLong = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE, maxLong = Integer.MIN_VALUE;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * This is the type code that goes in the .img file so that the GPS device
	 * knows what to display.
	 *
	 * @return the type.
	 */
	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	protected abstract Coord getLocation();

	/**
	 * We build up the bounding box of this element by calling this routine.
	 *
	 * @param co The coordinate to add.
	 */
	protected void addToBounds(Coord co) {
		int lat = co.getLatitude();
		if (lat < minLat) {
			minLat = lat;
		} else if (lat > maxLat) {
			maxLat = lat;
		}

		int lon = co.getLongitude();
		if (lon < minLong) {
			minLong = lon;
		} else if (lon > maxLong) {
			maxLong = lon;
		}
	}

	/**
	 * Get the region that this element covers.
	 *
	 * @return The area that bounds this element.
	 */
	public Area getBounds() {
		return new Area(minLat, minLong, maxLat, maxLong);
	}
}

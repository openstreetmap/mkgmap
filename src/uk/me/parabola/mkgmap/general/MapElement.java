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

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * A map element is a point, line or shape that appears on the map.  This
 * class holds all the common routines that are shared across all elements.
 * 
 * @author Steve Ratcliffe.
 */
public abstract class MapElement {
	private String name;
	private int type;
	private int minResolution;

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

	/**
	 * Get the 'location' of the element.  This is the mid point of the bounding
	 * box for the element.  For a point, this will be the coordinates of the
	 * point itself of course.
	 *
	 * @return Co-ordinate of the mid-point of the bounding box of the element.
	 */
	protected abstract Coord getLocation();

	/**
	 * Get the region that this element covers.
	 *
	 * @return The area that bounds this element.
	 */
	public abstract Area getBounds();

	/**
	 * Get the resolutions that an element should be displayed at.
	 * It will return the minimum resolution at which this element should be
	 * displayed at.
	 *
	 * @return The lowest resolution at which the element will be visible.
	 */
	public int getResolution() {
		// This is the new way: the min resolution is set by the reader and we
		// just return it here.
		if (minResolution != 0)
			return minResolution;

		// The old way - there is a built in list of min resolutions based on
		// the element type, this will eventually go.  You can't distinguish
		// between points and lines here either.
		switch (getType()) {
		case 1:
		case 2:
			return 10;
		case 3:
			return 18;
		case 4:
			return 19;
		case 5:
			return 21;
		case 6:
			return 24;
		case 0x14:
		case 0x17:
			return 20;
		case 0x15: // coast, make always visible
			return 10;
		default:
			return 24;
		}
	}

	public void setMinResolution(int minResolution) {
		this.minResolution = minResolution;
	}
}

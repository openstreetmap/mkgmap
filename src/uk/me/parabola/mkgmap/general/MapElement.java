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

/**
 * A map element is a point, line or shape that appears on the map.  This
 * class holds all the common routines that are shared across all elements.
 * 
 * @author Steve Ratcliffe.
 */
public abstract class MapElement {
	private String name;
	private int type;

	private int minResolution = 24;
	private int maxResolution = 24;

	protected MapElement() {
	}

	protected MapElement(MapElement orig) {
		name = orig.name;
		type = orig.type;
		minResolution = orig.minResolution;
		maxResolution = orig.maxResolution;
	}

	/**
	 * Provide a copy of this MapElement without geometry. This is used
	 * when filtering and clipping to create modified versions.
	 *
	 * @return the copy;
	 */
	public abstract MapElement copy();

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
	public abstract Coord getLocation();

	/**
	 * Get the resolutions that an element should be displayed at.
	 * It will return the minimum resolution at which this element should be
	 * displayed at.
	 *
	 * @return The lowest resolution at which the element will be visible.
	 */
	public int getMinResolution() {
		return minResolution;
	}

	public void setMinResolution(int minResolution) {
		this.minResolution = minResolution;
	}

	public int getMaxResolution() {
		return maxResolution;
	}

	public void setMaxResolution(int maxResolution) {
		this.maxResolution = maxResolution;
	}
}

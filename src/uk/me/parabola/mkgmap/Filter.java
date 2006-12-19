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
 * Create date: 18-Dec-2006
 */
package uk.me.parabola.mkgmap;

/**
 * At each zoom level it is only possible/desirable to display a subset of the
 * possible information available.
 *
 * It is also a good idea to reduce the number of points that go to make up
 * a way so that there are no zero length or very short segments at the lower
 * resolutions.
 *
 * @author Steve Ratcliffe
 */
public interface Filter {

	/**
	 * Filter the lines (roads etc) from the map detail as required for the
	 * given zoom level.
	 * 
	 * @param detail The map features.
	 * @param zoom The zoom level as a number of bits.  Eg 24 is the highest
	 * level of detail.
	 */
	public void filterLines(MapDetail detail, int zoom);
}

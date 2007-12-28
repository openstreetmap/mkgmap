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
 * Create date: 24-Mar-2007
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.trergn.Overview;

import java.util.List;

/**
 * A source of map data.  This base interface is used internally within
 * the program.
 * 
 * @author Steve Ratcliffe
 */
public interface MapDataSource {
	/**
	 * Get the area that this map covers.
	 *
	 * @return The area the map covers.
	 */
	Area getBounds();

	/**
	 * Get the list of points that need to be rendered on the map.
	 *
	 * @return A list of {@link MapPoint} objects.
	 */
	List<MapPoint> getPoints();

	/**
	 * Get the list of lines that need to be rendered to the map.
	 *
	 * @return A list of {@link MapLine} objects.
	 */
	List<MapLine> getLines();

	/**
	 * Get the list of shapes that need to be rendered to the map.
	 *
	 * @return A list of {@link MapShape} objects.
	 */
	List<MapShape> getShapes();

	/**
	 * Get a list of every feature that is used in the map.  As features are
	 * created a list is kept of each separate feature that is used.  This
	 * goes into the .img file and is important for points and polygons although
	 * it doesn't seem to matter if lines are represented or not on my Legend Cx
	 * anyway.
	 *
	 * @return A list of all the types of point, polygon and polyline that are
	 * used in the map.
	 */
	List<Overview> getOverviews();
}

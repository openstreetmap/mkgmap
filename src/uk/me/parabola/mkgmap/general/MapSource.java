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
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.mkgmap.FormatException;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * A source of map information in a standard format.  The OSM reader presents
 * this interface to the main map making program.  OSM concepts are converted
 * into Garmin map based structures such as {@link MapLine}. 
 *
 * @author Steve Ratcliffe
 */
public interface MapSource {

	/**
	 * Load map by name.  The name is in a suitable form to be recognised
	 * by the particular map source.  It could be a file name or a URI.
	 *
	 * @param name The name of the resource to be loaded.
	 * @throws FileNotFoundException When the file or resource is not found.
	 * @throws FormatException For any kind of malformed input.
	 */
	public void load(String name)
			throws FileNotFoundException, FormatException;

	/**
	 * Get a suitable copyright message for this map source.
	 *
	 * @return A string with the name of the copyright holder.
	 */
	public String copyrightMessage();

	/**
	 * Get the area that this map covers.
	 *
	 * @return The area the map covers.
	 */
	public Area getBounds();

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
}

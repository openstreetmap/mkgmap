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

import uk.me.parabola.imgfmt.FormatException;

import java.io.FileNotFoundException;

/**
 * A source of map information in a standard format.  The OSM reader presents
 * this interface to the main map making program.  OSM concepts are converted
 * into Garmin map based structures such as {@link MapLine}. 
 *
 * Note that it does not reference anything from the imgfmt package that
 * relies on subdivisions.  In other words it does not directly reference
 * Point or Polyline as they depend on the subdivision they are in.  It
 * can refer to Coord and Overview however as they have global meaning.
 *
 * @author Steve Ratcliffe
 */
public interface LoadableMapDataSource extends MapDataSource {

	/**
	 * Determins if the file (or other resource) is supported by this map
	 * data source.  The implementation may do this however it likes, eg
	 * by extension or by opening up the file and reading part of it.
	 *
	 * @param name The file (or other resource) to check.
	 * @return True if the loadable map data source supports that file.
	 */
	public boolean isFileSupported(String name);

	/**
	 * Load map by name.  The name is in a suitable form to be recognised
	 * by the particular map source.  It could be a file name or a URI.
	 *
	 * You would implement this interface to read mapping data in an other
	 * format.
	 *
	 * @param name The name of the resource to be loaded.
	 * @throws FileNotFoundException When the file or resource is not found.
	 * @throws FormatException For any kind of malformed input.
	 */
	public void load(String name)
			throws FileNotFoundException, FormatException;

	/**
	 * Get the map levels for this map.  This is an array of @{link LevelInfo}
	 * structures that map a level to a resolution.  Some map data sources
	 * may actually have the concept of map layers that can be used to
	 * construct this information.  Others may just have to provide a default
	 * that is useful with the map source.  In the latter case it would be
	 * important to be able to configure the levels separately while creating
	 * the map.
	 *
	 * <p>Note that it does not include the top empty level as we will alway
	 * generate that in the main program automatically.
	 *
	 * @return Array of structures that map the level to the resolution.  Never
	 * returns null. Some kind of default should always be returned and this
	 * must include at least one level.
	 */
	public LevelInfo[] mapLevels();

	/**
	 * Get a suitable copyright message for this map source.  You can get
	 *
	 * @return An array of strings with copyright information.  If there are
	 * none then return a zero length array.
	 */
	public String[] copyrightMessages();

}

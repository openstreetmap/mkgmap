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
 * Create date: Feb 17, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.Properties;

import uk.me.parabola.mkgmap.general.MapCollector;

/**
 * An new system to convert from osm styles to garmin styles.  Instead of a
 * single file, there will be multiple files in a directory.  The directory
 * will be the name of the style.  From the start there will be a versioning
 * system, so that we can extend it better.
 *
 * I expect that it should be possible to set the name 
 *
 * @author Steve Ratcliffe
 */
public class StyledConverter implements OsmConverter {
	private MapCollector collector;
	private Properties config;

	public StyledConverter(Properties config, MapCollector collector) {
		this.config = config;
		this.collector = collector;
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 * <p>
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
	}

	/**
	 * 
	 * @param el The element to set the name upon.
	 */
	public void convertName(Element el) {
	}
}

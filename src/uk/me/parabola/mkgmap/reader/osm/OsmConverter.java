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
 * Create date: 20-Dec-2006
 */
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.imgfmt.app.Area;

/**
 * @author Steve Ratcliffe
 */
public interface OsmConverter {
	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 *
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way);

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node);

	/**
	 * In OSM there isn't just one name tag for a node or way, there are
	 * several and you might want to create a name out of several tags.
	 * This method allows you to do whatever you want.
	 *
	 * It is called before convertNode and convertWay.
	 *
	 * <p>Examples are:
	 * <ul>
	 *
	 * <li>A road name having the reference in brackets after the name (uses
	 * the name and ref tags).
	 *
	 * <li>Maps for different languages, you might want to try the local
	 * language first and fall back to more generic versions of the name:
	 * eg try name:zh_py first and then name:en, int_name, name in order
	 * until one is found.
	 *
	 * <li>Special purpose maps like the cycling map, need to set the name
	 * to something other than 'name', also dependant on the other
	 * tags present.
	 *
	 * </ul>
	 *
	 * @param el The element to set the name upon.
	 */
	public void convertName(Element el);

	/**
	 * Set the bounding box for this map.  This should be set before any other
	 * elements are converted if you want to use it.
	 * All elements that are added are clipped to this box, new points are
	 * added as needed at the boundry.
	 *
	 * If a node or a way falls completely outside the boundry then
	 * it would be ommited.  This would not normally happen in the way this
	 * option is typically used however.
	 *
	 * @param bbox The bounding area.
	 */
	public void setBoundingBox(Area bbox);
}

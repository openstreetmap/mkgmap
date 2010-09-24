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
	 * Takes a relation and applies rules that affect the garmin types
	 * of its contained elements.
	 *
	 * The relation rules are run first.  A relation contains references
	 * to a number of nodes, ways and even other relations, as well as its
	 * own set of tags.  They have many purposes some of which are not
	 * relevant to styling.
	 *
	 * @param relation The relation to convert.
	 */
	public void convertRelation(Relation relation);

	/**
	 * Set the bounding box for this map.  This should be set before any other
	 * elements are converted if you want to use it.
	 * All elements that are added are clipped to this box, new points are
	 * added as needed at the boundary.
	 *
	 * If a node or a way falls completely outside the boundary then
	 * it would be omitted.  This would not normally happen in the way this
	 * option is typically used however.
	 *
	 * @param bbox The bounding area.
	 */
	public void setBoundingBox(Area bbox);

	/**
	 * Called when all conversion has been done.
	 */
	public void end();
}

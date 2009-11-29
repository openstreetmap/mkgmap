/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 07-Nov-2008
 */
package uk.me.parabola.mkgmap.reader.osm;

/**
 * A rule takes an element and returns the correct garmin type for it.
 * Immplementations can be simple or complex as needed.
 *
 * @author Steve Ratcliffe
 */
public interface Rule {

	/**
	 * Given the element return the garmin type that should be used to
	 * represent it.
	 *
	 * @param el The element as read from an OSM xml file in 'tag' format.
	 * @return Enough information to represent this as a garmin type.
	 */
	public GType resolveType(Element el);

	/**
	 * Given the element return the garmin type that should be used to
	 * represent it.
	 *
	 * @param el The element as read from an OSM xml file in 'tag' format.
	 * @param pre The previous garmin type generated from the element.
	 * @return Enough information to represent this as a garmin type.
	 */
	public GType resolveType(Element el, GType pre);
}

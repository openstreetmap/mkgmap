/*
 * Copyright (C) 2013.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package uk.me.parabola.mkgmap.osmstyle.function;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Function that returns the id of the OSM element.
 * @author WanMil
 */
public class OsmIdFunction extends StyleFunction {

	public OsmIdFunction() {
		super(null);
	}

	public boolean supportsNode() {
		return true;
	}

	public boolean supportsWay() {
		return true;
	}

	public boolean supportsRelation() {
		return true;
	}
	
	public String value(Element el) {
		// return the osm id
		return String.valueOf(el.getId());
	}

	public String getName() {
		return "osmid";
	}

}

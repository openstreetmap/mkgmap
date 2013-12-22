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
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Retrieves the OSM type of an element. Values:
 * <ul>
 * <li>node</li>
 * <li>way</li>
 * <li>relation</li>
 * </ul>
 * @author WanMil
 *
 */
public class TypeFunction extends StyleFunction {

	public TypeFunction() {
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
		if (el instanceof Node)
			return "node";
		if (el instanceof Way) 
			return "way";
		if (el instanceof Relation)
			return "relation";
		return null;
	}

	public String getName() {
		return "type";
	}

}

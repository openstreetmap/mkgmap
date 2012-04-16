/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.util.Map.Entry;

import uk.me.parabola.mkgmap.reader.osm.Tags;

public class Boundary  {
	private String id;	// the id of the OSM relation (was kept in tag "mkgmap:boundaryid")

	private final Tags tags;
	private transient Area area;

	public Boundary(Area area, Tags tags, String id) {
		this.area = new Area(area);
		this.tags = tags.copy();
		this.id = id;
	}

	public Boundary(Area area, Iterable<Entry<String, String>> tags, String id) {
		this.area = new Area(area);
		this.id = id;
		this.tags = new Tags();
		for (Entry<String, String> tag : tags) {
			this.tags.put(tag.getKey(), tag.getValue());
		}
	}

	public String getId() {
		return id;
	}

	public Tags getTags() {
		return tags;
	}

	public Area getArea() {
		return area;
	}
}

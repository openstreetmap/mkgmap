/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.reader.osm;

/**
 * Interface for adding a map element to the map.  Called from the style
 * engine when it has resolved a type from the input osm tags.
 *
 * @author Steve Ratcliffe
 */
public interface TypeResult {
	/**
	 * Add the resolved type to next stage in the map making process.
	 * @param el The OSM element.
	 * @param type The Garmin type that this resolves too.
	 */
	public void add(Element el, GType type);

	/**
	 * Use this if you don't want to save the results.  Only likely to be
	 * used for the test cases.
	 */
	public static TypeResult NULL_RESULT = new TypeResult() {
		public void add(Element el, GType type) {
		}
	};
}

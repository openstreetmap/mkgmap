/*
 * Copyright (C) 2012.
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
 * Get the value of a tag from the element.
 *
 * In the style language: highway = primary
 * This is effectively a shorthand for: get_tag(highway) = primary
 *
 * @author Steve Ratcliffe
 */
public class GetTagFunction extends StyleFunction {

	public GetTagFunction(String value) {
		super(value);
	}

	public String value(Element el) {
		return el.getTag(getKeyValue());
	}

	/**
	 * Since this contains a tag value it can potentially be used to index the whole rule,
	 * so return true here.
	 */
	public boolean isIndexable() {
		return true;
	}

	public String toString() {
		return "$" + getKeyValue();
	}
}

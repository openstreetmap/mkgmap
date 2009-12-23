/*
 * Copyright 2009 Clinton Gladstone
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */

package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * This can be used to filter out redundant values.
 *
 * The filter checks the equality of a value with another tag's value.
 * If the two values match, a null string is returned.
 *
 * @author Clinton Gladstone
 */
public class NotEqualFilter extends ValueFilter {

	private final String tagName; 

	public NotEqualFilter(String s) {

		tagName = s;

	}

	public String doFilter(String value, Element el) {
		if (value == null) return value;

		String tagValue = el.getTag(tagName);

		if (tagValue == null)
			return value;

		if (value.equals(tagValue))
			return null;  // Return nothing if value is identical to the tag's value 
		else
			return value;

	}
}

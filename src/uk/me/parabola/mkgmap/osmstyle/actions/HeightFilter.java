/*
 * Copyright 2009 Toby Speight
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
import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * A {@code HeightFilter} transforms values into Garmin-tagged elevations.
 *
 * @author Toby Speight
 *
 * @since 2009-04-26
 */
public class HeightFilter extends ConvertFilter {

	public HeightFilter(String s) {
		super(s == null ? "m=>ft" : s);
		if (s != null && !(s.endsWith("ft") || s.endsWith("feet")))
			throw new SyntaxException(String.format("height filter reqires ft (feet) as target unit: '%s'", s));
	}

	public String doFilter(String value, Element el) {
		String s = super.doFilter(value, el);
		if (s != null)
			s = "\u001f" + s;
		return s;
	}
}

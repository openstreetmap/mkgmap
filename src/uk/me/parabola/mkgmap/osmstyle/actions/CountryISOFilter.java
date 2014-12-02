/*
 * Copyright (C) 2014.
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
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.build.LocatorConfig;
import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Convert a string containing a country name or ISO string to the 3 character ISO string.
 * Samples: Deutschland->DEU, UK->GBR
 *
 * @author GerdP
 */
public class CountryISOFilter extends ValueFilter {

	public CountryISOFilter() {
	}

	protected String doFilter(String value, Element el) {
		if (value == null)
			return value;
		String s = LocatorConfig.get().getCountryISOCode(value);
		if (s != null)
			return s;
		else 
			return value;
	}
}

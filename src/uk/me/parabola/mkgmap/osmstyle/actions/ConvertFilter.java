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
 * Create date: 07-Dec-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.osmstyle.eval.UnitConversions;
import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Convert a numeric quantity from one set of units to another.
 *
 * @author Steve Ratcliffe
 */
public class ConvertFilter extends ValueFilter {
	private static final Pattern UNIT_RE = Pattern.compile("\\s*([\\d.]+)\\s*([\\w/]*)\\s*");

	private final UnitConversions units;

	public ConvertFilter(String arg) {
		units = UnitConversions.createConversion(arg);
	}

	protected String doFilter(String value, Element el) {
		if (value == null || !units.isValid())
			return value;

		String number = value;
		Double factor = units.getDefaultFactor();

		// If this is not a pure number, then extract the number part and the unit part
		// and convert based on the found values.  There are also various possible error
		// cases.
		if (!Character.isDigit(value.charAt(value.length() - 1))) {
			// Extract number and unit string
			Matcher matcher = UNIT_RE.matcher(value);
			if (matcher.matches()) {
				number = matcher.group(1);
				String source = matcher.group(2);
				factor = units.convertFactor(source);
				if (factor == null)
					return value;
			} else {
				return value;
			}
		}

		try {
			double d = Double.parseDouble(number);
			return String.valueOf(Math.round(d * factor));
		} catch (NumberFormatException e) {
			// Turns out it wasn't a pure number, just return the value unchanged.
			return value;
		}
	}
}

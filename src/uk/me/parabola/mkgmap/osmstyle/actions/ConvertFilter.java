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

import uk.me.parabola.mkgmap.osmstyle.UnitConversions;

/**
 * Convert a numeric quantity from one set of units to another.
 *
 * TODO: this will change a lot it is just here for backward compatibility
 * at present.
 * @author Steve Ratcliffe
 */
public class ConvertFilter extends ValueFilter {
	private final double factor;

	public ConvertFilter(String arg) {
		factor = UnitConversions.convertFactor(arg);
	}

	public String doFilter(String value) {
		if (value == null) return null;
		
		try {
			double d = Double.parseDouble(value);

			double res = d * factor;
			res = Math.round(res * 100)/100.0;
			return String.valueOf(res);
		} catch (NumberFormatException e) {
			return value;
		}
	}
}

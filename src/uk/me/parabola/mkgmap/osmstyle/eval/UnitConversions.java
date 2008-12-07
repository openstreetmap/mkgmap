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
package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.HashMap;
import java.util.Map;

/**
 * Converting quantities from one unit to another.
 *
 * TODO: this will probably change a lot.
 *
 * @author Steve Ratcliffe
 */
public class UnitConversions {
	private static final Map<String, Double> conversions = new HashMap<String, Double>();

	// Initially we are just supporting the existing case for contour
	// lines where we convert to feet.
	static {
		Map<String, Double> m = conversions;
		m.put("m=>ft", 3.2808399);
	}
	//
	//private double factor;
	//
	//public double convert(double in) {
	//	return in * factor;
	//}

	/**
	 * Get the coversion factor for the given conversion.
	 * @param code A string such as 'm=>ft' which would mean meters
	 * to feet.
	 * @return The factor required to convert the first to the second.
	 */
	public static double convertFactor(String code) {
		Double f = conversions.get(code);
		return (f == null)?1 :f;
	}
}

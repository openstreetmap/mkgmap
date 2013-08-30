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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Returns the maxspeed converted to km/h.
 * @author WanMil
 */
public class MaxSpeedKmhFunction extends CachedFunction {
	private static final Pattern ENDS_IN_MPH_PATTERN = Pattern.compile(".*mph");
	private static final Pattern REMOVE_MPH_PATTERN = Pattern.compile("[ \t]*mph");
	private static final Pattern REMOVE_KMH_PATTERN = Pattern.compile("[ \t]*km/?h");

	private final DecimalFormat nf = new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.US));

	public MaxSpeedKmhFunction() {
		// requires maxspeed
		super("maxspeed");
	}

	protected String calcImpl(Element el) {
		// get the maxspeed value
		String tagValue = el.getTag("maxspeed");
		if (tagValue == null) {
			// there is no maxspeed => function has no value
			return null;
		}
		
		String speedTag = tagValue.toLowerCase().trim();
		
		double factor = 1.0;
		if (ENDS_IN_MPH_PATTERN.matcher(speedTag).matches()) {
			// Check if it is a limit in mph
			speedTag = REMOVE_MPH_PATTERN.matcher(speedTag).replaceFirst("");
			factor = 1.61;
		} else
			speedTag = REMOVE_KMH_PATTERN.matcher(speedTag).replaceFirst("");  // get rid of kmh just in case

		double kmh;
		try {
			kmh = Integer.parseInt(speedTag) * factor;
		} catch (Exception e) {
			// parse error => maxspeed cannot be calculated
			return null;
		}		
		
		// format with two decimals
		return nf.format(kmh);
	}

	public String getName() {
		return "maxspeedkmh";
	}

	public boolean supportsWay() {
		return true;
	}
}

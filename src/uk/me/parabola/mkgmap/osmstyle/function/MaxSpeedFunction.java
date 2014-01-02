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
 * Returns the maxspeed converted either to km/h or mph.
 * 
 * @author WanMil
 */
public class MaxSpeedFunction extends CachedFunction {

	public enum SpeedUnit {
		KMH, MPH;

		public double convert(double value, SpeedUnit valueUnit) {
			if (this == valueUnit) {
				// same unit => no conversion necessary
				return value;
			} else if (valueUnit == MPH) {
				// not the same unit - value is mph => target is km/h => factor
				// 1.61
				return value * 1.61;
			} else {
				// not the same unit - value is kmh => target is mph => factor
				// 1/1.61
				return value / 1.61;
			}
		}
	}

	private static final Pattern ENDS_IN_MPH_PATTERN = Pattern.compile(".*mph");
	private static final Pattern REMOVE_MPH_PATTERN = Pattern
			.compile("[ \t]*mph");
	private static final Pattern REMOVE_KMH_PATTERN = Pattern
			.compile("[ \t]*km/?h");

	private final DecimalFormat nf = new DecimalFormat("0.0#",
			DecimalFormatSymbols.getInstance(Locale.US));

	private final SpeedUnit unit;

	public MaxSpeedFunction(SpeedUnit unit) {
		// requires maxspeed
		super("maxspeed");

		this.unit = unit;
	}

	protected String calcImpl(Element el) {
		// get the maxspeed value
		String tagValue = el.getTag("maxspeed");
		if (tagValue == null) {
			// there is no maxspeed => function has no value
			return null;
		}

		String speedTag = tagValue.toLowerCase().trim();

		// take KMH as default
		SpeedUnit speedTagUnit = SpeedUnit.KMH;
		if (ENDS_IN_MPH_PATTERN.matcher(speedTag).matches()) {
			// Check if it is a limit in mph
			speedTag = REMOVE_MPH_PATTERN.matcher(speedTag).replaceFirst("");
			speedTagUnit = SpeedUnit.MPH;
		} else
			// get rid of kmh just in case
			speedTag = REMOVE_KMH_PATTERN.matcher(speedTag).replaceFirst("");

		try {
			// convert to the target unit
			double speed = this.unit.convert(Integer.parseInt(speedTag), speedTagUnit);
			// format with two decimals
			return nf.format(speed);
		} catch (Exception e) {
			// parse error => maxspeed cannot be calculated
			return null;
		}

	}

	public String getName() {
		switch (this.unit) {
		case MPH:
			return "maxspeedmph";
		case KMH:
		default:
			return "maxspeedkmh";
		}
	}

	public boolean supportsWay() {
		return true;
	}
}

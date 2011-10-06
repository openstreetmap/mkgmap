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
 * Create date: 09-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Represents a number and the units it is in. We want ultimately to be
 * able to do things like: is 10km/h > 8mph, and get the right answer
 * by converting to a common unit.
 *
 * To start with we will just compare the numbers.
 *
 * @author Steve Ratcliffe
 */
public class ValueWithUnit implements Comparable<ValueWithUnit> {
	private static final Pattern EXTRACT_NUMBER_UNIT
			= Pattern.compile("[ \t]*(-?[0-9.]+)[ \t]*(.*)");
	private static final BigDecimal ZERO = new BigDecimal(0);

	private final BigDecimal value;
	private final String unit;

	private final boolean valid;

	public ValueWithUnit(String val) {
		Matcher m = EXTRACT_NUMBER_UNIT.matcher(val);
		boolean found = m.find();

		BigDecimal value = ZERO;
		String unit = "";
		boolean ok = false;

		if (found) {
			try {
				value = new BigDecimal(m.group(1));
				unit = m.group(2).trim();
				ok = true;
			} catch (NumberFormatException e) {
				ok = false;
			}
		}
		
		this.value = value;
		this.unit = unit;
		this.valid = ok;
	}

	/**
	 * Compares this object with the specified object for order. Returns
	 * a negative integer, zero, or a positive integer as this object
	 * is less than, equal to, or greater than the specified object.
	 *
	 * To start with, just compare the value and ignore the unit.
	 */
	public int compareTo(ValueWithUnit o) {
		return value.compareTo(o.value);
	}

	public boolean isValid() {
		return valid;
	}

	public String toString() {
		return value + unit;
	}
}

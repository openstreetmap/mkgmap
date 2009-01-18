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
 * Create date: May 17, 2008
 */
package uk.me.parabola.util;

import java.util.Properties;

/**
 * Wrapper that behaves as an enhanced properties class that has getProperty
 * calls for different data types.
 *
 * @author Steve Ratcliffe
 */
public class EnhancedProperties extends Properties {

	public EnhancedProperties() {
	}

	/**
	 * Get a property as an integer value.  If the property does not exist
	 * or the value is not a valid integer, then the default value is returned
	 * instead.
	 * @param key The property name to retreive.
	 * @param def The Default value to use if the property doesn not exist or
	 * if the value is not a valid integer.
	 * @return The value of the property as an integer, or the default value.
	 */
	public int getProperty(String key, int def) {
		try {
			String s = getProperty(key);
			return s == null ? def : Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/**
	 * Get a property as a boolean value.  If the value of the property
	 * begins with a '1', a 'y' or a 't' (for 1, yes, true) then true is
	 * returned.  If the property does not exist then the given default
	 * value is returned.
	 *
	 * @param key The property name to get.
	 * @param def The default value that is returned if property does not
	 * exist.
	 * @return The value of the property as a boolean.  If the property does
	 * not exist then the value of 'def'.
	 */
	public boolean getProperty(String key, boolean def) {
		String s = getProperty(key);
		if (s != null) {
			if (s.length() == 0)
				return true;
			char c = s.toLowerCase().charAt(0);
			if (c == '1' || c == 'y' || c == 't')
				return true;
			else
				return false;
		}
		return def;
	}

	/**
	 * Return a property as a double value.  If the property does not
	 * exist or is not a valid double, then the given default value
	 * is returned instead.
	 * @param key The property name.
	 * @param def The default value to return if no valid value.
	 * @return The value of the property as a double.
	 */
	public double getProperty(String key, double def) {
		try {
			String s = getProperty(key);
			return s == null ? def : Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public Object clone() {
		return super.clone();
	}
}

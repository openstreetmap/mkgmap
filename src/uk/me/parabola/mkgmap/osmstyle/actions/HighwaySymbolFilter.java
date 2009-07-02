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

import java.util.HashMap;
import java.util.Map;


/**
 * Prepend a Garmin magic-character to the value.
 * TODO: symbolic names?
 *
 * @author Toby Speight
 */
public class HighwaySymbolFilter extends ValueFilter {
	private final String prefix;

	private static final Map<String, String>symbols = new HashMap<String, String>();
	private static final int MAX_REF_LENGTH = 8; // enough for "A6144(M)" (RIP)

	static {
		//symbols.put("ele", "\u001f"); // name.height separator

		// Now add other symbols
		symbols.put("interstate", "\u0001"); // US Interstate
		symbols.put("shield", "\u0002"); // US Highway shield
		symbols.put("round", "\u0003"); // US Highway round
		symbols.put("hbox", "\u0004"); // box with horizontal bands
		symbols.put("box", "\u0005"); // Square box
		symbols.put("oval", "\u0006"); // box with rounded ends
	}

	public HighwaySymbolFilter(String s) {
		// First, try the lookup table
		String p = symbols.get(s);
		if (p == null) {
			p = "[" + s + "]";
		}
		prefix = p;
	}

	public String doFilter(String value) {
		if (value == null || value.length() > MAX_REF_LENGTH) return value;

		// is it mostly alphabetic?
		int alpha_balance = 0;
		for (char c : value.toCharArray()) {
			alpha_balance += (Character.isLetter(c)) ? 1 : -1;
		}
		if (alpha_balance > 0) return value;

		// remove space if there is exactly one
		int first_space = value.indexOf(" ");
		if (first_space >= 0 && value.indexOf(" ", first_space) < 0) {
			value = value.replace(" ", "");
		}

		return prefix + value;
	}
}

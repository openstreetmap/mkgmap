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
public class PrependFilter extends ValueFilter {
	private final String prefix;

	private static final Map<String, String> symbols_6bit;
	private static final Map<String, String> symbols_8bit;

	static {
		// Firstly, the symbols common to both encodings
		symbols_6bit = new HashMap<String, String>();
		symbols_6bit.put("ele", "\u001f"); // name.height separator

		// Copy to other encoding
		symbols_8bit = new HashMap<String, String>(symbols_6bit);

		// Now add other symbols
		symbols_6bit.put("interstate", "\u002a"); // US Interstate
		symbols_8bit.put("interstate", "\u0001");
		symbols_6bit.put("shield", "\u002b"); // US Highway shield
		symbols_8bit.put("shield", "\u0002");
		symbols_6bit.put("round", "\u002c"); // US Highway round
		symbols_8bit.put("round", "\u0003");
		symbols_6bit.put("boxx", "\u002d"); // box with horizontal bands
		symbols_8bit.put("boxx", "\u0004");
		symbols_6bit.put("box", "\u002e"); // Square box
		symbols_8bit.put("box", "\u0005");
		symbols_6bit.put("oval", "\u002f"); // box with rounded ends
		symbols_8bit.put("oval", "\u0006");
	}

	// TODO: runtime select appropriate table
	private final Map<String, String> symbols = symbols_8bit;

	public PrependFilter(String s) {
		// First, try the lookup table
		String p = symbols.get(s);
		if (p == null) {
			// else, s is a hex constant character number
			try {
				p = Character.toString((char)Integer.parseInt(s, 16));
			} catch (NumberFormatException e) {
				// failed - use string literally
				p = s;
			}
		}
		prefix = p;
	}

	public String doFilter(String value) {
		return value == null ? null : prefix + value;
	}
}

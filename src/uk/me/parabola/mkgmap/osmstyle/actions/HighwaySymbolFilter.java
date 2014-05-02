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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.reader.osm.Element;


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

	private int maxAlphaNum = MAX_REF_LENGTH; // Max. length for alphanumeric (e.g., 'A67')
	private int maxAlpha = MAX_REF_LENGTH; // Max. length for alpha only signs (e.g., 'QEW')

	private static final Pattern spacePattern = Pattern.compile(" ", Pattern.LITERAL);
	private static final Pattern semicolonPattern = Pattern.compile(";", Pattern.LITERAL);
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

		String[] filters = s.split(":");

		// First, try the lookup table
		String p = symbols.get(filters[0]);
		if (p == null) {
			p = "[" + filters[0] + "]";
		}
		prefix = p;

		// Set maximum length for alpha/alphanumeric signs:
		if ( filters.length == 3 ) {
			maxAlphaNum = Integer.parseInt(filters[1]);
			maxAlpha = Integer.parseInt(filters[2]);
		} else if ( filters.length == 2 ) {
			maxAlphaNum = Integer.parseInt(filters[1]);
			maxAlpha = maxAlphaNum; // If only one option specified, use for both
		} else {
			maxAlphaNum = MAX_REF_LENGTH; // Ensure use of defaults if none specified
			maxAlpha = MAX_REF_LENGTH; 
		}

	}

	public String doFilter(String value, Element el) {
		if (value == null) return value;

		// is it mostly alphabetic?
		/* int alpha_balance = 0;
		for (char c : value.toCharArray()) {
			alpha_balance += (Character.isLetter(c)) ? 1 : -1;
		}
		if (alpha_balance > 0) return value;

		// remove space if there is exactly one
		int first_space = value.indexOf(" ");
		if (first_space >= 0 && value.indexOf(" ", first_space + 1) < 0) {
			value = value.replace(" ", "");
		} */


		// Nuke all spaces
//		String shieldText = value.replace(" ", "");
		String shieldText = spacePattern.matcher(value).replaceAll(Matcher.quoteReplacement(""));
		// Also replace ";" with "/", to change B3;B4 to B3/B4
		//shieldText = shieldText.replace(";", "/");
		shieldText = semicolonPattern.matcher(shieldText).replaceAll(Matcher.quoteReplacement("/"));
		
		// Check if value is alphanumeric
		boolean isAlphaNum = false;

		for (char c : shieldText.toCharArray()) {
		  	if (Character.isDigit(c)) {
				isAlphaNum = true; // Consider alphanumeric if we find one or more digits
			}
		}

		// Check if shield exceeds maximum length:
		if ( (isAlphaNum && shieldText.length() > maxAlphaNum) || (! isAlphaNum) && shieldText.length() > maxAlpha ) {
			return value; // If so, return original value
		} else {
			return prefix + shieldText; // If not, return condensed value with magic code
		}
	}
}

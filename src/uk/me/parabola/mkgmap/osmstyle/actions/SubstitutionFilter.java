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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Perform string substitution on a value.
 * The operator => can be used for exact string substitution
 * The operator ~> can be used for regexp substitutions
 * If no operator is set, the matching string is deleted
 *
 * @author Toby Speight
 * @author Enrico Liboni
 */
public class SubstitutionFilter extends ValueFilter {
	private final String from;
	private final String to;
	private boolean isRegexp = false;
	private final Pattern pattern;

	public SubstitutionFilter(String arg) {
		int i = arg.indexOf("=>");

		if (i == -1) { // no occurrences of =>, let's try with ~>
			i = arg.indexOf("~>");
			if ( i >= 0 ) isRegexp = true;
		}

		if (i >= 0) {
			from = arg.substring(0, i);
			to = arg.substring(i + 2);
		} else {
			from = arg;
			to = "";
		}
		if (isRegexp)
			pattern = Pattern.compile(from);
		else
			pattern = Pattern.compile(from, Pattern.LITERAL);
	}

	public String doFilter(String value, Element el) {
		if (value == null) return null;
		return pattern.matcher(value).replaceAll(isRegexp ? to : Matcher.quoteReplacement(to));
		// replaceAll expects a regexp as 1st argument
//				return (isRegexp ? value.replaceAll(from, to) : value.replace(from, to) );
	}
}

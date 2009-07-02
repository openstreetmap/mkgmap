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

/**
 * Perform simple string substitution on a value.
 *
 * @author Toby Speight
 */
public class SubstitutionFilter extends ValueFilter {
	private final String from;
	private final String to;

	public SubstitutionFilter(String arg) {
		int i = arg.indexOf("=>");
		if (i >= 0) {
			from = arg.substring(0, i);
			to = arg.substring(i + 2);
		} else {
			from = arg;
			to = "";
		}
	}

	public String doFilter(String value) {
		if (value == null) return null;
		if (from == null || to == null)
			// can't happen!
			return value;
		return value.replace(from, to);
	}
}

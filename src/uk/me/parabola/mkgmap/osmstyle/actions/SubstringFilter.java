/*
 * Copyright (C) 2010 Jeffrey C. Ollie
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
 * Author: Jeffrey C. Ollie
 * Create date: 08-March-2010
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Extract a substring from a value
 *
 * @author Jeffrey C. Ollie
 */
public class SubstringFilter extends ValueFilter {
	private int args;
	private int start;
	private int end;

	public SubstringFilter(String arg) {
		start = 0;
		end = 0;
		args = 0;

		String[] temp = arg.split(":");

		try {
			if (temp.length == 1) {
				start = Integer.parseInt(temp[0]);
				args = 1;
			} else if (temp.length == 2) {
				start = Integer.parseInt(temp[0]);
				end = Integer.parseInt(temp[1]);
				args = 2;
			} else {
				start = 0;
				end = 0;
				args = 0;
			}
		} catch (NumberFormatException e) {
			throw new ExitException("Not valid numbers in style substring command: " + arg);
		}
	}

	protected String doFilter(String value, Element el) {
		if (value == null) return null;

		if (args == 1) {
			return value.substring(start);
		}
		if (args == 2) {
			return value.substring(start, end);
		}
		return value;
	}
}

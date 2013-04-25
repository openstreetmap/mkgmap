/*
 * Copyright (c) 2013.
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
package uk.me.parabola.mkgmap.osmstyle.actions;

import java.util.regex.Pattern;
import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Get a part of a value.
 * value is split at a separator that defaults to semicolon ';'
 * by default the first part is returned
 * if the optional second parameter 'partnumber' is bigger 
 * than the number of parts in the value then null is returned
 *
 * @author Franco Bez
 */
public class PartFilter extends ValueFilter {
	private String separator;
	private int partnumber;

	public PartFilter(String arg) {
		String[] temp = arg.split(":");
		partnumber = 1;
		try {
			if( temp.length > 1 ) {
				partnumber = Integer.parseInt(temp[1]);
			}
			if(temp[0].length() > 0 ){
				separator = temp[0];
			}
			else{
				separator = ";";
			}
		} catch (NumberFormatException e) {
			throw new ExitException("Not valid numbers in style part command: " + arg);
		}
	}

	public String doFilter(String value, Element el) {
		if (value == null) return null;
		// split uses a regex we need to replace special characters
		String[] temp = value.split(Pattern.quote(separator));
		if (temp.length >= partnumber)
			return temp[partnumber-1].trim();
		return null;
	}
}

/*
 * Copyright (c) 2015.
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License version 3 or version 2 as published by the Free
 * Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * This can be used to filter out redundant values.<br>
 * <br>
 * The filter checks whether the value is contained within another tag's value.
 * If so, a null string is returned.<br>
 * <br>
 * Another tag should consist of values separated by a delimiter (semicolon ';'
 * by default).<br>
 * <br>
 * Example:<br>
 * <code>
 * type=route & route=bus & ref=* {<br>
 * 		apply {<br>
 * 			set route_ref='$(route_ref),${ref|not-contained:,:route_ref}' | '$(route_ref)' | '${ref}';<br>
 * 		}<br>
 * }</code><br>
 * Here, ref value is only added to route_ref when it is not already contained
 * in that list (with separator ','). Otherwise, the value of route_ref is
 * unchanged.
 *
 * @author Maxim Duester
 */
public class NotContainedFilter extends ValueFilter {
	private String separator;
	private short tagKey;

	public NotContainedFilter(String arg) {
		String[] temp = arg.split(":");

		if (temp.length < 2 || temp[1].isEmpty())
			throw new SyntaxException(
					"Missing tag to compare in style not-contained command: "
							+ arg);

		// set the separator (default to ;)
		if (temp[0].length() > 0)
			separator = temp[0];
		else
			separator = ";";
		// set the tag short value
		tagKey = TagDict.getInstance().xlate(temp[1]);
	}

	public String doFilter(String value, Element el) {
		if (value == null)
			return null;

		String tagValue = el.getTag(tagKey);
		// tag not found => value not in tag's value
		if (tagValue == null)
			return value;

		// split uses a regex we need to replace special characters
		String[] temp = tagValue.split(Pattern.quote(separator));

		for (String s : temp)
			if (s.equals(value))
				return null;

		// nothing found => value not in tag's value
		return value;
	}
}

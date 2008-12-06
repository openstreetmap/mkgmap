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
 * Create date: 02-Dec-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Build a value that can have tag values substituted in it.
 *
 * @author Steve Ratcliffe
 */
public class ValueBuilder {

	private String[] values;
	private String[] tags;

	public ValueBuilder(String pattern) {
		compile(pattern);
		assert tags.length == values.length;
	}

	/**
	 * Bulid this string if all the tags that are required are available.
	 *
	 * If a tag does not exist then the whole string is rejected.  This allows
	 * you to make conditional replacements.
	 *
	 * @param el Used as a source of tags.
	 * @return The built string if all required tags are available.  If any
	 * are missing then it returns null.
	 */
	public String build(Element el) {
		for (int i = 0; i < tags.length; i++) {
			String tname = tags[i];
			if (tname != null) {
				String val = el.getTag(tname);
				if (val == null)
					return null; // return early on not found

				values[i] = val;
			}
		}

		// If we get here we can build the final string.
		// Special case, no substitution, return the given string.
		if (values.length == 1)
			return values[0];

		StringBuilder sb = new StringBuilder();
		for (String s : values)
			sb.append(s);

		return sb.toString();
	}

	/**
	 * A tag value can contain variables that are the values of other tags.
	 * This is especially useful for 'name', as you might want to set it to
	 * some combination of other tags.
	 *
	 * If there are no replacement values, the same string as was passed
	 * in.  If all the replacement values exist, then the string with the
	 * values all replaced.  If any replacement tagname does not exist
	 * then returns null.

	 * @param in An input string that may contain tag replacement introduced
	 * by ${tagname}.
	 */
	private void compile(String in) {
		if (!in.contains("$")) {
			values = new String[] {in};
			tags = new String[] {null};
			return;
		}
		List<String> valuesList = new ArrayList<String>();
		List<String> tagsList = new ArrayList<String>();

		int state = 0;
		StringBuilder text = new StringBuilder();
		StringBuilder tagname = null;
		for (char c : in.toCharArray()) {
			switch (state) {
			case 0:
				if (c == '$') {
					valuesList.add(text.toString());
					tagsList.add(null);
					text.setLength(0);
					state = 1;
				} else
					text.append(c);
				break;
			case 1:
				if (c == '{') {
					tagname = new StringBuilder();
					state = 2;
				} else {
					state = 0;
					text.append(c);
				}
				break;
			case 2:
				if (c == '}') {
					//noinspection ConstantConditions
					assert tagname != null;
					valuesList.add(null);
					tagsList.add(tagname.toString());
					state = 0;
					tagname = null;
				} else {
					tagname.append(c);
				}
				break;
			default:
				assert false;
			}
		}

		if (text.length() > 0) {
			tagsList.add(null);
			valuesList.add(text.toString());
		}

		tags = tagsList.toArray(new String[tagsList.size()]);
		values = valuesList.toArray(new String[valuesList.size()]);
	}


}

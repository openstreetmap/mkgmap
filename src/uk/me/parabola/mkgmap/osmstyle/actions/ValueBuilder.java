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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Build a value that can have tag values substituted in it.
 *
 * @author Steve Ratcliffe
 * @author Toby Speight
 */
public class ValueBuilder {

	private final List<ValueItem> items = new ArrayList<ValueItem>();

	public ValueBuilder(String pattern) {
		compile(pattern);
	}

	/**
	 * Build this string if all the tags that are required are available.
	 *
	 * If a tag does not exist then the whole string is rejected.  This allows
	 * you to make conditional replacements.
	 *
	 * @param el Used as a source of tags.
	 * @return The built string if all required tags are available.  If any
	 * are missing then it returns null.
	 */
	public String build(Element el) {
		// Check early for no match and return early
		for (ValueItem item : items) {
			if (item.getValue(el) == null)
				return null;
		}

		// If we get here we can build the final string.  A common case
		// is that there is just one, so return it directly.
		if (items.size() == 1)
			return items.get(0).getValue(el);

		// OK we have to construct the result.
		StringBuilder sb = new StringBuilder();
		for (ValueItem item : items)
			sb.append(item.getValue(el));

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
			items.add(new ValueItem(in));
			return;
		}

		int state = 0;
		StringBuilder text = new StringBuilder();
		StringBuilder tagname = null;
		for (char c : in.toCharArray()) {
			switch (state) {
			case 0:
				if (c == '$') {
					state = 1;
				} else
					text.append(c);
				break;
			case 1:
				if (c == '{') {
					if (text.length() > 0) {
						items.add(new ValueItem(text.toString()));
						text.setLength(0);
					}
					tagname = new StringBuilder();
					state = 2;
				} else {
					state = 0;
					text.append('$');
					text.append(c);
				}
				break;
			case 2:
				if (c == '}') {
					//noinspection ConstantConditions
					assert tagname != null;
					addTagValue(tagname.toString());
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

		if (text.length() > 0)
			items.add(new ValueItem(text.toString()));
	}

	private void addTagValue(String tagname) {
		ValueItem item = new ValueItem();
		if (tagname.contains("|")) {
			String[] parts = tagname.split("\\|");
			assert parts.length > 1;
			item.setTagname(parts[0]);
			for (int i = 1; i < parts.length; i++)
				addFilter(item, parts[i]);
		} else {
			item.setTagname(tagname);
		}
		items.add(item);
	}

	private void addFilter(ValueItem item, String expr) {
		Pattern pattern = Pattern.compile("([^:]+):(.*)");
		//pattern.
		Matcher matcher = pattern.matcher(expr);
		matcher.find();
		String cmd = matcher.group(1);
		String arg = matcher.group(2);
		if (cmd.equals("def")) {
			item.addFilter(new DefaultFilter(arg));
		} else if (cmd.equals("conv")) {
			item.addFilter(new ConvertFilter(arg));
		} else if (cmd.equals("subst")) {
			item.addFilter(new SubstitutionFilter(arg));
		} else if (cmd.equals("prefix")) {
			item.addFilter(new PrependFilter(arg));
		} else if (cmd.equals("highway-symbol")) {
			item.addFilter(new HighwaySymbolFilter(arg));
		} else if (cmd.equals("height")) {
			item.addFilter(new HeightFilter(arg));
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("'");
		for (ValueItem v : items) {
			sb.append(v);
		}
		sb.append("'");
		return sb.toString();
	}
}

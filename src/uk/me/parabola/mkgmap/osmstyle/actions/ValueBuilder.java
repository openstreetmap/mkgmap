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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * Build a value that can have tag values substituted in it.
 *
 * @author Steve Ratcliffe
 * @author Toby Speight
 */
public class ValueBuilder {
	private static final Pattern[] FILTER_ARG_PATTERNS = {
			Pattern.compile("[ \t]*([^: \\t|]+:\"[^\"]+\")[ \t]*"),
			Pattern.compile("[ \t]*([^: \\t|]+:'[^']+')[ \t]*"),

			// This must be last
			Pattern.compile("[ \t]*([^: \\t|]+:[^|]*)"),
			Pattern.compile("[ \t]*([^: \\t|]+)"),
	};

	private static final Pattern NAME_ARG_SPLIT = Pattern.compile("([^:]+)(?::[\"']?(.*?)[\"']?)?", Pattern.DOTALL);

	private final List<ValueItem> items = new ArrayList<>();
	private final boolean completeCheck;

	public ValueBuilder(String pattern) {
		this (pattern, true);
	}
	
	public ValueBuilder(String pattern, boolean completeCheck) {
		this.completeCheck =completeCheck;
		compile(pattern);
	}

	/**
	 * Build this string if all the tags that are required are available.
	 *
	 * If a tag does not exist then the whole string is rejected.  This allows
	 * you to make conditional replacements.
	 *
	 * @param el Used as a source of tags.
	 * @param lel Used as a source of local tags.
	 * @return The built string if all required tags are available.  If any
	 * are missing then it returns null.
	 */
	public String build(Element el, Element lel) {
		if (completeCheck) {
			// Check early for no match and return early
			for (ValueItem item : items) {
				if (item.getValue(el, lel) == null)
					return null;
			}
		}

		// If we get here we can build the final string.  A common case
		// is that there is just one, so return it directly.
		if (items.size() == 1)
			return items.get(0).getValue(el, lel);

		// OK we have to construct the result.
		StringBuilder sb = new StringBuilder();
		for (ValueItem item : items)
			sb.append(item.getValue(el, lel));

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

		char state = '\0';
		StringBuilder text = new StringBuilder();
		StringBuilder tagname = null;
		for (char c : in.toCharArray()) {
			switch (state) {
			case '\0':
				if (c == '$') {
					state = '$';
				} else
					text.append(c);
				break;
			case '$':
				switch (c) {
				case '{':
				case '(':
					if (text.length() > 0) {
						items.add(new ValueItem(text.toString()));
						text.setLength(0);
					}
					tagname = new StringBuilder();
					state = (c == '{') ? '}' : ')';
					break;
				default:
					state = '\0';
					text.append('$');
					text.append(c);
				}
				break;
			case '}':
			case ')':
				if (c == state) {
					//noinspection ConstantConditions
					assert tagname != null;
					addTagValue(tagname.toString(), c == ')');
					state = '\0';
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

	private void addTagValue(String tagname, boolean is_local) {
		ValueItem item = new ValueItem();
		if (tagname.contains("|")) {
			String[] parts = tagname.split("[ \t]*\\|", 2);
			assert parts.length > 1;

			item.setTagname(parts[0], is_local);

			String s = parts[1];

			int start = 0;
			int end = s.length();
			while (start < end) {
				Matcher matcher = null;
				for (Pattern p : FILTER_ARG_PATTERNS) {
					matcher = p.matcher(s);
					matcher.region(start, end);
					if (matcher.lookingAt())
						break;
				}

				if (matcher != null && matcher.lookingAt()) {
					start = matcher.end() + 1;
					addFilter(item, matcher.group(1));
				} else {
					assert false;
					start = end;
				}
			}
		} else {
			item.setTagname(tagname, is_local);
		}
		items.add(item);
	}

	private void addFilter(ValueItem item, String expr) {
		Matcher matcher = NAME_ARG_SPLIT.matcher(expr);

		matcher.matches();
		String cmd = matcher.group(1);
		String arg = matcher.group(2);

		switch (cmd) {
		case "def":
			item.addFilter(new DefaultFilter(arg));
			break;
		case "conv":
			item.addFilter(new ConvertFilter(arg));
			break;
		case "subst":
			item.addFilter(new SubstitutionFilter(arg));
			break;
		case "prefix":
			item.addFilter(new PrependFilter(arg));
			break;
		case "highway-symbol":
			item.addFilter(new HighwaySymbolFilter(arg));
			break;
		case "height":
			item.addFilter(new HeightFilter(arg));
			break;
		case "not-equal":
			item.addFilter(new NotEqualFilter(arg));
			break;
		case "substring":
			item.addFilter(new SubstringFilter(arg));
			break;
		case "part":
			item.addFilter(new PartFilter(arg));
			break;
		case "ascii":
			item.addFilter(new TransliterateFilter("ascii"));
			break;
		case "latin1":
			item.addFilter(new TransliterateFilter("latin1"));
			break;
		case "country-ISO":
			item.addFilter(new CountryISOFilter());
			break;
		case "not-contained":
			item.addFilter(new NotContainedFilter(arg));
			break;
		default:
			throw new SyntaxException(String.format("Unknown filter '%s'", cmd));
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

	public Set<String> getUsedTags() {
		Set<String> set = new HashSet<>();
		for (ValueItem v : items) {
			String tagname = v.getTagname();
			if (tagname != null)
				set.add(tagname);
		}
		return set;
	}
}

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
 * Create date: 15-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Add a tag, optionally changing it if it already exists.  The value that
 * the tag is set to can have replacements from the current tags.
 *
 * @author Steve Ratcliffe
 */
public class AddTagAction implements Action {
	private boolean modify;
	private final String tag;
	private final String value;

	/**
	 * Create an action to add the given tag and value.  If the tag
	 * already has a value then nothing is done.
	 */
	public AddTagAction(String tag, String value) {
		this.tag = tag;
		this.value = value;
	}

	/**
	 * Create an action to add the given tag with a value.
	 * If the modify flag is true, then we change the tag if it
	 * already exists.
	 */
	public AddTagAction(String tag, String value, boolean modify) {
		this.modify = modify;
		this.tag = tag;
		this.value = value;
	}

	public void perform(Element el) {
		String tv = el.getTag(tag);
		if (tv != null && !modify)
			return;

		String newval = resolveVars(value, el);
		if (newval != null)
			el.addTag(tag, newval);
	}

	/**
	 * A tag value can contain variables that are the values of other tags.
	 * This is especially useful for 'name', as you might want to set it to
	 * some combination of other tags.
	 *
	 * If a tag does not exist then the whole string is rejected.  This allows
	 * you to make conditional replacements.
	 * @param in An input string that may contain tag replacement introduced
	 * by ${tagname}.
	 *
	 * @return If there are no replacement values, the same string as was passed
	 * in.  If all the replacement values exist, then the string with the
	 * values all replaced.  If any replacement tagname does not exist
	 * then returns null.
	 */
	private String resolveVars(String in, Element el) {
		if (!in.contains("$"))
			return in;

		StringBuilder sb = new StringBuilder();
		int state = 0;
		StringBuilder tagname = null;
		for (char c : in.toCharArray()) {
			switch (state) {
			case 0:
				if (c == '$')
					state = 1;
				else
					sb.append(c);
				break;
			case 1:
				if (c == '{') {
					tagname = new StringBuilder();
					state = 2;
				} else {
					state = 0;
					sb.append(c);
				}
				break;
			case 2:
				if (c == '}') {
					//noinspection ConstantConditions
					assert tagname != null;
					String val = el.getTag(tagname.toString());
					if (val == null)
						return null;
					sb.append(val);
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

		return sb.toString();
	}
}

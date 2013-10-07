/*
 * Copyright (C) 2013.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Add one value to all mkgmap access tags, optionally changing them if they already exist.  
 * The value can have replacements from the current tags.
 *
 * @author WanMil
 */
public class AddAccessAction implements Action {
	private final boolean modify;
	private final List<ValueBuilder> values = new ArrayList<ValueBuilder>();

	// The tags used to build the value.
	private Element valueTags;

	/**
	 * Create an action to add the given value to all mkgmap access tags.
	 * If the modify flag is false, then only those tags are set that do
	 * not already exist.
	 */
	public AddAccessAction(String value, boolean modify) {
		this.modify = modify;
		this.values.add(new ValueBuilder(value));
	}

	public void perform(Element el) {
		// 1st build the value
		Element tags = valueTags!=null? valueTags: el;
		String accessValue = null;
		for (ValueBuilder value : values) {
			accessValue = value.build(tags, el);
			if (accessValue != null) {
				break;
			}
		}
		if (accessValue == null) {
			return;
		}
		for (String accessTag : StyledConverter.ACCESS_TAGS) {
			setTag(el, accessTag, accessValue);
		}
	}
	
	/**
	 * Set the tag of the given element. In case the modify flag
	 * is {@code true} the tag is always set. Otherwise the tag
	 * is set only if it does not already exist.
	 * @param el OSM element
	 * @param tag the tag name
	 * @param value the value to be set
	 */
	private void setTag(Element el, String tag, String value) {
		String tv = el.getTag(tag);
		if (tv != null && !modify)
			return;

		el.addTag(tag, value);
	}

	/**
	 * Adds a value expression.
	 * @param value the value expression
	 */
	public void add(String value) {
		values.add(new ValueBuilder(value));
	}

	public void setValueTags(Element valueTags) {
		this.valueTags = valueTags;
	}

	public Set<String> getUsedTags() {
		Set<String> set = new HashSet<String>();

		if (values != null) {
			for (ValueBuilder vb : values) {
				set.addAll(vb.getUsedTags());
			}
		}
		return set;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(modify ? "setaccess " : "addaccess ");
		for (int i = 0; i < values.size(); i++) {
			sb.append(values.get(i));
			if (i < values.size() - 1)
				sb.append(" | ");
		}
		sb.append(';');
		return sb.toString();
	}
}

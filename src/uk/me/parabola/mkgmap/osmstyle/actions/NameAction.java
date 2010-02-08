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

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Set the name on the given element.  The tags of the element may be
 * used in setting the name.
 *
 * We have a list of possible substitutions.
 *
 * @author Steve Ratcliffe
 */
public class NameAction implements Action {

	private final List<ValueBuilder> names = new ArrayList<ValueBuilder>();

	/**
	 * search for the first matching name pattern and set the element name
	 * to it.
	 *
	 * If the element name is already set, then nothing is done.
	 *
	 * @param el The element on which the name may be set.
	 */
	public void perform(Element el) {
		if (el.getName() != null)
			return;
		
		for (ValueBuilder vb : names) {
			String s = vb.build(el, el);
			if (s != null) {
				el.setName(s);
				break;
			}
		}
	}

	public void add(String val) {
		names.add(new ValueBuilder(val));
	}

	public Set<String> getUsedTags() {
		Set<String> set = new HashSet<String>();
		if (names != null) {
			for (ValueBuilder vb : names) {
				set.addAll(vb.getUsedTags());
			}
		}
		return set;
	}

	public String toString() {
		StringBuilder sb = new  StringBuilder();
		sb.append("name ");
		for (ValueBuilder vb : names) {
			sb.append(vb);
			sb.append(" | ");
		}
		sb.setLength(sb.length() - 1);
		return sb.toString();
	}
}

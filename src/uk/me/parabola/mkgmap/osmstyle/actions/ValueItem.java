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
 * Create date: 07-Dec-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Part of a substitution string.  This can represent a constant
 * value or value that is taken from the element tags.
 * 
 * @author Steve Ratcliffe
 */
public class ValueItem {
	private String tagname;
	private ValueFilter filter;
	private String value;

	public ValueItem() {
	}

	public ValueItem(String value) {
		this.value = value;
	}

	public String getValue(Element el) {
		if (value != null)
			return value;   // already known
		
		if (tagname != null) {
			String tagval = el.getTag(tagname);
			if (filter != null)
				value = filter.filter(tagval);
			else
				value = tagval;
		}

		return value;
	}

	public void addFilter(ValueFilter f) {
		if (filter == null)
			filter = f;
		else
			filter.add(f);
	}

	public void setTagname(String tagname) {
		this.tagname = tagname;
	}
}

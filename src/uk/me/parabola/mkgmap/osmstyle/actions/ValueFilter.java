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
 * Filter a value.  This is used for special effects and not for the majority
 * of substitutions.
 *
 * Takes a value, applies the filter and returns the result.  Filters
 * can be chained.
 * 
 * @author Steve Ratcliffe
 */
public abstract class ValueFilter {
	private ValueFilter next;

	public final String filter(String value, Element el) {
		String res = doFilter(value, el);
		if (next != null)
			res = next.doFilter(res, el);
		return res;
	}

	protected abstract String doFilter(String value, Element el);

	public void add(ValueFilter f) {
		if (next == null)
			next = f;
		else
			next.add(f);
	}
}

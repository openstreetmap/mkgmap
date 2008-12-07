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

	public final String filter(String value) {
		String res = doFilter(value);
		if (next != null)
			res = next.doFilter(res);
		return res;
	}

	protected abstract String doFilter(String value);

	public void add(ValueFilter f) {
		if (next == null)
			next = f;
		else
			next.add(f);
	}
}

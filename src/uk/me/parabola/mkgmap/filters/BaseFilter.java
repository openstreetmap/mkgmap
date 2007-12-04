/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Dec 3, 2007
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.mkgmap.general.MapElement;

/**
 * A base filter to use that has empty implementations of methods that are not
 * always used.
 *
 * @author Steve Ratcliffe
 */
public class BaseFilter implements MapFilter {
	/**
	 * Empty implementation of the init function.
	 *
	 * @param config Configuration information, giving parameters of the map
	 *               level that is being produced through this filter.
	 */
	public void init(FilterConfig config) {
	}

	/**
	 * Empty implementation.
	 *
	 * @param element A map element.
	 * @param next	This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		throw new UnsupportedOperationException();
	}
}

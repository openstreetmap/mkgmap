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
 * Create date: Dec 1, 2007
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;

/**
 * Filter for removing empty elements and degenerate elements, for example
 * lines or shapes with just one point.
 * 
 * @author Steve Ratcliffe
 */
public class RemoveEmpty implements MapFilter {
	private static final Logger log = Logger.getLogger(RemoveEmpty.class);

	public void init(FilterConfig config) {
	}

	/**
	 * If this is a line (or a shape, which extends a line) then we check
	 * to see if it is empty or only a single point.  If it is then it
	 * is dropped.
	 *
	 * @param element A map element.
	 * @param next	This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		if (element instanceof MapLine) {
			MapLine mapLine = (MapLine) element;
			if (mapLine.getPoints().size() <= 1) {
				if (log.isDebugEnabled())
					log.debug("dropping degenerate element");
				return;
			}
		}
		next.doFilter(element);
	}
}

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

import uk.me.parabola.mkgmap.general.MapElement;

/**
 * Used to set up a filter chain for adding map elements to an area of the map
 * at a given level.
 *
 * <p>Although this is based on servlet filters, there is a complication in
 * that we want to be able to split up an element.
 * 
 * @author Steve Ratcliffe
 */
public interface MapFilterChain {

	/**
	 * Pass the element on to the next filter in the chain.  If there are no
	 * more then it will be saved for adding to the map.
	 *
	 * @param element The map element.
	 */
	public void doFilter(MapElement element);

}

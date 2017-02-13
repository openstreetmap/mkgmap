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
package uk.me.parabola.mkgmap.build;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.FilterConfig;
import uk.me.parabola.mkgmap.filters.MapFilter;
import uk.me.parabola.mkgmap.filters.MapFilterChain;
import uk.me.parabola.mkgmap.general.MapElement;

import java.util.ArrayList;
import java.util.List;

/**
 * This calls all the filters that are applied to an element as it is added to
 * the map at a particular level.
 *
 * @author Steve Ratcliffe
 */
public class LayerFilterChain implements MapFilterChain {
	private static final Logger log = Logger.getLogger(LayerFilterChain.class);
	
	// The filters that will be applied to the element.
	private List<MapFilter> filters = new ArrayList<MapFilter>();

	// The position in the filter list.
	private int position;

	private final FilterConfig config;

	public LayerFilterChain(FilterConfig config) {
		this.config = config;
	}

	public void doFilter(MapElement element) {
		int nfilters = filters.size();

		log.debug("doing filter pos=", position, "out of=", nfilters);
		if (position >= nfilters)
			return;
		
		MapFilter f = filters.get(position++);
		try {
			f.doFilter(element, this);
			// maintain chain position for repeated calls in the split filters 
			position--; 
		} catch (RuntimeException e) {
			position--; // maintain position
			throw e;
		}
	}

	/**
	 * Start the filtering process for an element.
	 * @param element The element to add to the map.
	 */
	void startFilter(MapElement element) {
		position = 0;
		doFilter(element);
	}

	/**
	 * Add a filter to this chain.
	 *
	 * @param filter Filter to added at the end of the chain.
	 */
	public void addFilter(MapFilter filter) {
		assert config != null;

		filter.init(config);
		filters.add(filter);
	}
}

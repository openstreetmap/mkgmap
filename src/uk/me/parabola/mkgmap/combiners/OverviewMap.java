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
 * Create date: 25-Sep-2007
 */
package uk.me.parabola.mkgmap.combiners;

import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapCollector;

/**
 * This is the interface that is used to create the overview map.  We will then
 * read back the map via a LoadableMapDataSource.
 * 
 * @author Steve Ratcliffe
 */
public interface OverviewMap extends LoadableMapDataSource, MapCollector {

	/**
	 * Add a copyright string to the map.
	 *
	 * @param cw The string to add.
	 */
	public void addCopyright(String cw);

	/**
	 * Get the 'shift' value of the overview map.  This is used for rounding
	 * coordinates of objects that are added to the map.
	 *
	 * @return An integer shift value, that is 24-bits.
	 */
	public int getShift();
}

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
package uk.me.parabola.mkgmap.reader.overview;

import uk.me.parabola.mkgmap.general.LoadableMapDataSource;

import java.util.Properties;

/**
 * This is the interface that is used to create the overview map.  We will then
 * read back the map via a LoadableMapDataSource.
 * 
 * @author Steve Ratcliffe
 */
public interface OverviewMap {

	/**
	 * Add this data source to the overview map.  By this we mean that we extract
	 * some high level features from the map, to be decided, but probably capital
	 * cities sea, major roads etc.
	 *
	 * These features are then added to the overview data source so that they
	 * can be read back and used to create the overview map.
	 *
	 * Of course we will be saving the bounds too for the overview map bounds.
	 *
	 * @param src One of the component maps that will form part of the overview.
	 * @param props Current options.
	 */
	public void addMapDataSource(LoadableMapDataSource src, Properties props);
}

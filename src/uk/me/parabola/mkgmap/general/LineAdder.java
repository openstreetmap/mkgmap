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
 * Create date: 10-Dec-2008
 */
package uk.me.parabola.mkgmap.general;

/**
 * For adding a line to the map model.  Created for the overlay feature.
 *
 * @author Steve Ratcliffe
 */
public interface LineAdder {

	/**
	 * Add the given line.  This will usually be to a MapCollector.
	 */
	public void add(MapLine element);
}

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
 * Create date: 10-Nov-2008
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.osmstyle.LineAdder;

/**
 * For clipping lines and polygons.
 * @author Steve Ratcliffe
 */
public interface Clipper {
	public static final Clipper NULL_CLIPPER = new NullClipper();

	/**
	 * Clip a line and add the resulting line or lines (if any) to the
	 * collector.
	 */
	public void clipLine(MapLine line, LineAdder adder);

	/**
	 * Clip a polygon and add the resulting shapes to the collector.
	 */
	public void clipShape(MapShape shape, MapCollector collector);

	/**
	 * 'Clip' a point - return true if the point is within the clipped region.
	 */
	public boolean contains(Coord location);
}

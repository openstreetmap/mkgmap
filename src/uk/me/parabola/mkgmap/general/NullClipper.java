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
 * Does no clipping and just adds the elements directly.
 * 
 * @author Steve Ratcliffe
 */
public class NullClipper implements Clipper {
	public void clipLine(MapLine line, LineAdder adder) {
		adder.add(line);
	}

	public void clipShape(MapShape shape, MapCollector collector) {
		collector.addShape(shape);
	}

	public boolean contains(Coord location) {
		return true;
	}
}

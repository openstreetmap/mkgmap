/**
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Author: steve
 * Date: 24-Dec-2006
 */
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.imgfmt.app.Coord;

/**
 * A node with its own identity.  This is a node that is not just being used for
 * its coordinates (in which case we just use {@link Coord} but has another
 * use for example it might be a hospital or a candlestick maker.
 *
 * @author Steve Ratcliffe
 */
class Node extends Element {
	private final Coord location;

	Node(long id, Coord co) {
		location = co;
		setId(id);
	}

	public Coord getLocation() {
		return location;
	}
}

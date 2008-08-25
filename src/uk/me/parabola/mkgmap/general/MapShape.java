/**
 * Copyright (C) 2006 Steve Ratcliffe
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Author: steve
 * Date: 23-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.net.RoadDef;


/**
 * A shape or polygon is just the same as a line really as far as I can tell.
 * There are some things that you cannot do with them semantically.
 *
 * @author Steve Ratcliffe.
 */
public class MapShape extends MapLine {// So top code can link objects from here
private RoadDef roadDef;

	public MapShape() {
	}

	/**
	 * Copy from another element.  Note that this constructor takes a line
	 * and not a shape.  It should however actually be a shape.
	 * @param orig The shape to copy.
	 */
	public MapShape(MapLine orig) {
		super(orig);
	}

	public void setDirection(boolean direction) {
		throw new IllegalArgumentException(
				"can't set a direction on a polygon");
	}

}

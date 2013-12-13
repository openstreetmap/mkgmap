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
 * Create date: 13-Jul-2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.imgfmt.app.Coord;

/**
 * A coordinate that has a POI
 *
 * @author Steve Ratcliffe
 */
public class CoordPOI extends Coord {
	private Node node;
	private boolean used;

	/**
	 * Construct from other coord instance, copies the lat/lon values in high precision,
	 * nothing else.
 
	 */
	public CoordPOI(Coord co) {
		super(co);
	}

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	public void setUsed(boolean b) {
		this.used = b;
	}
	public boolean isUsed() {
		return used;
	}
}

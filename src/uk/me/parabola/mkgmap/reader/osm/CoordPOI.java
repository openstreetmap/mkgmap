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
	private final Node node;

	/**
	 * Construct from co-ordinates that are already in map-units.
	 *
	 * @param latitude The latitude in map units.
	 * @param longitude The longitude in map units.
	 * @param node The Node that represents the POI.
	 */
	public CoordPOI(int latitude, int longitude, Node node) {
		super(latitude, longitude);
		this.node = node;
	}

	public Node getNode() {
		return node;
	}
}

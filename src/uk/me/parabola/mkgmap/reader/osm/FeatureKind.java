/*
 * Copyright (C) 2012.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package uk.me.parabola.mkgmap.reader.osm;

/**
 * Used to classify objects into points, lines, etc.
 *
 * @author Steve Ratcliffe
 */
public enum FeatureKind {
	POINT,
	POLYLINE,
	POLYGON,

	// These are not really feature kinds, as there is no corresponding Garmin object.
	RELATION,
	ALL,
}

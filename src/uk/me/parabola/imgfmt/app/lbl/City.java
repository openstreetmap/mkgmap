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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.Label;

/**
 * A city is in a region.  It also has (or can have anyway) a reference to
 * an indexed point within the map itself.
 * 
 * @author Steve Ratcliffe
 */
public class City {
	private Region region;

	// The location of the city.  These could both be zero if we are using a
	// label instead.
	private char subdivision;
	private byte pointIndex;

	// You can have either a label or a subdivision and point.  This will be
	// null if the location is being specified.
	private Label label;
}

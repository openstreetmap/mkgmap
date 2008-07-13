/*
 * Copyright (C) 2008
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
 * Create date: 07-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

/**
 * An arc joins two nodes within a {@link RouteCenter}.  There are also
 * links between nodes in different centers.
 *
 * @author Steve Ratcliffe
 */
public class RouteArc {
	private int nodeId;
	private int roadId;

	private byte initialHeading;
	private byte endHeading;
}

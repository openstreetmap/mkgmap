/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
/* Create date: 21-Feb-2009 */
package func.lib;

import java.util.List;

import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.RoadNetwork;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.trergn.Overview;

/**
 * A map source that allows us to create a map to specification without
 * having a .osm file.
 * 
 * @author Steve Ratcliffe
 */
public class TestDataSource implements MapDataSource {
	public Area getBounds() {
		return null;
	}

	public List<MapPoint> getPoints() {
		return null;
	}

	public List<MapLine> getLines() {
		return null;
	}

	public List<MapShape> getShapes() {
		return null;
	}

	public RoadNetwork getRoadNetwork() {
		return null;
	}

	public List<Overview> getOverviews() {
		return null;
	}
}

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
 * Create date: 20-Jan-2007
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Zoom;
import uk.me.parabola.log.Logger;

import java.util.List;

/**
 * @author Steve Ratcliffe
 */
public class MapSplitter {
	private static final Logger log = Logger.getLogger(MapSplitter.class);

	private final MapDataSource mapSource;

	private static final int MAX_DIVISION_SIZE = 1000;
//	private static final int MAX_DIVISION_SIZE = 0x7fff;

	// This is the zoom in terms of pixels per coordinate.  So 24 is the highest
	// zoom
	private int zoom;

	/**
	 * Creates a list of map areas and keeps splitting them down until they
	 * are small enough.  There is both a maximum size to an area and also
	 * a maximum number of things that will fit inside each division.
	 *
	 * Since these are not well defined (it all depends on how complicated the
	 * features are etc), we shall underestimate the maximum sizes and probably
	 * make them configurable.
	 *
	 * @param mapSource The input map data source.
	 * @param zoom The zoom level that we need to split for.
	 */
	public MapSplitter(MapDataSource mapSource, Zoom zoom) {
		this.mapSource = mapSource;
		this.zoom = zoom.getBitsPerCoord();

		MapArea ma = init(mapSource);
	}

	private void split() {
		Area bounds = mapSource.getBounds();
		log.debug("orig area", bounds);

		int width = bounds.getWidth();
		int height = bounds.getHeight();
		log.debug("width", width, "height", height);

		// There is an absolute maximum size that a division can be.  Make sure
		// that we are well inside that.
		int xsplit = 1;
		if (width > MAX_DIVISION_SIZE)
			xsplit = width/MAX_DIVISION_SIZE + 1;

		int ysplit = 1;
		if (height > MAX_DIVISION_SIZE)
			ysplit = height / MAX_DIVISION_SIZE + 1;

		Area[] areas;
		if (xsplit > 1 || ysplit > 1) {
			areas = bounds.split(xsplit, ysplit);
		} else {
			areas = new Area[1];
			areas[0] = bounds;
		}
	}

	// divide into minimum size areas
	// allocate ways and points to each area
	// for each area
	//   if two many points or ways
	//      split into 4
	//      re-allocate to new areas
	//      continue

	private MapArea init(MapDataSource src) {
		List<MapPoint> points = src.getPoints();
		List<MapLine> lines = src.getLines();
		List<MapShape> shapes = src.getShapes();

		Area bounds = src.getBounds();
		MapArea ma = new MapArea(bounds, src);

		ma.setPoints(points);
		ma.setLines(lines);
		ma.setShapes(shapes);

		return ma;
	}
}

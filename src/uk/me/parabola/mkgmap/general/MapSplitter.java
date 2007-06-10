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
import java.util.ArrayList;

/**
 * The map must be split into subdivisions.  To do this we start off with
 * one of these MapAreas containing all of the map and split it up into
 * smaller and smaller areas until each area is below a maximum size and
 * contains fewer than a maximum number of map features.
 *
 * @author Steve Ratcliffe
 */
public class MapSplitter {
	private static final Logger log = Logger.getLogger(MapSplitter.class);

	private final MapDataSource mapSource;

	// There is an absolute largest size as offsets are in 16 bits, we are
	//  staying safely inside it however.
	private static final int MAX_DIVISION_SIZE = 0x3fff;

	// The maximum number of map features in a subdivision.  Note that there
	// not as such a maximum number, it is all about what will fit.  So we
	// chose a number that seems safe.
	private static final int MAX_FEATURE_NUMBER = 2500;//2000;

	private Zoom zoom;

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
		this.zoom = zoom;
	}

	public MapSplitter(MapDataSource src, Zoom zoom, LevelFilter filter) {
		this.mapSource = src;
		this.zoom = zoom;
	}

	/**
	 * This splits the map into a series of smaller areas.  There is both a
	 * maximum size and a maximum number of features that can be contained
	 * in a single area.
	 *
	 * @return An array of map areas, each of which is within the size limit
	 * and the limit on the number of features.
	 */
	public MapArea[] split() {
		Area bounds = mapSource.getBounds();
		log.debug("orig area", bounds);

		MapArea ma = initialArea(mapSource);
		MapArea[] areas = splitMaxSize(ma);

		// Now step through each area and see if any have too many map features
		// in them.  For those that do, we further split them.  This is done
		// recursively until everything fits.
		List<MapArea> alist = new ArrayList<MapArea>();
		addAreasToList(areas, alist);

		MapArea[] results = new MapArea[alist.size()];
		return alist.toArray(results);
	}

	/**
	 * Adds map areas to a list.  If an area has too many features, then it
	 * is split into 4 and this routine is called recusively to add the new
	 * areas.
	 *
	 * @param areas The areas to add to the list (and possibly split up).
	 * @param alist The list that will finally contain the complete list of
	 * map areas.
	 */
	private void addAreasToList(MapArea[] areas, List<MapArea> alist) {
		for (MapArea a : areas) {
			if (a.getCountForResolution(zoom.getResolution()) > MAX_FEATURE_NUMBER) {
				log.debug("splitting area", a);
				MapArea[] sublist = a.split(2, 2);
				addAreasToList(sublist, alist);
			} else {
				log.debug("adding area unsplit");
				alist.add(a);
			}
		}
	}

	/**
	 * Split the area into portions that have the maximum size.  There is a
	 * maximum limit to the size of a subdivision (16 bits or about 1.4 degrees
	 * at the most detailed zoom level).
	 *
	 * The size depends on the shift level.
	 *
	 * We are choosing a limit smaller than the real max to allow for
	 * uncertaintly about what happens with features that extend beyond the box.
	 *
	 * If the area is already small enough then it will be returned unchanged.
	 *
	 * @param mapArea The area that needs to be split down.
	 * @return An array of map areas.  Each will be below the max size.
	 */
	private MapArea[] splitMaxSize(MapArea mapArea) {
		Area bounds = mapArea.getBounds();

		int shift = zoom.getShiftValue();
		int width = bounds.getWidth() >> shift;
		int height = bounds.getHeight() >> shift;
		log.debug("shifted width", width, "shifted height", height);

		// There is an absolute maximum size that a division can be.  Make sure
		// that we are well inside that.
		int xsplit = 1;
		if (width > MAX_DIVISION_SIZE)
			xsplit = width / MAX_DIVISION_SIZE + 1;

		int ysplit = 1;
		if (height > MAX_DIVISION_SIZE)
			ysplit = height / MAX_DIVISION_SIZE + 1;

		MapArea[] areas = mapArea.split(xsplit, ysplit);
		return areas;
	}

	/**
	 * The initial area contains all the features of the map.
	 *
	 * @param src The map data source.
	 * @return The initial map area covering the whole area and containing
	 * all the map features that are visible.
	 */
	private MapArea initialArea(MapDataSource src) {
		return new MapArea(src);
	}
}

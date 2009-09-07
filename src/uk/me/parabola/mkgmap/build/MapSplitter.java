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
package uk.me.parabola.mkgmap.build;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.trergn.Zoom;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapDataSource;

/**
 * The map must be split into subdivisions.  To do this we start off with
 * one of these MapAreas containing all of the map and split it up into
 * smaller and smaller areas until each area is below a maximum size and
 * contains fewer than a maximum number of map features.
 *
 * @author Steve Ratcliffe
 */
class MapSplitter {
	private static final Logger log = Logger.getLogger(MapSplitter.class);

	private final MapDataSource mapSource;

	// There is an absolute largest size as offsets are in 16 bits, we are
	//  staying safely inside it however.
	private static final int MAX_DIVISION_SIZE = 0x7fff;

	// The maximum region size.  Note that the offset to the start of a section
	// has to fit into 16 bits, the end of the last section could be beyond the
	// 16 bit limit. Leave a little room for the region pointers
	private static final int MAX_RGN_SIZE = 0xfff8;

	// The maximum number of lines. NET points to lines in subdivision
	// using bytes.
	private static final int MAX_NUM_LINES = 0xff;

	private static final int MAX_NUM_POINTS = 0xff;

	private final Zoom zoom;

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
	MapSplitter(MapDataSource mapSource, Zoom zoom) {
		this.mapSource = mapSource;
		this.zoom = zoom;
	}

	/**
	 * This splits the map into a series of smaller areas.  There is both a
	 * maximum size and a maximum number of features that can be contained
	 * in a single area.
	 *
	 * This routine is not called recursively.
	 *
	 * @return An array of map areas, each of which is within the size limit
	 * and the limit on the number of features.
	 */
	public MapArea[] split() {
		log.debug("orig area", mapSource.getBounds());

		MapArea ma = initialArea(mapSource);
		MapArea[] areas = splitMaxSize(ma);

		// Now step through each area and see if any have too many map features
		// in them.  For those that do, we further split them.  This is done
		// recursively until everything fits.
		List<MapArea> alist = new ArrayList<MapArea>();
		addAreasToList(areas, alist, 0);

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
	private void addAreasToList(MapArea[] areas, List<MapArea> alist, int depth) {
		int res = zoom.getResolution();
		for (MapArea area : areas) {
			Area bounds = area.getBounds();
			int[] sizes = area.getSizeAtResolution(res);
			if(log.isInfoEnabled()) {
				String padding = depth + "                                            ";
				log.info(padding.substring(0, (depth + 1) * 2) + 
						 bounds.getWidth() + "x" + bounds.getHeight() +
						 ", res = " + res +
						 ", points = " + area.getNumPoints() + "/" + sizes[MapArea.POINT_KIND] +
						 ", lines = " + area.getNumLines() + "/" + sizes[MapArea.LINE_KIND] +
						 ", shapes = " + area.getNumShapes() + "/" + sizes[MapArea.SHAPE_KIND]);
			}

			if (area.getNumLines() > MAX_NUM_LINES ||
			    area.getNumPoints() > MAX_NUM_POINTS ||
				(sizes[MapArea.POINT_KIND] > MAX_RGN_SIZE &&
				 (area.hasIndPoints() || area.hasLines() || area.hasShapes())) ||
				(((sizes[MapArea.POINT_KIND] + sizes[MapArea.LINE_KIND]) > MAX_RGN_SIZE) &&
				 area.hasShapes())) {
				if (area.getBounds().getMaxDimention() > 100) {
					if (log.isDebugEnabled())
						log.debug("splitting area", area);
					MapArea[] sublist;
					if(bounds.getWidth() > bounds.getHeight())
						sublist = area.split(2, 1, res);
					else
						sublist = area.split(1, 2, res);
					addAreasToList(sublist, alist, depth + 1);
					continue;
				} else {
					log.warn("area too small to split", area);
				}
			}

			log.debug("adding area unsplit", ",has points" + area.hasPoints());

			MapArea[] sublist = area.split(1, 1, res);
			assert sublist.length == 1: sublist.length;
			//assert sublist[0].getAreaResolution() == res: sublist[0].getAreaResolution();
			alist.add(sublist[0]);
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
		if (log.isDebugEnabled())
			log.debug("shifted width", width, "shifted height", height);

		// There is an absolute maximum size that a division can be.  Make sure
		// that we are well inside that.
		int xsplit = 1;
		if (width > MAX_DIVISION_SIZE)
			xsplit = width / MAX_DIVISION_SIZE + 1;

		int ysplit = 1;
		if (height > MAX_DIVISION_SIZE)
			ysplit = height / MAX_DIVISION_SIZE + 1;

		return mapArea.split(xsplit, ysplit, zoom.getResolution());
	}

	/**
	 * The initial area contains all the features of the map.
	 *
	 * @param src The map data source.
	 * @return The initial map area covering the whole area and containing
	 * all the map features that are visible.
	 */
	private MapArea initialArea(MapDataSource src) {
		return new MapArea(src, zoom.getResolution());
	}
}

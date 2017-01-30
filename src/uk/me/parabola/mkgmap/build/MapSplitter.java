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
public class MapSplitter {
	private static final Logger log = Logger.getLogger(MapSplitter.class);

	private final MapDataSource mapSource;

	// There is an absolute largest size as offsets are in 16 bits, we are
	//  staying safely inside it however.
	public static final int MAX_DIVISION_SIZE = 0x7fff;

	// The maximum region size.  Note that the offset to the start of a section
	// has to fit into 16 bits, the end of the last section could be beyond the
	// 16 bit limit. Leave a little room for the region pointers
	public static final int MAX_RGN_SIZE = 0xfff8;

	// The maximum number of lines. NET points to lines in subdivision
	// using bytes.
	public static final int MAX_NUM_LINES = 0xff;

	public static final int MAX_NUM_POINTS = 0xff;

	// maximum allowed amounts of points/lines/shapes with extended types
	// real limits are not known but if these values are too large, data
	// goes missing (lines disappear, etc.)
	public static final int MAX_XT_POINTS_SIZE = 0xff00;
	public static final int MAX_XT_LINES_SIZE  = 0xff00;
	public static final int MAX_XT_SHAPES_SIZE = 0xff00;
	
	public static final int MIN_DIMENSION = 10; // just a reasonable value

	// The target number of estimated bytes for one area, smaller values
	// result in more and typically smaller areas and larger *.img files
	private static final int WANTED_MAX_AREA_SIZE = 0x3fff; 
	
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
	 * @param orderByDecreasingArea aligns subareas as powerOf2 and splits polygons into the subareas.  
	 * @return An array of map areas, each of which is within the size limit
	 * and the limit on the number of features.
	 */
	public MapArea[] split(boolean orderByDecreasingArea) {
		log.debug("orig area", mapSource.getBounds());

		MapArea ma = initialArea(mapSource, orderByDecreasingArea);
		MapArea[] origArea = {ma};
		MapArea[] areas = splitMaxSize(ma);
		if (areas == null) {
			log.warn("initial split returned null for ",ma);
			return origArea;
		}

		// Now step through each area and see if any have too many map features
		// in them.  For those that do, we further split them.  This is done
		// recursively until everything fits.
		List<MapArea> alist = new ArrayList<>();
		addAreasToList(areas, alist, 0);
		if (alist.isEmpty()) {
			return origArea;
		}

		MapArea[] results = new MapArea[alist.size()];
		return alist.toArray(results);
	}

	/**
	 * Adds map areas to a list.  If an area has too many features, then it
	 * is split into 2 and this routine is called recursively to add the new
	 * areas.
	 *
	 * @param areas The areas to add to the list (and possibly split up).
	 * @param alist The list that will finally contain the complete list of
	 * map areas.
	 */
	private void addAreasToList(MapArea[] areas, List<MapArea> alist, int depth) {
		int shift = zoom.getShiftValue();

		for (MapArea area : areas) {
			if (!area.hasData())
				continue;
			Area bounds = area.getBounds();
			int[] sizes = area.getEstimatedSizes();
			if(log.isInfoEnabled()) {
				String padding = depth + "                                                                      ";
				log.info(padding.substring(0, (depth + 1) * 2) + 
						 bounds.getWidth() + "x" + bounds.getHeight() +
						 ", points = " + area.getNumPoints() + "/" + sizes[MapArea.POINT_KIND] +
						 ", lines = " + area.getNumLines() + "/" + sizes[MapArea.LINE_KIND] +
						 ", shapes = " + area.getNumShapes() + "/" + sizes[MapArea.SHAPE_KIND]);
			}

			boolean wantSplit = false;
			boolean mustSplit = false;
			if (area.getNumLines() > MAX_NUM_LINES ||
				area.getNumPoints() > MAX_NUM_POINTS ||
				(sizes[MapArea.POINT_KIND] +
				 sizes[MapArea.LINE_KIND] +
				 sizes[MapArea.SHAPE_KIND]) > MAX_RGN_SIZE ||
				sizes[MapArea.XT_POINT_KIND] > MAX_XT_POINTS_SIZE ||
				sizes[MapArea.XT_LINE_KIND] > MAX_XT_LINES_SIZE ||
				sizes[MapArea.XT_SHAPE_KIND] > MAX_XT_SHAPES_SIZE)
				mustSplit = true;
			else if (bounds.getMaxDimension() > (MIN_DIMENSION << shift)) {
				int sumSize = 0;
				for (int s : sizes)
					sumSize += s;
				if (sumSize > WANTED_MAX_AREA_SIZE) {
					// area has more bytes than wanted, and large enough to split
					log.debug("splitting area because data size is larger than wanted:", sumSize);
					wantSplit = true;
				}
			}
    
			if (wantSplit || mustSplit) {
				if (bounds.getMaxDimension() > (MIN_DIMENSION << shift)) {
					log.debug("splitting area in half", area, mustSplit, wantSplit);
					MapArea[] sublist;
					if (bounds.getWidth() > bounds.getHeight())
						sublist = area.split(2, 1, bounds, false);
					else
						sublist = area.split(1, 2, bounds, false);
					if (sublist == null)
						log.error("SubDivision split failed at", area.getBounds().getCenter().toOSMURL());
					else {
						addAreasToList(sublist, alist, depth + 1);
						continue;
					}
				} else if (mustSplit) { // can't reduce size, so force more subdivisions
					log.debug("splitting area by contents", area);
					MapArea[] sublist = area.split(1, 1, bounds, true);
					addAreasToList(sublist, alist, depth + 1);
					continue;
				}
			}

			log.debug("adding area unsplit: has points", area.hasPoints());
			alist.add(area);
		}
	} // addAreasToList

    
	/**
	 * Split the area into portions that have the maximum size.  There is a
	 * maximum limit to the size of a subdivision (16 bits or about 1.4 degrees
	 * at the most detailed zoom level).
	 *
	 * The size depends on the shift level.
	 *
	 * We are choosing a limit smaller than the real max to allow for
	 * uncertainty about what happens with features that extend beyond the box.
	 *
	 * If the area is already small enough then it will be returned unchanged.
	 *
	 * @param mapArea The area that needs to be split down.
	 * @return An array of map areas.  Each will be below the max size.
	 */
	private MapArea[] splitMaxSize(MapArea mapArea) {
		Area bounds = mapArea.getFullBounds();

		int shift = zoom.getShiftValue();
		int width = bounds.getWidth() >> shift;
		int height = bounds.getHeight() >> shift;

		// There is an absolute maximum size that a division can be.  Make sure
		// that we are well inside that.
		int xsplit = 1;
		if (width > MAX_DIVISION_SIZE)
			xsplit = width / MAX_DIVISION_SIZE + 1;

		int ysplit = 1;
		if (height > MAX_DIVISION_SIZE)
			ysplit = height / MAX_DIVISION_SIZE + 1;

		bounds = mapArea.getBounds();
// getFullBounds in following causes primary area overlap which conflicts with concept of orderByDecreasingArea
// It is a little bit bigger because lines are allowed to go slightly out of their area		
		log.debug("splitMaxSize: bounds", bounds, "shift", shift, "width", width, "height", height, "xsplit", xsplit, "ysplit", ysplit);
		return mapArea.split(xsplit, ysplit, bounds, false);
	}

	/**
	 * The initial area contains all the features of the map.
	 *
	 * @param src The map data source.
	 * @param splitPolygonsIntoArea aligns subareas as powerOf2 and splits polygons into the subareas.  
	 * @return The initial map area covering the whole area and containing
	 * all the map features that are visible.
	 */
	private MapArea initialArea(MapDataSource src, boolean splitPolygonsIntoArea) {
		return new MapArea(src, zoom.getResolution(), splitPolygonsIntoArea);
	}
}

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
 * Create date: 01-Jul-2008
 */
package uk.me.parabola.mkgmap.general;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.Java2DConverter;

/**
 * Clip a polygon to the given bounding box.  This may result in more than
 * one polygon.
 *
 * @author Steve Ratcliffe
 */
public class PolygonClipper {
	private static final Logger log = Logger.getLogger(PolygonClipper.class);
	/**
	 * Clip the input polygon to the given area.
	 * @param bbox The bounding box.
	 * @param coords The coords of the polygon.
	 * @return Return null if the polygon is already completely inside the
	 * bounding box.
	 */
	public static List<List<Coord>> clip(Area bbox, List<Coord> coords) {
		if (bbox == null)
			return null;

		// If all the points are inside the box then we just return null
		// to show that nothing was done and the line can be used.  This
		// is expected to be the normal case.
		boolean foundOutside = false;
		for (Coord co : coords) {
			if (!bbox.contains(co)) {
				foundOutside = true;
				break;
			}
		}
		if (!foundOutside)
			return null;
		Long2ObjectOpenHashMap<Coord> map = new Long2ObjectOpenHashMap<>(coords.size());
		for (int i = 1; i < coords.size(); i++){
			Coord co = coords.get(i);
			map.put(Utils.coord2Long(co), co);
		}
		java.awt.geom.Area bbarea = Java2DConverter.createBoundsArea(bbox); 
		java.awt.geom.Area shape = Java2DConverter.createArea(coords);

		shape.intersect(bbarea);

		List<List<Coord>> shapes = Java2DConverter.areaToShapes(shape);
		int n=0;
		for (List<Coord> sh: shapes){
			n++;
			for (int i = 0; i < sh.size(); i++){
				Coord co = sh.get(i);
				Coord origCoord = map.get(Utils.coord2Long(co));
				if (origCoord != null)
					sh.set(i, origCoord);
				else 
					map.put(Utils.coord2Long(co), co);
			}
		}
		return shapes;
	}

}

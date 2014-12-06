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
 * Create date: Dec 6, 2007
 */
package uk.me.parabola.mkgmap.filters;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.util.Java2DConverter;

/**
 * @author Steve Ratcliffe
 */
public class PolygonSplitterBase extends BaseFilter {
	protected static final int MAX_SIZE = 0x7fff;
	private int shift;
	
	public void init(FilterConfig config) {
		shift = config.getShift();
	}
	
	/**
	 * Split the given shape and place the resulting shapes in the outputs list.
	 * @param shape The original shape (that is too big).
	 * @param outputs The output list.
	 */
	protected void split(MapShape shape, List<MapShape> outputs) {
		// TODO: use different algo which will keep track of holes which are
		// connected with the outer polygon
		
		// Convert to a awt area
		Area area = Java2DConverter.createArea(shape.getPoints());

		// Get the bounds of this polygon
		Rectangle bounds = area.getBounds();

		if (bounds.isEmpty())
			return;  // Drop it
		
		int half = 1 << (shift - 1);	// 0.5 shifted
		int mask = ~((1 << shift) - 1); // to remove fraction bits

		// Cut the bounding box into two rectangles. The position of the common
		// line is rounded to the current resolution.
		Rectangle r1;
		Rectangle r2;
		if (bounds.width > bounds.height) {
			int halfWidth = bounds.width / 2;
			if (shift != 0){
				halfWidth = (halfWidth + half) & mask;
				if (halfWidth == 0 || halfWidth == bounds.width)
					halfWidth = bounds.width / 2;
			}
			r1 = new Rectangle(bounds.x, bounds.y, halfWidth, bounds.height);
			r2 = new Rectangle(bounds.x + halfWidth, bounds.y, bounds.width - halfWidth, bounds.height);
		} else {
			int halfHeight = bounds.height / 2;
			if (shift != 0){
				halfHeight = (halfHeight + half) & mask;
				if (halfHeight== 0 || halfHeight == bounds.height)
					halfHeight = bounds.height / 2;
			}
			r1 = new Rectangle(bounds.x, bounds.y, bounds.width, halfHeight);
			r2 = new Rectangle(bounds.x, bounds.y + halfHeight, bounds.width, bounds.height - halfHeight);
		}

		// Now find the intersection of these two boxes with the original
		// polygon.  This will make two new areas, and each area will be one
		// (or more) polygons.
		Area clipper = new Area(r1);
		clipper.intersect(area);
		areaToShapes(shape, clipper, outputs);
		clipper = new Area(r2);
		clipper.intersect(area);
		areaToShapes(shape, clipper, outputs);
	}

	/**
	 * Convert the area back into {@link MapShape}s.  It is possible that the
	 * area is multiple discontinuous polygons, so you may append more than one
	 * shape to the output list.
	 *
	 * @param origShape The original shape, this is only used as a prototype to
	 * copy for the newly created shapes.
	 * @param area The area to be converted.
	 * @param outputs Used to hold output shapes.
	 */
	private void areaToShapes(MapShape origShape, Area area, List<MapShape> outputs) {
		List<List<Coord>> subShapePoints = Java2DConverter.areaToShapes(area);
		
		for (List<Coord> subShape : subShapePoints) {
			MapShape s = origShape.copy();
			s.setPoints(subShape);
			outputs.add(s);
		}
	}
}

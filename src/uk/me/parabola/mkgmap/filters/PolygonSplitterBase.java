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

import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.SutherlandHodgmanPolygonClipper;

/**
 * @author Steve Ratcliffe
 */
public class PolygonSplitterBase extends BaseFilter {
	protected static final int MAX_SIZE = 0x7fff;

	/**
	 * Split the given shape and place the resulting shapes in the outputs list.
	 * @param shape The original shape (that is too big).
	 * @param outputs The output list.
	 */
	protected void split(MapShape shape, List<MapShape> outputs) {
		Area bounds = shape.getBounds();
		// calculate a bit larger bbox so that we are sure that we clip only in the middle
		Area outerBounds = new Area(Math.max(Utils.toMapUnit(-90.0),bounds.getMinLat() - 1),
				Math.max(Utils.toMapUnit(-180.0), bounds.getMinLong() -1),
				Math.min(Utils.toMapUnit(90.0), bounds.getMaxLat() + 1),
				Math.min(Utils.toMapUnit(180.0), bounds.getMaxLong() + 1));
		Area[] clipRectangles;
		if (outerBounds.getWidth() > outerBounds.getHeight()) {
			clipRectangles = outerBounds.split(2, 1);
		} else {
			clipRectangles = outerBounds.split(1, 2);
		}
		for (int i = 0; i < clipRectangles.length; i++){
			List<Coord> clipped = SutherlandHodgmanPolygonClipper.clipPolygon(shape.getPoints(), clipRectangles[i]);
			if (clipped != null){
				MapShape s = shape.copy();
				s.setPoints(clipped);
				s.setClipped(true);
				outputs.add(s);
			}
		}
	}
}

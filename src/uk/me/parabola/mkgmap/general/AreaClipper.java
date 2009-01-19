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
 * Create date: 08-Nov-2008
 */
package uk.me.parabola.mkgmap.general;

import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * Clip objects to a bounding box.
 * 
 * TODO: migrate LineClipper and PolygonClipper into here and simplify.
 *
 * @author Steve Ratcliffe
 */
public class AreaClipper implements Clipper {
	private final Area bbox;

	public AreaClipper(Area bbox) {
		this.bbox = bbox;
	}

	public void clipLine(MapLine line, LineAdder collector) {
		List<List<Coord>> list = LineClipper.clip(bbox, line.getPoints());
		if (list == null) {
			collector.add(line);
		} else {
			for (List<Coord> lco : list) {
				MapLine nline = line.copy();
				nline.setPoints(lco);
				collector.add(nline);
			}
		}
	}

	public void clipShape(MapShape shape, MapCollector collector) {
		List<List<Coord>> list = PolygonClipper.clip(bbox, shape.getPoints());
		if (list == null) {
			collector.addShape(shape);
		} else {
			for (List<Coord> lco : list) {
				MapShape nshape = new MapShape(shape);
				nshape.setPoints(lco);
				collector.addShape(nshape);
			}
		}
	}

	public boolean contains(Coord location) {
		return bbox == null || bbox.contains(location);
	}
}

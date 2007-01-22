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
 * Create date: 21-Jan-2007
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Area;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve Ratcliffe
 */
public class MapArea {
	private static final int INITIAL_CAPACITY = 100;

	private Area area;

	private List<MapPoint> points = new ArrayList<MapPoint>(INITIAL_CAPACITY);
	private List<MapLine> lines = new ArrayList<MapLine>(INITIAL_CAPACITY);
	private List<MapShape> shapes = new ArrayList<MapShape>(INITIAL_CAPACITY);


	public MapArea(Area area) {
		this.area = area;
	}

	public void addPoint(MapPoint p) {
		points.add(p);
	}

	public void addLine(MapLine l) {
		lines.add(l);
	}

	public void addShape(MapShape s) {
		shapes.add(s);
	}

	public void setPoints(List<MapPoint> points) {
		this.points = points;
	}

	public void setLines(List<MapLine> lines) {
		this.lines = lines;
	}

	public void setShapes(List<MapShape> shapes) {
		this.shapes = shapes;
	}
}

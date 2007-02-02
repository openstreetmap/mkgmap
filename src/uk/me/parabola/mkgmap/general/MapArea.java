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
import uk.me.parabola.log.Logger;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve Ratcliffe
 */
public class MapArea {
	private static final Logger log = Logger.getLogger(MapArea.class);

	private static final int INITIAL_CAPACITY = 100;

	private Area area;

	private List<MapPoint> points = new ArrayList<MapPoint>(INITIAL_CAPACITY);
	private List<MapLine> lines = new ArrayList<MapLine>(INITIAL_CAPACITY);
	private List<MapShape> shapes = new ArrayList<MapShape>(INITIAL_CAPACITY);


	public MapArea(Area area) {
		this.area = area;
	}

	public MapArea(MapDataSource src) {
		this.area = src.getBounds();
		this.points = src.getPoints();
		this.lines = src.getLines();
		this.shapes = src.getShapes();
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

	//public void setPoints(List<MapPoint> points) {
	//	this.points = points;
	//}
	//
	//public void setLines(List<MapLine> lines) {
	//	this.lines = lines;
	//}
	//
	//public void setShapes(List<MapShape> shapes) {
	//	this.shapes = shapes;
	//}

	public MapArea[] split(int nx, int ny) {
		Area[] bounds = area.split(nx, ny);
		MapArea[] areas = new MapArea[nx * ny];
		for (int i = 0; i < nx * ny; i++) {
			areas[i] = new MapArea(bounds[i]);
			log.debug("area before", areas[i].area);
		}

		int xbase = bounds[0].getMinLong();
		int ybase = bounds[0].getMinLat();
		int dx = bounds[0].getWidth();
		int dy = bounds[0].getHeight();

		// Now sprinkle each map element into the correct map area.
		List<MapPoint> plist = this.points;
		for (MapPoint p : plist) {
			MapArea area1 = pickArea(nx, ny, areas, xbase, ybase, dx, dy, p);
			area1.addPoint(p);
		}

		List<MapLine> llist = this.lines;
		for (MapLine l : llist) {
			MapArea area1 = pickArea(nx, ny, areas, xbase, ybase, dx, dy, l);
			area1.addLine(l);
		}

		List<MapShape> elist = this.shapes;
		for (MapShape e : elist) {
			MapArea area1 = pickArea(nx, ny, areas, xbase, ybase, dx, dy, e);
			area1.addShape(e);
		}

		for (int i = 0; i < nx * ny; i++) {
			log.debug("area after", areas[i].area);
		}

		return areas;
	}

	public Area getBounds() {
		return area;
	}

	private MapArea pickArea(int nx, int ny, MapArea[] areas, int xbase, int ybase, int dx, int dy, MapElement e) {
		int x = e.getLocation().getLongitude();
		int y = e.getLocation().getLatitude();

		int xcell = (x - xbase) / dx;
		int ycell = (y - ybase) / dy;

		if (xcell >= nx)
			xcell = nx - 1;
		if (ycell >= ny)
			ycell = ny - 1;

		assert areas[xcell * ny + ycell].area.contains(e.getLocation());
		log.debug("adding", e.getLocation(), "to", xcell, "/", ycell, areas[xcell*ny+ycell].area);
		return areas[xcell * ny + ycell];
	}

	public int getPointCount() {
		return points.size();
	}
	public int getLineCount() {
		return lines.size();
	}
	public int getShapeCount() {
		return shapes.size();
	}

	public List<MapPoint> getPoints() {
		return points;
	}

	public List<MapLine> getLines() {
		return lines;
	}

	public List<MapShape> getShapes() {
		return shapes;
	}
}

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
 * A sub area of the map.  We have to divide the map up into areas to meet the
 * format of the Garmin map.  This class holds all the map elements that belong
 * to a particular area and provide a way of splitting areas into smaller ones.
 *
 * @author Steve Ratcliffe
 */
public class MapArea {
	private static final Logger log = Logger.getLogger(MapArea.class);

	private static final int INITIAL_CAPACITY = 100;

	private Area bounds;
	private int minLat = Integer.MAX_VALUE;
	private int minLon = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLon = Integer.MIN_VALUE;

	private List<MapPoint> points = new ArrayList<MapPoint>(INITIAL_CAPACITY);
	private List<MapLine> lines = new ArrayList<MapLine>(INITIAL_CAPACITY);
	private List<MapShape> shapes = new ArrayList<MapShape>(INITIAL_CAPACITY);


	public MapArea(Area area) {
		this.setBounds(area);
	}

	public MapArea(MapDataSource src) {
		this.setBounds(src.getBounds());
		this.points = src.getPoints();
		this.lines = src.getLines();
		this.shapes = src.getShapes();
	}

	public void addPoint(MapPoint p) {
		points.add(p);
		addToBounds(p.getBounds());
	}

	public void addLine(MapLine l) {
		lines.add(l);
		addToBounds(l.getBounds());

	}

	public void addShape(MapShape s) {
		shapes.add(s);
		addToBounds(s.getBounds());
	}

	public void addToBounds(Area a) {
		int l = a.getMinLat();
		if (l < minLat)
			minLat = l;
		l = a.getMaxLat();
		if (l > maxLat)
			maxLat = l;

		l = a.getMinLong();
		if (l < minLon)
			minLon = l;
		l = a.getMaxLong();
		if (l > maxLon)
			maxLon = l;
	}

	/**
	 * Split this area into several pieces. All the map elements are reallocated
	 * to the appropriate subarea.  Usually this instance would now be thrown
	 * away and the new sub areas used instead.
	 *
	 * @param nx The number of pieces in the x (longitude) direction.
	 * @param ny The number of pieces in the y direction.
	 * @return An array of the new MapArea's.
	 */
	public MapArea[] split(int nx, int ny) {
		Area[] bounds = getBounds().split(nx, ny);
		MapArea[] areas = new MapArea[nx * ny];
		for (int i = 0; i < nx * ny; i++) {
			areas[i] = new MapArea(bounds[i]);
			if (log.isDebugEnabled())
				log.debug("area before", areas[i].getBounds());
		}

		int xbase = bounds[0].getMinLong();
		int ybase = bounds[0].getMinLat();
		int dx = bounds[0].getWidth();
		int dy = bounds[0].getHeight();

		// Now sprinkle each map element into the correct map area.
		List<MapPoint> plist = this.points;
		for (MapPoint p : plist) {
			MapArea area1 = pickArea(areas, p, xbase, ybase, nx, ny, dx, dy);
			area1.addPoint(p);
		}

		List<MapLine> llist = this.lines;
		for (MapLine l : llist) {
			MapArea area1 = pickArea(areas, l, xbase, ybase, nx, ny, dx, dy);
			area1.addLine(l);
		}

		List<MapShape> elist = this.shapes;
		for (MapShape e : elist) {
			MapArea area1 = pickArea(areas, e, xbase, ybase, nx, ny, dx, dy);
			area1.addShape(e);
		}

		if (log.isDebugEnabled()) {
			for (int i = 0; i < nx * ny; i++) {
				log.debug("area after", areas[i].getBounds());
			}
		}

		return areas;
	}

	public Area getBounds() {
		return bounds;
	}

	public void setBounds(Area bounds) {
		this.bounds = bounds;
	}

	public Area getFullBounds() {
		return new Area(minLat, minLon, maxLat, maxLon);
	}

	/**
	 * Out of all the available areas, it picks the one that the map element
	 * should be placed into.
	 *
	 * Since we know how the area is divided (equal sizes) we can work out
	 * which one it fits into without stepping through them all and checking
	 * coordinates.
	 *
	 * @param areas The available areas to choose from.
	 * @param e The map element.
	 * @param xbase The x coord at the origin
	 * @param ybase The y coord of the origin
	 * @param nx number of divisions.
	 * @param ny number of divisions in y.
	 * @param dx The size of each division (x direction)
	 * @param dy The size of each division (y direction)
	 * @return The area from areas where the map element fits.
	 */
	private MapArea pickArea(MapArea[] areas, MapElement e, int xbase, int ybase, int nx, int ny, int dx, int dy) {
		int x = e.getLocation().getLongitude();
		int y = e.getLocation().getLatitude();

		int xcell = (x - xbase) / dx;
		int ycell = (y - ybase) / dy;

		if (xcell >= nx)
			xcell = nx - 1;
		if (ycell >= ny)
			ycell = ny - 1;

		if (log.isDebugEnabled()) {
			log.debug("adding", e.getLocation(), "to", xcell, "/", ycell,
					areas[xcell * ny + ycell].getBounds());
		}
		assert areas[xcell * ny + ycell].getBounds().contains(e.getLocation());
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

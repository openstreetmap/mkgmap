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
package uk.me.parabola.mkgmap.build;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.trergn.Overview;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.FilterConfig;
import uk.me.parabola.mkgmap.filters.LineSizeSplitterFilter;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
import uk.me.parabola.mkgmap.filters.MapFilterChain;
import uk.me.parabola.mkgmap.filters.PolygonSplitterFilter;
import uk.me.parabola.mkgmap.filters.PolygonSubdivSizeSplitterFilter;
import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.RoadNetwork;

/**
 * A sub area of the map.  We have to divide the map up into areas to meet the
 * format of the Garmin map.  This class holds all the map elements that belong
 * to a particular area and provides a way of splitting areas into smaller ones.
 *
 * It also acts as a map data source so that we can derive lower level
 * areas from it.
 *
 * @author Steve Ratcliffe
 */
public class MapArea implements MapDataSource {
	private static final Logger log = Logger.getLogger(MapArea.class);

	private static final int INITIAL_CAPACITY = 100;
	private static final int MAX_RESOLUTION = 24;

	public static final int POINT_KIND    = 0;
	public static final int LINE_KIND     = 1;
	public static final int SHAPE_KIND    = 2;
	public static final int XT_POINT_KIND = 3;
	public static final int XT_LINE_KIND  = 4;
	public static final int XT_SHAPE_KIND = 5;
	public static final int NUM_KINDS     = 6;

	// This is the initial area.
	private final Area bounds;

	// Because ways may extend beyond the bounds, we keep track of the actual
	// bounding box here.
	private int minLat = Integer.MAX_VALUE;
	private int minLon = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLon = Integer.MIN_VALUE;

	// The contents of the area.
	private final List<MapPoint> points = new ArrayList<MapPoint>(INITIAL_CAPACITY);
	private final List<MapLine> lines = new ArrayList<MapLine>(INITIAL_CAPACITY);
	private final List<MapShape> shapes = new ArrayList<MapShape>(INITIAL_CAPACITY);

	// amount of space required for the contents
	private final int[] sizes = new int[NUM_KINDS];

	private int nActivePoints;
	private int nActiveIndPoints;
	private int nActiveLines;
	private int nActiveShapes;

	/** The resolution that this area is at */
	private final int areaResolution;

	/**
	 * Create a map area from the given map data source.  This map
	 * area will have the same bounds as the map data source and
	 * will contain all the same map elements.
	 *
	 * @param src The map data source to initialise this area with.
	 * @param resolution The resolution of this area.
	 */
	public MapArea(MapDataSource src, int resolution) {
		this.areaResolution = 0;
		this.bounds = src.getBounds();
		addToBounds(bounds);

		for (MapPoint p : src.getPoints()) {
			if(bounds.contains(p.getLocation()))
				addPoint(p);
			else
				log.error("Point with type 0x" + Integer.toHexString(p.getType()) + " at " + p.getLocation().toOSMURL() + " is outside of the map area centred on " + bounds.getCenter().toOSMURL() + " width = " + bounds.getWidth() + " height = " + bounds.getHeight() + " resolution = " + resolution);
		}
		addLines(src, resolution);
		addPolygons(src, resolution);
	}

	/**
	 * Add the polygons, making sure that they are not too big.
	 * @param src The map data.
	 * @param resolution The resolution of this layer.
	 */
	private void addPolygons(MapDataSource src, final int resolution) {
		MapFilterChain chain = new MapFilterChain() {
			public void doFilter(MapElement element) {
				MapShape shape = (MapShape) element;
				shapes.add(shape);
				addToBounds(shape.getBounds());
				addSize(element, shape.hasExtendedType()? XT_SHAPE_KIND : SHAPE_KIND);
			}
		};

		PolygonSubdivSizeSplitterFilter filter = new PolygonSubdivSizeSplitterFilter();
		FilterConfig config = new FilterConfig();
		config.setResolution(resolution);
		config.setBounds(bounds);
		filter.init(config);

		for (MapShape s : src.getShapes()) {
			filter.doFilter(s, chain);
		}
	}

	/**
	 * Add the lines, making sure that they are not too big for resolution
	 * that we are working with.
	 * @param src The map data.
	 * @param resolution The current resolution of the layer.
	 */
	private void addLines(MapDataSource src, final int resolution) {
		// Split lines for size, such that it is appropriate for the
		// resolution that it is at.
		MapFilterChain chain = new MapFilterChain() {
			public void doFilter(MapElement element) {
				MapLine line = (MapLine) element;
				lines.add(line);
				addToBounds(line.getBounds());
				addSize(element, line.hasExtendedType()? XT_LINE_KIND : LINE_KIND);
			}
		};

		LineSizeSplitterFilter filter = new LineSizeSplitterFilter();
		FilterConfig config = new FilterConfig();
		config.setResolution(resolution);
		config.setBounds(bounds);
		filter.init(config);
		for (MapLine l : src.getLines()) {
			filter.doFilter(l, chain);
		}
	}

	/**
	 * Create an map area with the given initial bounds.
	 *
	 * @param area The bounds for this area.
	 * @param res The minimum resolution for this area.
	 */
	private MapArea(Area area, int res) {
		bounds = area;
		areaResolution = res;
		addToBounds(area);
	}

	/**
	 * Split this area into several pieces. All the map elements are reallocated
	 * to the appropriate subarea.  Usually this instance would now be thrown
	 * away and the new sub areas used instead.
	 *
	 * @param nx The number of pieces in the x (longitude) direction.
	 * @param ny The number of pieces in the y direction.
	 * @param resolution The resolution of the level.
	 * @param bounds the bounding box that is used to create the areas.  
	 * @return An array of the new MapArea's.
	 */
	public MapArea[] split(int nx, int ny, int resolution, Area bounds) {
		Area[] areas = bounds.split(nx, ny);
		MapArea[] mapAreas = new MapArea[nx * ny];
		log.info("Splitting area " + bounds + " into " + nx + "x" + ny + " pieces at resolution " + resolution);
		boolean useNormalSplit = true;
		while (true){
			for (int i = 0; i < nx * ny; i++) {
				mapAreas[i] = new MapArea(areas[i], resolution);
				if (log.isDebugEnabled())
					log.debug("area before", mapAreas[i].getBounds());
			}

			int xbase = areas[0].getMinLong();
			int ybase = areas[0].getMinLat();
			int dx = areas[0].getWidth();
			int dy = areas[0].getHeight();
			
			boolean[] used = new boolean[nx * ny];
			// Now sprinkle each map element into the correct map area.
			for (MapPoint p : this.points) {
				int pos = pickArea(mapAreas, p, xbase, ybase, nx, ny, dx, dy);
				mapAreas[pos].addPoint(p);
				used[pos] = true;
			}

			
			int areaIndex = 0;
			for (MapLine l : this.lines) {
				// Drop any zero sized lines.
				if (l.getRect().height <= 0 || l.getRect().width <= 0)
					continue;
				if (useNormalSplit)
					areaIndex = pickArea(mapAreas, l, xbase, ybase, nx, ny, dx, dy);
				else 
					areaIndex = ++areaIndex % mapAreas.length;
				mapAreas[areaIndex].addLine(l);
				used[areaIndex] = true;
			}

			for (MapShape e : this.shapes) {
				if (useNormalSplit)
					areaIndex = pickArea(mapAreas, e, xbase, ybase, nx, ny, dx, dy);
				else 
					areaIndex = ++areaIndex % mapAreas.length;
				mapAreas[areaIndex].addShape(e);
				used[areaIndex] = true;
			}
			// detect special case  
			if (useNormalSplit && mapAreas.length == 2 && bounds.getMaxDimension() < 2 * (MapSplitter.MIN_DIMENSION + 1)
					&& used[0] != used[1]
					&& (this.lines.size() > 1 || this.shapes.size() > 1)) {
				/* if we get here we probably have two or more identical long ways or
				 * big shapes with the same center point. We can safely distribute
				 * them equally to the two areas.  
				 */
				useNormalSplit = false;
				continue;
			} 
			return mapAreas;
		}
	}

	
	/**
	 * Get the full bounds of this area.  As lines and polylines are
	 * added then may go outside of the initial area.  When this happens
	 * we need to increase the size of the area.
	 *
	 * @return The full size required to hold all the included
	 * elements.
	 */
	public Area getFullBounds() {
		return new Area(minLat, minLon, maxLat, maxLon);
	}

	/**
	 * Get an estimate of the size of the RGN space that will be required to
	 * hold the elements
	 *
	 * @return Estimates of the max size that will be needed in the RGN file
	 * for the points/lines/shapes in this sub-division.
	 */
	public int[] getEstimatedSizes() {
		return sizes;
	}

	/**
	 * Get the initial bounds of this area.  That is the initial
	 * bounds before anything was added.
	 *
	 * @return The initial bounds as when it was created.
	 * @see #getFullBounds
	 */
	public Area getBounds() {
		return bounds;
	}

	/**
	 * Get a list of all the points.
	 *
	 * @return The points.
	 */
	public List<MapPoint> getPoints() {
		return points;
	}

	/**
	 * Get a list of all the lines.
	 *
	 * @return The lines.
	 */
	public List<MapLine> getLines() {
		return lines;
	}

	/**
	 * Get a list of all the shapes.
	 *
	 * @return The shapes.
	 */
	public List<MapShape> getShapes() {
		return shapes;
	}

	public RoadNetwork getRoadNetwork() {
		// I don't think this is needed here.
		return null;
	}

	/**
	 * This is not used for areas.
	 * @return Always returns null.
	 */
	public List<Overview> getOverviews() {
		return null;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasPoints() {
		return nActivePoints > 0;
	}

	/**
	 * True if there are active indexed points in the area.
	 * @return True if any active indexed points in the area.
	 */
	public boolean hasIndPoints() {
		return nActiveIndPoints > 0;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasLines() {
		return nActiveLines > 0;
	}

	/**
	 * Return number of lines in this area.
	 */
	public int getNumLines() {
		return nActiveLines;
	}

	/**
	 * Return number of shapes in this area.
	 */
	public int getNumShapes() {
		return nActiveShapes;
	}

	/**
	 * Return number of points in this area.
	 */
	public int getNumPoints() {
		return nActivePoints + nActiveIndPoints;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasShapes() {
		return nActiveShapes > 0;
	}

	/**
	 * Add an estimate of the size that will be required to hold this element
	 * if it should be displayed at the given resolution.  We also keep track
	 * of the number of <i>active</i> elements here ie elements that will be
	 * shown because they are at a resolution at least as great as the resolution
	 * of the area.
	 *
	 * @param p The element containing the minimum resolution that it will be
	 * displayed at.
	 * @param kind What kind of element this is KIND_POINT etc.
	 */
	private void addSize(MapElement p, int kind) {

		int res = p.getMinResolution();
		if (res > MAX_RESOLUTION)
			return;

		int numPoints;
		int numElements;

		switch (kind) {
		case POINT_KIND:
		case XT_POINT_KIND:
			if(res <= areaResolution) {
				// Points are predictably less than 9 bytes.
				sizes[kind] += 9;
				if(!p.hasExtendedType()) {
					if(((MapPoint) p).isCity())
						nActiveIndPoints++;
					else
						nActivePoints++;
				}
			}
			break;

		case LINE_KIND:
		case XT_LINE_KIND:
			if(res <= areaResolution) {
				// Estimate the size taken by lines and shapes as a constant plus
				// a factor based on the number of points.
				numPoints = ((MapLine) p).getPoints().size();
				numElements = 1 + ((numPoints - 1) / LineSplitterFilter.MAX_POINTS_IN_LINE);
				sizes[kind] += numElements * 11 + numPoints * 4;
				if (!p.hasExtendedType())
					nActiveLines += numElements;
			}
			break;

		case SHAPE_KIND:
		case XT_SHAPE_KIND:
			if(res <= areaResolution) {
				// Estimate the size taken by lines and shapes as a constant plus
				// a factor based on the number of points.
				numPoints = ((MapLine) p).getPoints().size();
				numElements = 1 + ((numPoints - 1) / PolygonSplitterFilter.MAX_POINT_IN_ELEMENT);
				sizes[kind] += numElements * 11 + numPoints * 4;
				if (!p.hasExtendedType())
					nActiveShapes += numElements;
			}
			break;

		default:
			log.error("should not be here");
			assert false;
			break;
		}

	}

	/**
	 * Add a single point to this area.
	 *
	 * @param p The point to add.
	 */
	private void addPoint(MapPoint p) {
		points.add(p);
		addToBounds(p.getBounds());
		addSize(p, p.hasExtendedType()? XT_POINT_KIND : POINT_KIND);
	}

	/**
	 * Add a single line to this area.
	 *
	 * @param l The line to add.
	 */
	private void addLine(MapLine l) {
		lines.add(l);
		addToBounds(l.getBounds());
		addSize(l, l.hasExtendedType()? XT_LINE_KIND : LINE_KIND);
	}

	/**
	 * Add a single shape to this map area.
	 *
	 * @param s The shape to add.
	 */
	private void addShape(MapShape s) {
		shapes.add(s);
		addToBounds(s.getBounds());
		addSize(s, s.hasExtendedType()? XT_SHAPE_KIND : SHAPE_KIND);
	}

	/**
	 * Add to the bounds of this area.  That is the new bounds
	 * for this area will cover the existing ones plus the new
	 * area.
	 *
	 * @param a Area to add into this map area.
	 */
	private void addToBounds(Area a) {
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
	 * @return The index to areas where the map element fits.
	 */
	private int pickArea(MapArea[] areas, MapElement e,
			int xbase, int ybase,
			int nx, int ny,
			int dx, int dy)
	{
		int x = e.getLocation().getLongitude();
		int y = e.getLocation().getLatitude();

		int xcell = (x - xbase) / dx;
		int ycell = (y - ybase) / dy;

		if (xcell < 0) {
			log.info("xcell was", xcell, "x", x, "xbase", xbase);
			xcell = 0;
		}
		if (ycell < 0) {
			log.info("ycell was", ycell, "y", y, "ybase", ybase);
			ycell = 0;
		}
		
		if (xcell >= nx)
			xcell = nx - 1;
		if (ycell >= ny)
			ycell = ny - 1;

		if (log.isDebugEnabled()) {
			log.debug("adding", e.getLocation(), "to", xcell, "/", ycell,
					areas[xcell * ny + ycell].getBounds());
		}
		return xcell * ny + ycell;
	}
}

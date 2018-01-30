/*
 * Copyright (C) 2006, 2012.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.util;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.osmosis.core.filter.common.PolygonFileReader;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

/**
 * This is a tool class that provides static methods to convert between mkgmap
 * objects and Java2D objects. The Java2D objects provide some optimized polygon
 * algorithms that are quite useful so that it makes sense to perform the
 * conversion.
 * 
 * @author WanMil
 */
public class Java2DConverter {
	private static final Logger log = Logger.getLogger(Java2DConverter.class);

	/**
	 * Creates a Java2D {@link Area} object from the given mkgmap rectangular
	 * {@link uk.me.parabola.imgfmt.app.Area} object.
	 * 
	 * @param bbox a rectangular bounding box
	 * @return the converted Java2D area
	 */
	public static Area createBoundsArea(uk.me.parabola.imgfmt.app.Area bbox) {
		return createArea(bbox.toCoords());
	}

	/**
	 * Converts the bounding box of a Java2D {@link Shape} object to an mkgmap
	 * {@link uk.me.parabola.imgfmt.app.Area} object.
	 * 
	 * @param shape a Java2D Shape (Area, Path2D, ...)
	 * @return the bounding box
	 */
	public static uk.me.parabola.imgfmt.app.Area createBbox(Shape shape) {
		Rectangle areaBounds = shape.getBounds();
		return new uk.me.parabola.imgfmt.app.Area(areaBounds.y,areaBounds.x,(int) areaBounds.getMaxY(),(int) areaBounds.getMaxX());
	}
	

	/**
	 * Creates a Java2D {@link Area} object from a polygon given as a list of
	 * {@link Coord} objects. This list should describe a closed polygon.
	 * 
	 * @param polygonPoints a list of points that describe a closed polygon
	 * @return the converted Java2D area
	 */
	public static Area createArea(List<Coord> polygonPoints) {
		return new Area(createPath2D(polygonPoints));
	}
	
	/**
	 * Creates a Java2D {@link Path2D} object from a polygon given as a list of
	 * {@link Coord} objects. This list should describe a closed polygon.
	 * 
	 * @param polygonPoints a list of points that describe a closed polygon
	 * @return the converted Java2D path
	 */
	public static Path2D createPath2D (List<Coord> polygonPoints) {
		int n = polygonPoints.size();
		if (n < 3)
			return new Path2D.Double();
		
		Path2D path = new Path2D.Double(PathIterator.WIND_NON_ZERO, n);
		if (polygonPoints.get(0).highPrecEquals(polygonPoints.get(n-1))){
			// if first and last point are high-prec-equal, ignore last point 
			// because we use closePath() to signal that
			--n;
		}
		int lastLat = Integer.MAX_VALUE,lastLon = Integer.MAX_VALUE;
		for (int i = 0; i < n; i++){
			Coord co = polygonPoints.get(i);
			int latHp = co.getHighPrecLat();
			int lonHp = co.getHighPrecLon();
			double x = (double)lonHp / (1<<Coord.DELTA_SHIFT); 
			double y = (double)latHp / (1<<Coord.DELTA_SHIFT); 
			if (i == 0)
				path.moveTo(x, y);
			else {
				if (lastLon != lonHp || lastLat != latHp)
					path.lineTo(x, y);
			}
			lastLon = lonHp;
			lastLat = latHp;
		}
		path.closePath();
		return path;
	}

	public static Polygon createHighPrecPolygon(List<Coord> points) {
		Polygon polygon = new Polygon();
		for (Coord co : points) {
			polygon.addPoint(co.getHighPrecLon(), co.getHighPrecLat());
		}
		return polygon;
	}

	public static List<Area> areaToSingularAreas(Area area) {
		return areaToSingularAreas(0, area);
	}
	/**
	 * Convert an area that may contains multiple areas to a list of singular
	 * areas keeping the highest possible precision.
	 * 
	 * @param area an area
	 * @return list of singular areas
	 */
	private static List<Area> areaToSingularAreas(int depth, Area area) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		} else if (area.isSingular()) {
			return Collections.singletonList(area);
		} else {
			List<Area> singularAreas = new ArrayList<>();

			// all ways in the area MUST define outer areas
			// it is not possible that one of the areas define an inner segment

			double[] res = new double[6];
			PathIterator pit = area.getPathIterator(null);
			Path2D path = null;
			while (!pit.isDone()) {
				int type = pit.currentSegment(res);
				double lat = res[1];
				double lon = res[0];

				switch (type) {
				case PathIterator.SEG_LINETO:
					path.lineTo(lon, lat);
					break;
				case PathIterator.SEG_CLOSE:
					path.closePath();
					Area a = new Area(path);
					if (!a.isEmpty()) {
						if (depth < 10 && !a.isSingular()){
							// should not happen, but it does. Error in Area code?
							singularAreas.addAll(areaToSingularAreas(depth+1,a));
						}
						else 
							singularAreas.add(a);
					}
					path = null;
					break;
				case PathIterator.SEG_MOVETO:
					path = new Path2D.Double();
					path.moveTo(lon, lat);
					break;
				default:
					log.error("Unsupported path iterator type " + type
							+ ". This is an mkgmap error.");
				}
				pit.next();
			}
			return singularAreas;
		}
	}

	/**
	 * Convert an area to an mkgmap way. The caller must ensure that the area is
	 * singular. Otherwise only the first non-empty part of the area is converted.
	 * 
	 * @param area the area
	 * @return a new mkgmap way
	 */
	public static List<Coord> singularAreaToPoints(Area area) {
		if (area.isEmpty()) {
			return null;
		}

		List<Coord> points = null;

		double[] res = new double[6];
		PathIterator pit = area.getPathIterator(null);
		int prevLatHp = Integer.MIN_VALUE;
		int prevLongHp = Integer.MIN_VALUE;

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			int latHp = (int)Math.round(res[1] * (1<<Coord.DELTA_SHIFT));
			int lonHp = (int)Math.round(res[0] * (1<<Coord.DELTA_SHIFT));

			switch (type) {
			case PathIterator.SEG_MOVETO:
				if (points != null)
					log.error("area not singular");
				points = new ArrayList<>();
				points.add(Coord.makeHighPrecCoord(latHp, lonHp));
				break;
			case PathIterator.SEG_LINETO:
				assert points != null;
				if (prevLatHp != latHp || prevLongHp != lonHp) {
					points.add(Coord.makeHighPrecCoord(latHp, lonHp));
				}
				break;
			case PathIterator.SEG_CLOSE:
				assert points != null;
				if (points.size() < 3)
					points = null; 
				else {
					if (points.get(0).highPrecEquals(points.get(points.size() - 1))) { 
						// replace equal last with closing point
						points.set(points.size() - 1, points.get(0)); 
					}
					else
						points.add(points.get(0)); // add closing point
					if (points.size() < 4)
						points = null;
					else
						return points;
				}
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}

			prevLatHp = latHp;
			prevLongHp = lonHp;

			pit.next();
		}
		return points;
	}

	/**
	 * Convert the area back into a list of polygons each represented by a list
	 * of coords. It is possible that the area contains multiple discontinuous
	 * polygons, so you may append more than one shape to the output list.<br/>
	 * <b>Attention:</b> The outline of the polygon is has clockwise order whereas
	 * holes in the polygon have counterclockwise order. 
	 * 
	 * @param area The area to be converted.
	 * @param useHighPrec false: round coordinates to map units
	 * @return a list of closed polygons
	 */
	public static List<List<Coord>> areaToShapes(java.awt.geom.Area area) {
		List<List<Coord>> outputs = new ArrayList<>(4);

		double[] res = new double[6];
		PathIterator pit = area.getPathIterator(null);
		
		List<Coord> coords = null;

		int prevLatHp = Integer.MIN_VALUE;
		int prevLongHp = Integer.MIN_VALUE;

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			int latHp = (int) Math.round(res[1] * (1<<Coord.DELTA_SHIFT));
			int lonHp = (int) Math.round(res[0] * (1<<Coord.DELTA_SHIFT));
			
			switch (type) {
			case PathIterator.SEG_LINETO:
				if (prevLatHp != latHp || prevLongHp != lonHp) 
					coords.add(Coord.makeHighPrecCoord(latHp, lonHp));

				prevLatHp = latHp;
				prevLongHp = lonHp;
				break;
			case PathIterator.SEG_MOVETO: 
			case PathIterator.SEG_CLOSE:
				if ((type == PathIterator.SEG_MOVETO && coords != null) || type == PathIterator.SEG_CLOSE) {
					if (coords.size() > 2){
						if (coords.get(0).highPrecEquals(coords.get(coords.size() - 1))){ 
							// replace equal last with closing point
							coords.set(coords.size() - 1, coords.get(0)); 
						}
						else
							coords.add(coords.get(0)); // add closing point
					}
					if (coords.size() > 3){
						outputs.add(coords);
					}
				}
				if (type == PathIterator.SEG_MOVETO){
					coords = new ArrayList<>();
					coords.add(Coord.makeHighPrecCoord(latHp, lonHp));
					prevLatHp = latHp;
					prevLongHp = lonHp;
				} else {
					coords = null;
					prevLatHp = Integer.MIN_VALUE;
					prevLongHp = Integer.MIN_VALUE;
				}
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}

			pit.next();
		}

		return outputs;
	}

	/**
	 * Convert area with coordinates in degrees to area in MapUnits
	 * @param area
	 * @return
	 */
	public static java.awt.geom.Area AreaDegreesToMapUnit(java.awt.geom.Area area){
		if (area == null)
			return null;
		double[] res = new double[6];
		Path2D path = new Path2D.Double();
		PathIterator pit = area.getPathIterator(null);
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			double fLat = res[1];
			double fLon = res[0];
			int lat = Utils.toMapUnit(fLat);
			int lon = Utils.toMapUnit(fLon);
			
			switch (type) {
			case PathIterator.SEG_LINETO:
				path.lineTo(lon, lat);
				break;
			case PathIterator.SEG_MOVETO: 
				path.moveTo(lon, lat);
				break;
			case PathIterator.SEG_CLOSE:
				path.closePath();
				break;
			default:
				System.out.println("Unsupported path iterator type " + type
						+ ". This is an internal splitter error.");
			}

			pit.next();
		}
		return new java.awt.geom.Area(path);
	} 

	/**
	 * Read am osmosis *.poly file that describes a polygon with coordinates in degrees.  
	 * @param polygonFile path to the poly file
	 * @return the polygon converted to map units or null in case of error. 
	 */
	public static java.awt.geom.Area readPolyFile(String polygonFile) {
		File f = new File(polygonFile);
		if (!f.exists()) {
			throw new IllegalArgumentException("polygon file doesn't exist: " + polygonFile);
		}
		try {
			PolygonFileReader pfr = new PolygonFileReader(f);
			java.awt.geom.Area polygonInDegrees = pfr.loadPolygon();
			return Java2DConverter.AreaDegreesToMapUnit(polygonInDegrees);
		} catch (Exception e) {
			log.error("cannot read polygon file", polygonFile);
		}
		return null;
	}


} 
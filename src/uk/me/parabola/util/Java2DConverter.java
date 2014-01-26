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
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
	 * Converts the bounding box of a Java2D {@link Area} object to an mkgmap
	 * {@link uk.me.parabola.imgfmt.app.Area} object.
	 * 
	 * @param area a Java2D area
	 * @return the bounding box
	 */
	public static uk.me.parabola.imgfmt.app.Area createBbox(Area area) {
		Rectangle areaBounds = area.getBounds();
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
		if (polygonPoints.size()<3)
			return new Area();
		Path2D path = new Path2D.Double();
		int n = polygonPoints.size();
		if (polygonPoints.get(0).highPrecEquals(polygonPoints.get(n-1))){
			// if first and last point are high-prec-equal, ignore last point 
			// because we use closePath() to signal that
			--n;
		}
		double lastLat = Integer.MAX_VALUE,lastLon = Integer.MAX_VALUE;
		for (int i = 0; i < n; i++){
			Coord co = polygonPoints.get(i);
			int lat30 = co.getHighPrecLat();
			int lon30 = co.getHighPrecLon();
			double x = (double)lon30 / (1<<Coord.DELTA_SHIFT); 
			double y = (double)lat30 / (1<<Coord.DELTA_SHIFT); 
			if (i == 0)
				path.moveTo(x, y);
			else {
				if (lastLon != lon30 || lastLat != lat30)
					path.lineTo(x, y);
			}
			lastLon = lon30;
			lastLat = lat30;
		}
		path.closePath();
		return new Area(path);
		
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
			List<Area> singularAreas = new ArrayList<Area>();

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
		int prevLat30 = Integer.MIN_VALUE;
		int prevLong30 = Integer.MIN_VALUE;

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			int lat30 = (int)Math.round(res[1] * (1<<Coord.DELTA_SHIFT));
			int lon30 = (int)Math.round(res[0] * (1<<Coord.DELTA_SHIFT));

			switch (type) {
			case PathIterator.SEG_MOVETO:
				if (points != null)
					log.error("area not singular");
				points = new ArrayList<Coord>();
				points.add(Coord.makeHighPrecCoord(lat30, lon30));
				break;
			case PathIterator.SEG_LINETO:
				assert points != null;
				if (prevLat30 != lat30 || prevLong30 != lon30) {
					points.add(Coord.makeHighPrecCoord(lat30, lon30));
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

			prevLat30 = lat30;
			prevLong30 = lon30;

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
		List<List<Coord>> outputs = new ArrayList<List<Coord>>(4);

		double[] res = new double[6];
		PathIterator pit = area.getPathIterator(null);
		
		List<Coord> coords = null;

		int prevLat30 = Integer.MIN_VALUE;
		int prevLong30 = Integer.MIN_VALUE;

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			int lat30 = (int) Math.round(res[1] * (1<<Coord.DELTA_SHIFT));
			int lon30 = (int) Math.round(res[0] * (1<<Coord.DELTA_SHIFT));
			
			switch (type) {
			case PathIterator.SEG_LINETO:
				if (prevLat30 != lat30 || prevLong30 != lon30) 
					coords.add(Coord.makeHighPrecCoord(lat30, lon30));

				prevLat30 = lat30;
				prevLong30 = lon30;
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
					coords = new ArrayList<Coord>();
					coords.add(Coord.makeHighPrecCoord(lat30, lon30));
					prevLat30 = lat30;
					prevLong30 = lon30;
				} else {
					coords = null;
					prevLat30 = Integer.MIN_VALUE;
					prevLong30 = Integer.MIN_VALUE;
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


} 
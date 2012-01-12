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
		return new Area(new Rectangle(bbox.getMinLong(), bbox.getMinLat(),
				bbox.getMaxLong() - bbox.getMinLong(), bbox.getMaxLat()
						- bbox.getMinLat()));
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
		return new uk.me.parabola.imgfmt.app.Area(areaBounds.y, areaBounds.x,
				areaBounds.y + areaBounds.height, areaBounds.x
						+ areaBounds.width);
	}

	/**
	 * Creates a Java2D {@link Area} object from a polygon given as a list of
	 * {@link Coord} objects. This list should describe a closed polygon.
	 * 
	 * @param polygonPoints a list of points that describe a closed polygon
	 * @return the converted Java2D area
	 */
	public static Area createArea(List<Coord> polygonPoints) {
		return new Area(createPolygon(polygonPoints));
	}

	/**
	 * Create a polygon from a list of points.
	 * 
	 * @param points list of points
	 * @return the polygon
	 */
	public static Polygon createPolygon(List<Coord> points) {
		Polygon polygon = new Polygon();
		for (Coord co : points) {
			polygon.addPoint(co.getLongitude(), co.getLatitude());
		}
		return polygon;
	}

	/**
	 * Convert an area that may contains multiple areas to a list of singular
	 * areas
	 * 
	 * @param area an area
	 * @return list of singular areas
	 */
	public static List<Area> areaToSingularAreas(Area area) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		} else if (area.isSingular()) {
			return Collections.singletonList(area);
		} else {
			List<Area> singularAreas = new ArrayList<Area>();

			// all ways in the area MUST define outer areas
			// it is not possible that one of the areas define an inner segment

			float[] res = new float[6];
			PathIterator pit = area.getPathIterator(null);
			int prevLat = Integer.MIN_VALUE;
			int prevLong = Integer.MIN_VALUE;

			Polygon p = null;
			while (!pit.isDone()) {
				int type = pit.currentSegment(res);
				int lat = Math.round(res[1]);
				int lon = Math.round(res[0]);

				switch (type) {
				case PathIterator.SEG_LINETO:
					if (prevLat != lat || prevLong != lon) {
						p.addPoint(lon, lat);
					}
					prevLat = lat;
					prevLong = lon;
					break;
				case PathIterator.SEG_CLOSE:
					p.addPoint(p.xpoints[0], p.ypoints[0]);
					Area a = new Area(p);
					if (!a.isEmpty()) {
						singularAreas.add(a);
					}
					p = null;
					break;
				case PathIterator.SEG_MOVETO:
					if (p != null) {
						Area a2 = new Area(p);
						if (!a2.isEmpty()) {
							singularAreas.add(a2);
						}
					}
					p = new Polygon();
					p.addPoint(lon, lat);
					break;
				default:
					log.error("Unsupported path iterator type " + type
							+ ". This is an mkgmap error.");
				}

				prevLat = lat;
				prevLong = lon;

				pit.next();
			}
			return singularAreas;
		}
	}

	/**
	 * Convert an area to an mkgmap way. The caller must ensure that the area is
	 * singular. Otherwise only the first part of the area is converted.
	 * 
	 * @param area the area
	 * @return a new mkgmap way
	 */
	public static List<Coord> singularAreaToPoints(Area area) {
		if (area.isEmpty()) {
			return null;
		}

		List<Coord> points = null;

		float[] res = new float[6];
		PathIterator pit = area.getPathIterator(null);
		int prevLat = Integer.MIN_VALUE;
		int prevLong = Integer.MIN_VALUE;

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			int lat = Math.round(res[1]);
			int lon = Math.round(res[0]);

			switch (type) {
			case PathIterator.SEG_MOVETO:
				points = new ArrayList<Coord>();
				points.add(new Coord(lat, lon));
				break;
			case PathIterator.SEG_LINETO:
				assert points != null;
				if (prevLat != lat || prevLong != lon) {
					points.add(new Coord(lat, lon));
				}
				break;
			case PathIterator.SEG_CLOSE:
				assert points != null;
				if (points.get(0).equals(points.get(points.size() - 1)) == false) 
					points.add(points.get(0));
				return points;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}

			prevLat = lat;
			prevLong = lon;

			pit.next();
		}
		return points;
	}

	/**
	 * Convert the area back into a list of polygons each represented by a list
	 * of coords. It is possible that the area contains multiple discontiguous
	 * polygons, so you may append more than one shape to the output list.<br/>
	 * <b>Attention:</b> The outline of the polygon is has clockwise order whereas
	 * holes in the polygon have counterclockwise order. 
	 * 
	 * @param area The area to be converted.
	 * @return a list of closed polygons
	 */
	public static List<List<Coord>> areaToShapes(java.awt.geom.Area area) {
		List<List<Coord>> outputs = new ArrayList<List<Coord>>(4);

		float[] res = new float[6];
		PathIterator pit = area.getPathIterator(null);
		List<Coord> coords = null;

		int prevLat = Integer.MIN_VALUE;
		int prevLong = Integer.MIN_VALUE;

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			int lat = Math.round(res[1]);
			int lon = Math.round(res[0]);

			switch (type) {
			case PathIterator.SEG_LINETO:
				if (prevLat != lat || prevLong != lon) {
					coords.add(new Coord(lat, lon));
				}
				prevLat = lat;
				prevLong = lon;
				break;
			case PathIterator.SEG_MOVETO:
				if (coords != null) {
					if (coords.size() > 2) {
						if (coords.get(0).equals(coords.get(coords.size() - 1)) == false)
							coords.add(coords.get(0));

						if (coords.size() > 3)
							outputs.add(coords);
					}
				}
				coords = new ArrayList<Coord>();
				coords.add(new Coord(lat, lon));
				prevLat = lat;
				prevLong = lon;
				break;
			case PathIterator.SEG_CLOSE:
				if (coords.size() > 2) {
					if (coords.get(0).equals(coords.get(coords.size() - 1)) == false)
						coords.add(coords.get(0));

					if (coords.size() > 3)
						outputs.add(coords);
				}
				coords = null;
				prevLat = Integer.MIN_VALUE;
				prevLong = Integer.MIN_VALUE;
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

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
import uk.me.parabola.mkgmap.reader.osm.Way;

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
		Path2D path= new Path2D.Double();
		double factor = 1.0 / (1<<6);
		int lat30 = polygonPoints.get(0).getHighPrecLat();
		int lon30 = polygonPoints.get(0).getHighPrecLon();
		path.moveTo(factor*lon30, factor*lat30);
		for (int i = 1; i < polygonPoints.size();i++) {
			lat30 = polygonPoints.get(i).getHighPrecLat();
			lon30 = polygonPoints.get(i).getHighPrecLon();
			path.lineTo(factor*lon30, factor*lat30);
		}
		return new Area(path);
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

			double[] res = new double[6];
			PathIterator pit = area.getPathIterator(null);
			int prevLat = Integer.MIN_VALUE;
			int prevLong = Integer.MIN_VALUE;

			Polygon p = null;
			while (!pit.isDone()) {
				int type = pit.currentSegment(res);
				int lat24 = (int)Math.round(res[1]);
				int lon24 = (int)Math.round(res[0]);

				switch (type) {
				case PathIterator.SEG_LINETO:
					if (prevLat != lat24 || prevLong != lon24) {
						p.addPoint(lon24, lat24);
					}
					prevLat = lat24;
					prevLong = lon24;
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
					p.addPoint(lon24, lat24);
					break;
				default:
					log.error("Unsupported path iterator type " + type
							+ ". This is an mkgmap error.");
				}

				prevLat = lat24;
				prevLong = lon24;

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

		double[] res = new double[6];
		PathIterator pit = area.getPathIterator(null);
		int prevLat = Integer.MIN_VALUE;
		int prevLong = Integer.MIN_VALUE;
		double factor = 1 << 6;
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			int lat = (int)Math.round(res[1]);
			int lon = (int)Math.round(res[0]);
			int lat30 = (int)Math.round(res[1]*factor);
			int lon30 = (int)Math.round(res[0]*factor);


			switch (type) {
			case PathIterator.SEG_MOVETO:
				points = new ArrayList<Coord>();
				points.add(Coord.makeHighPrecCoord(lat30, lon30));
				break;
			case PathIterator.SEG_LINETO:
				assert points != null;
				if (prevLat != lat || prevLong != lon) {
					points.add(Coord.makeHighPrecCoord(lat30, lon30));
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

		double[] res = new double[6];
		PathIterator pit = area.getPathIterator(null);
		
		// store float precision coords to check if the direction (cw/ccw)
		// of a polygon changes due to conversion to int precision 
		List<Double> floatLat = null;
		List<Double> floatLon = null;

		List<Coord> coords = null;

		int iPrevLat = Integer.MIN_VALUE;
		int iPrevLong = Integer.MIN_VALUE;
		double factor = 1 << 6;
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			double fLat = res[1];
			double fLon = res[0];
			int iLat = (int)Math.round(fLat);
			int iLon = (int)Math.round(fLon);
			int lat30 = (int)Math.round(fLat*factor);
			int lon30 = (int)Math.round(fLon*factor);
			switch (type) {
			case PathIterator.SEG_LINETO:
				floatLat.add(fLat);
				floatLon.add(fLon);

				if (iPrevLat != iLat || iPrevLong != iLon) 
					coords.add(Coord.makeHighPrecCoord(lat30, lon30));

				iPrevLat = iLat;
				iPrevLong = iLon;
				break;
			case PathIterator.SEG_MOVETO: 
			case PathIterator.SEG_CLOSE:
				if ((type == PathIterator.SEG_MOVETO && coords != null) || type == PathIterator.SEG_CLOSE) {
					if (coords.size() > 2 && coords.get(0).equals(coords.get(coords.size() - 1)) == false) {
						coords.add(coords.get(0));
					}
					if (coords.size() > 3){
						// use float values to verify area size calculations with higher precision
						if (floatLat.size() > 2) {
							if (floatLat.get(0).equals(floatLat.get(floatLat.size() - 1)) == false
									|| floatLon.get(0).equals(floatLon.get(floatLon.size() - 1)) == false){ 
								floatLat.add(floatLat.get(0));
								floatLon.add(floatLon.get(0));
							}
						}

						// calculate area size with float values 
						double realAreaSize = 0;
						double pf1Lat = floatLat.get(0);
						double pf1Lon = floatLon.get(0);
						for(int i = 1; i < floatLat.size(); i++) {
							double pf2Lat = floatLat.get(i);
							double pf2Lon = floatLon.get(i);
							realAreaSize += (pf1Lon * pf2Lat - 
									pf2Lon * pf1Lat);
							pf1Lat = pf2Lat;
							pf1Lon = pf2Lon;
						}
						
					
						// Check if the polygon with float precision has the same direction
						// than the polygon with int precision. If not reverse the int precision
						// polygon. Its direction has changed artificially by the int conversion.
						boolean floatPrecClockwise = (realAreaSize <= 0);
						if (Way.clockwise(coords) != floatPrecClockwise) {
							
							if (log.isInfoEnabled()) {
								log.info("Converting area to int precision changes direction. Will correct that.");
								StringBuilder sb = new StringBuilder("[");
								for (int i = 0; i < floatLat.size(); i++) {
									if (i > 0) {
										sb.append(", ");
									}
									sb.append(floatLat.get(i));
									sb.append("/");
									sb.append(floatLon.get(i));
								}
								sb.append("]");
								log.info("Float area: ", sb);
								log.info("Int area: ", coords);
							}
							
							Collections.reverse(coords);
						}
						outputs.add(coords);
					}
				}
				if (type == PathIterator.SEG_MOVETO){
					floatLat= new ArrayList<Double>();
					floatLon= new ArrayList<Double>();
					floatLat.add(fLat);
					floatLon.add(fLon);
					coords = new ArrayList<Coord>();
					coords.add(Coord.makeHighPrecCoord(lat30, lon30));
					iPrevLat = iLat;
					iPrevLong = iLon;
				} else {
					floatLat= null;
					floatLon= null;
					coords = null;
					iPrevLat = Integer.MIN_VALUE;
					iPrevLong = Integer.MIN_VALUE;
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

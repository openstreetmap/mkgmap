package uk.me.parabola.util;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

/**
 * This is a tool class that provides static methods to convert between
 * mkgmap objects and Java2D objects. The Java2D objects provide some 
 * optimized polygon algorithms that are quite useful so that it makes
 * sense to perform the conversion.
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
			float[] prevPoint = new float[6];
	
			Polygon p = null;
			while (!pit.isDone()) {
				int type = pit.currentSegment(res);
	
				switch (type) {
				case PathIterator.SEG_LINETO:
					if (!Arrays.equals(res, prevPoint)) {
						p.addPoint(Math.round(res[0]), Math.round(res[1]));
					}
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
					p.addPoint(Math.round(res[0]), Math.round(res[1]));
					break;
				default:
					log.error("Unsupported path iterator type "
							+ type+ ". This is an mkgmap error.");
				}
	
				System.arraycopy(res, 0, prevPoint, 0, 6);
				pit.next();
			}
			return singularAreas;
		}
	}

	/**
	 * Convert an area to an mkgmap way. The caller must ensure that the area is singular.
	 * Otherwise only the first part of the area is converted.
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

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			switch (type) {
			case PathIterator.SEG_MOVETO:
				points = new ArrayList<Coord>();
				points.add(new Coord(Math.round(res[1]), Math.round(res[0])));
				break;
			case PathIterator.SEG_LINETO:
				assert points != null;
				points.add(new Coord(Math.round(res[1]), Math.round(res[0])));
				break;
			case PathIterator.SEG_CLOSE:
				assert points != null;
				points.add(points.get(0));
				return points;
			default:
				log.error(
						"Unsupported path iterator type " +type+
						". This is an mkgmap error.");
			}
			pit.next();
		}
		return points;
	}
	
	/**
	 * Convert the area back into a list of polygons each represented by a list of coords. 
	 * It is possible that the area contains multiple discontiguous polygons, so you may 
	 * append more than one shape to the output list.<br/>
	 * <b>Attention:</b> The outline of holes in the area are converted as a polygon too. There
	 * is no way to check which returned polygon describes the outer shape and which one describes
	 * the hole. So it is better not to use this method with holes. 
	 *
	 * @param area The area to be converted.
	 * @return a list of closed polygons
	 */
	public static List<List<Coord>> areaToShapes(java.awt.geom.Area area) {
		List<List<Coord>> outputs = new ArrayList<List<Coord>>(4);
		float[] res = new float[6];
		PathIterator pit = area.getPathIterator(null);

		List<Coord> coords = null;
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			Coord co = new Coord(Math.round(res[1]), Math.round(res[0]));

			if (type == PathIterator.SEG_MOVETO) {
				// We get a move to at the beginning and if this area is actually
				// discontiguous we may get more than one, each one representing
				// the start of another polygon in the output.
				if (coords != null) {
					// this should not happen because each polygon is ended
					// with a SEG_CLOSE
					outputs.add(coords);
				}

				coords = new ArrayList<Coord>();
				coords.add(co);
			} else if (type == PathIterator.SEG_LINETO) {
				// Continuing with the path.
				assert coords != null;
				coords.add(co);
			} else if (type == PathIterator.SEG_CLOSE) {
				// The end of a polygon
				assert coords != null;
				// close the polygon
				coords.add(coords.get(0));
				
				outputs.add(coords);
				coords = null;
			}
			pit.next();
		}
		return outputs;
	}
	
	
}

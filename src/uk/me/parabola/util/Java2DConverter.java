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

public class Java2DConverter {
	private static final Logger log = Logger
	.getLogger(Java2DConverter.class);

	public static Area createArea(List<Coord> points) {
		return new Area(createPolygon(points));
	}
	
	/**
	 * Create a polygon from a list of points.
	 * 
	 * @param points
	 *            list of points
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
	 * @param area
	 *            an area
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
	 * @param area
	 *            the area
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
	
}

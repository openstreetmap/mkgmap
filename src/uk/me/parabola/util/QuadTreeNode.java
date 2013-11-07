package uk.me.parabola.util;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

public class QuadTreeNode {

	private static final int MAX_POINTS = 20;

	private Collection<Coord> points;
	private final Area bounds;
	private Area coveredBounds;

	public Area getCoveredBounds() {
		return coveredBounds;
	}

	private QuadTreeNode[] children;

	public static final class QuadTreePolygon {
		private final java.awt.geom.Area javaArea;
		private final Area bbox;

		public QuadTreePolygon(java.awt.geom.Area javaArea) {
			this.javaArea = javaArea;
			Rectangle bboxRect = javaArea.getBounds();
			bbox = new Area(bboxRect.y, bboxRect.x, bboxRect.y
					+ bboxRect.height, bboxRect.x + bboxRect.width);
		}

		public QuadTreePolygon(List<Coord> points) {
			this(Java2DConverter.createArea(points));
		}

		public QuadTreePolygon(Collection<List<Coord>> polygonList) {
			this.javaArea = new java.awt.geom.Area();
			for (List<Coord> polygon : polygonList) {
				javaArea.add(Java2DConverter.createArea(polygon));
			}
			Rectangle bboxRect = javaArea.getBounds();
			bbox = new Area(bboxRect.y, bboxRect.x, bboxRect.y
					+ bboxRect.height, bboxRect.x + bboxRect.width);
		}

		public Area getBbox() {
			return bbox;
		}

		public java.awt.geom.Area getArea() {
			return javaArea;
		}
	}

	public QuadTreeNode(Area bounds) {
		this(bounds, Collections.<Coord>emptyList());
	}

	public QuadTreeNode(Area bounds, Collection<Coord> points) {
		this.bounds = bounds;
		this.children = null;

		int minLat = Integer.MAX_VALUE;
		int maxLat = Integer.MIN_VALUE;
		int minLong = Integer.MAX_VALUE;
		int maxLong = Integer.MIN_VALUE;
		for (Coord c : points) {
			if (c.getLatitude() < minLat) {
				minLat = c.getLatitude();
			}
			if (c.getLatitude() > maxLat) {
				maxLat = c.getLatitude();
			}
			if (c.getLongitude() < minLong) {
				minLong = c.getLongitude();
			}
			if (c.getLongitude() > maxLong) {
				maxLong = c.getLongitude();
			}
		}
		coveredBounds = new Area(minLat, minLong, maxLat, maxLong);

		if (points.size() > MAX_POINTS) {
			this.points = points;
			split();
		} else {
			this.points = new HashSet<Coord>(points);
		}
	}

	public Area getBounds() {
		return this.bounds;
	}

	public boolean add(Coord c) {
		if (coveredBounds == null) {
			coveredBounds = new Area(c.getLatitude(), c.getLongitude(),
					c.getLatitude(), c.getLongitude());
		} else if (coveredBounds.contains(c) == false) {
			coveredBounds = new Area(Math.min(coveredBounds.getMinLat(),
					c.getLatitude()), Math.min(coveredBounds.getMinLong(),
					c.getLongitude()), Math.max(coveredBounds.getMaxLat(),
					c.getLatitude()), Math.max(coveredBounds.getMaxLong(),
					c.getLongitude()));
		}
		if (isLeaf()) {
			boolean added = points.add(c);
			if (points.size() > MAX_POINTS)
				split();
			return added;
		} else {
			for (QuadTreeNode nodes : children) {
				if (nodes.getBounds().contains(c)) {
					return nodes.add(c);
				}
			}
			return false;
		}
	}

	public List<Coord> get(Area bbox, List<Coord> resultList) {
		if (isLeaf()) {
			if (bbox.getMinLat() <= coveredBounds.getMinLat()
					&& bbox.getMaxLat() >= coveredBounds.getMaxLat()
					&& bbox.getMinLong() <= coveredBounds.getMinLong()
					&& bbox.getMaxLong() >= coveredBounds.getMaxLong()) {

				// the bounding box is contained completely in the bbox
				// => add all points without further check
				resultList.addAll(points);
			} else {
				// check each point
				for (Coord c : points) {
					if (bbox.contains(c)) {
						resultList.add(c);
					}
				}
			}
		} else {
			for (QuadTreeNode child : children) {
				if (bbox.intersects(child.getCoveredBounds())) {
					resultList = child.get(bbox, resultList);
				}
			}
		}
		return resultList;
	}

	public ArrayList<Coord> get(QuadTreePolygon polygon, ArrayList<Coord> resultList) {
		if (polygon.getBbox().intersects(getBounds())) {
			if (isLeaf()) {
				for (Coord c : points) {
					if (polygon.getArea().contains(c.getLongitude(),
							c.getLatitude())) {
						resultList.add(c);
					}
				}
			} else {
				for (QuadTreeNode child : children) {
					if (polygon.getBbox().intersects(child.getBounds())) {
						java.awt.geom.Area subArea = (java.awt.geom.Area) polygon
								.getArea().clone();
						subArea.intersect(createArea(child.getBounds()));
						child.get(new QuadTreePolygon(subArea), resultList);
					}
				}
			}
		}
		return resultList;

	}

	private java.awt.geom.Area createArea(Area bbox) {
		return new java.awt.geom.Area(new Rectangle(bbox.getMinLong(),
				bbox.getMinLat(), bbox.getWidth(), bbox.getHeight()));
	}

	public boolean isLeaf() {
		return points != null;
	}

	private void split() {
		if (bounds.getHeight() <= 1 || bounds.getWidth() <= 1) {
			return;
		}

		int halfLat = (bounds.getMinLat() + bounds.getMaxLat()) / 2;
		int halfLong = (bounds.getMinLong() + bounds.getMaxLong()) / 2;
		children = new QuadTreeNode[4];

		Area swBounds = new Area(bounds.getMinLat(), bounds.getMinLong(),
				halfLat, halfLong);
		Area nwBounds = new Area(halfLat + 1, bounds.getMinLong(),
				bounds.getMaxLat(), halfLong);
		Area seBounds = new Area(bounds.getMinLat(), halfLong + 1, halfLat,
				bounds.getMaxLong());
		Area neBounds = new Area(halfLat + 1, halfLong + 1, bounds.getMaxLat(),
				bounds.getMaxLong());

		children[0] = new QuadTreeNode(swBounds);
		children[1] = new QuadTreeNode(nwBounds);
		children[2] = new QuadTreeNode(seBounds);
		children[3] = new QuadTreeNode(neBounds);

		Collection<Coord> copyPoints = points;
		points = null;
		for (Coord c : copyPoints) {
			add(c);
		}
	}

	public void clear() {
		this.children = null;
		points = new HashSet<Coord>();
		coveredBounds = new Area(Integer.MAX_VALUE, Integer.MAX_VALUE,
				Integer.MIN_VALUE, Integer.MIN_VALUE);
	}
}

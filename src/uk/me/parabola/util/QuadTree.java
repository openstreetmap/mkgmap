package uk.me.parabola.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.util.QuadTreeNode.QuadTreePolygon;

public class QuadTree {

	private final QuadTreeNode root;
	private long itemCount;

	public QuadTree(Area bbox) {
		this.root = new QuadTreeNode(bbox);
		this.itemCount = 0;
	}

	public boolean addAll(Collection<Coord> coordList) {
		boolean oneAdded = false;
		for (Coord c : coordList) {
			oneAdded = add(c) | oneAdded;
		}
		return oneAdded;
	}

	public boolean add(Coord c) {

		boolean added = root.add(c);
		if (added) {
			itemCount++;
		}
		return added;
	}

	public List<Coord> get(Area bbox) {
		return root.get(bbox, new ArrayList<Coord>(2000));
	}

	public List<Coord> get(Collection<List<Coord>> polygons) {
		return root.get(new QuadTreePolygon(polygons), new ArrayList<Coord>(
				2000));
	}

	public List<Coord> get(List<Coord> polygon) {
		return get(polygon, 0);
	}

	public List<Coord> get(List<Coord> polygon, int offset) {
		if (polygon.size() < 3) {
			return Collections.emptyList();
		}
		if (polygon.get(0).equals(polygon.get(polygon.size() - 1)) == false) {
			return null;
		}
		ArrayList<Coord> points = root.get(new QuadTreePolygon(polygon),
				new ArrayList<Coord>(2000));
		if (offset > 0) {
			ListIterator<Coord> pointIter = points.listIterator();
			while (pointIter.hasNext()) {
				if (isCloseToPolygon(pointIter.next(), polygon, offset)) {
					pointIter.remove();
				}
			}
		}
		return points;
	}

	public void clear() {
		itemCount = 0;
		root.clear();
	}

	public long getSize() {
		return itemCount;
	}

	private boolean isCloseToPolygon(Coord point, List<Coord> polygon,
			int gap) {
		Iterator<Coord> polyIter = polygon.iterator();
		Coord c1 = null;
		Coord c2 = polyIter.next();
		while (polyIter.hasNext()) {
			c1 = c2;
			c2 = polyIter.next();
			double dist = distanceToSegment(c1, c2, point);
			if (dist <= gap) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Calculates the distance to the given segment in meter.
	 * @param spoint1 segment point 1
	 * @param spoint2 segment point 2
	 * @param point point
	 * @return the distance in meter
	 */
	private double distanceToSegment(Coord spoint1, Coord spoint2, Coord point) {

		double dx = spoint2.getLongitude() - spoint1.getLongitude();
		double dy = spoint2.getLatitude() - spoint1.getLatitude();

		if ((dx == 0) && (dy == 0)) {
			return spoint1.distance(point);
		}

		double frac = ((point.getLongitude() - spoint1.getLongitude()) * dx + (point
				.getLatitude() - spoint1.getLatitude()) * dy)
				/ (dx * dx + dy * dy);

		if (frac < 0) {
			return spoint1.distance(point);
		} else if (frac > 1) {
			return spoint2.distance(point);
		} else {
			return spoint1.makeBetweenPoint(spoint2, frac).distance(point);
		}

	}
}

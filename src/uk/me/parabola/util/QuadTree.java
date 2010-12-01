package uk.me.parabola.util;

import java.awt.geom.Point2D;
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
			int offset) {
		Iterator<Coord> polyIter = polygon.iterator();
		Coord c1 = null;
		Coord c2 = polyIter.next();
		while (polyIter.hasNext()) {
			c1 = c2;
			c2 = polyIter.next();
			double dist = distanceToSegment(c1, c2, point);
			if (dist <= offset) {
				return true;
			}
		}
		return false;
	}

	private double distanceToSegment(Coord lp1, Coord lp2, Coord ppoint) {

		double dx = lp2.getLongitude() - lp1.getLongitude();
		double dy = lp2.getLatitude() - lp1.getLatitude();

		if ((dx == 0) && (dy == 0)) {
			return Double.POSITIVE_INFINITY;
		}

		double frac = ((ppoint.getLongitude() - lp1.getLongitude()) * dx + (ppoint
				.getLatitude() - lp1.getLatitude()) * dy)
				/ (dx * dx + dy * dy);

		double pLat = lp1.getLatitude();
		double pLong = lp1.getLongitude();

		if (frac < 0) {
			pLat = lp1.getLatitude();
			pLong = lp1.getLongitude();
		} else if (frac > 1) {
			pLat = lp2.getLatitude();
			pLong = lp2.getLongitude();
		} else {
			pLong = lp1.getLongitude() + frac * dx;
			pLat = lp1.getLatitude() + frac * dy;
		}

		return Point2D.distance(pLong, pLat, ppoint.getLongitude(),
				ppoint.getLatitude());

	}
}

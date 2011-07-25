package uk.me.parabola.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.util.ElementQuadTreeNode.ElementQuadTreePolygon;

public class ElementQuadTree {

	private final ElementQuadTreeNode root;
	private long itemCount;

	public ElementQuadTree(Area bbox) {
		this.root = new ElementQuadTreeNode(bbox);
		this.itemCount = 0;
	}

	public ElementQuadTree(Collection<Element> elements) {
		this.root = new ElementQuadTreeNode(elements);
		this.itemCount = 0;
	}
	
//	public void outputBounds(String basename) {
//		root.outputBounds(basename, 0);
//	}
	
	public boolean addAll(Collection<Element> elements) {
		boolean oneAdded = false;
		for (Element element : elements) {
			oneAdded = add(element) | oneAdded;
		}
		return oneAdded;
	}

	public boolean add(Element element) {

		boolean added = root.add(element);
		if (added) {
			itemCount++;
		}
		return added;
	}

	public Set<Element> get(Area bbox) {
		return root.get(bbox, new HashSet<Element>());
	}

	public Set<Element> get(java.awt.geom.Area polygon) {
		return root.get(new ElementQuadTreePolygon(polygon), new HashSet<Element>());
	}
	
	public Set<Element> get(Collection<List<Coord>> polygons) {
		return root.get(new ElementQuadTreePolygon(polygons),
				new HashSet<Element>());
	}

	public int getDepth() {
		return root.getDepth();
	}
	
	public Set<Element> get(List<Coord> polygon) {
		if (polygon.size() < 3) {
			return new HashSet<Element>();
		}
		if (polygon.get(0).equals(polygon.get(polygon.size() - 1)) == false) {
			return new HashSet<Element>();
		}
		return root.get(new ElementQuadTreePolygon(polygon),
				new HashSet<Element>());
	}

//	public Set<Element> get(List<Coord> polygon, int offset) {
//		if (polygon.size() < 3) {
//			return new HashSet<Element>();
//		}
//		if (polygon.get(0).equals(polygon.get(polygon.size() - 1)) == false) {
//			return null;
//		}
//		Set<Element> points = root.get(new QuadTreePolygon(polygon),
//				new HashSet<Element>());
//		if (offset > 0) {
//			for (Coord c : new ArrayList<Coord>(points.keySet())) {
//				if (isCloseToPolygon(c, polygon, offset)) {
//					points.remove(c);
//				}
//			}
//		}
//		return points;
//	}

	public void clear() {
		itemCount = 0;
		root.clear();
	}

	// TODO itemCount is not a good counter (coords or elements?)
	private long getSize() {
		return itemCount;
	}

	private boolean isCloseToPolygon(Coord point, List<Coord> polygon,
			int gap) {
		Iterator<Coord> polyIter = polygon.iterator();
		Coord c2 = polyIter.next();
		while (polyIter.hasNext()) {
			Coord c1 = c2;
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

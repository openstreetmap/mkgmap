package uk.me.parabola.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.util.ElementQuadTreeNode.ElementQuadTreePolygon;

public class ElementQuadTree {

	private final ElementQuadTreeNode root;
	
	public ElementQuadTree(Area bbox, Collection<Element> elements) {
		this.root = new ElementQuadTreeNode(bbox, elements);
	}

	public void remove(Element element) {
		root.remove(element);
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
	
	public long getCoordSize() {
		return root.getSize();
	}
	
	public boolean isEmpty() {
		return root.isEmpty();
	}
	

	public void clear() {
		root.clear();
	}
}

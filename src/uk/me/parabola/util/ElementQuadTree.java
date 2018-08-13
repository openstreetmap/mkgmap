package uk.me.parabola.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.mkgmap.reader.osm.Element;

public class ElementQuadTree {

	private final ElementQuadTreeNode root;
	
	public ElementQuadTree(Area bbox, Collection<Element> elements) {
		this.root = new ElementQuadTreeNode(bbox, elements);
	}

	public Set<Element> get(Area bbox) {
		HashSet<Element> res = new LinkedHashSet<>();
		root.get(bbox, res);
		return res;
	}

	public int getDepth() {
		return root.getDepth();
	}
	
	public boolean isEmpty() {
		return root.isEmpty();
	}
	

	public void clear() {
		root.clear();
	}
}

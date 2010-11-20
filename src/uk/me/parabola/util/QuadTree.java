package uk.me.parabola.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
	

	public List<Coord> get(List<Coord> polygon) {
		if (polygon.size() < 3) {
			return Collections.emptyList();
		}
		if (polygon.get(0).equals(polygon.get(polygon.size()-1))==false) {
			return null;
		}
		return root.get(new QuadTreePolygon(polygon), new ArrayList<Coord>(2000));
	}
	
	public void clear() {
		itemCount=0;
		root.clear();
	}
	
	public long getSize() {
		return itemCount;
	}
	
}

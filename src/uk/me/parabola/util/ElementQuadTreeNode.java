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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * A quad tree node. All nodes in the tree can contain elements. The element is stored in the node when its bbox is too large to fit into a single child.
 * @author Gerd Petermann
 *
 */
public final class ElementQuadTreeNode {

	private static final Logger log = Logger.getLogger(ElementQuadTreeNode.class);

	/** The maximum number of coords in the quadtree node. */
	private static final int MAX_POINTS = 1000;

	private List<BoxedElement> elementList; // can be null

	/** The bounds of this quadtree node */
	private final Area bounds;
	private final Rectangle boundsRect;

	/** Flag if this node and all sub-nodes are empty */
	private Boolean empty;

	/** The sub-nodes in case this node is not a leaf */
	private ElementQuadTreeNode[] children;

	/**
	 * Retrieves if this quadtree node (and all sub-nodes) contains any elements.
	 * @return <code>true</code> this quadtree node does not contain any elements; <code>false</code> else
	 */
	public boolean isEmpty() {
		if (empty == null) {
			if (elementList != null && !elementList.isEmpty()) {
				empty = false;
			} else {
				empty = true;
				for (ElementQuadTreeNode child : children) {
				if (!child.isEmpty()) {
					empty = false;
					break;
				}
				}
			}
		}
		return empty;
	}
	
	
	/**
	 * Retrieves the number of coords hold by this quadtree node and all sub-nodes.
	 * @return the number of coords
	 */
	private long getSize() {
		int items = 0;
		for (BoxedElement bel : elementList) {
			items += bel.getSize();
		}
		if (children != null) {
			for (ElementQuadTreeNode child : children) {
				items += child.getSize();
			}
		}
		return items;
	}

	/**
	 * Retrieves the depth of this quadtree node. Leaves have depth 1.
	 * @return the depth of this quadtree node
	 */
	public int getDepth() {
		if (children == null)
			return 1;
		int maxDepth = 0;
		for (ElementQuadTreeNode node : children) {
			maxDepth = Math.max(node.getDepth(), maxDepth);
		}
		return maxDepth + 1;
	}

	private ElementQuadTreeNode(Area bounds, List<BoxedElement> elements) {
		this.bounds = bounds;
		boundsRect = new Rectangle(bounds.getMinLong(), bounds.getMinLat(),
				bounds.getWidth(), bounds.getHeight());
		this.children = null;
		elementList = elements;
		empty = elementList.isEmpty();
		
		checkSplit();		
	}
	
	
	public ElementQuadTreeNode(Area bounds, Collection<Element> elements) {
		this.bounds = bounds;
		boundsRect = new Rectangle(bounds.getMinLong(), bounds.getMinLat(),
					bounds.getWidth(), bounds.getHeight());
		this.children = null;

		this.elementList = new ArrayList<>();
		
		for (Element el : elements) {
			BoxedElement bel = new BoxedElement(el);
			elementList.add(bel);
		}
		empty = elementList.isEmpty();
		checkSplit();
	}

	public Area getBounds() {
		return this.bounds;
	}

	public Rectangle getBoundsAsRectangle() {
		return boundsRect;
	}

	/**
	 * Checks if this quadtree node exceeds the maximum size and splits it in such a case.
	 */
	private void checkSplit() {
		if (elementList != null && elementList.size() > 1 && getSize() > MAX_POINTS) {
			split();
		}
	}

	/**
	 * Retrieves all elements that intersects the given bounding box.
	 * @param bbox the bounding box
	 * @param resultSet results are stored in this collection
	 * @return the resultList
	 */
	public void get(Area bbox, Set<Element> resultSet) {
		if (isEmpty() || bbox.intersects(bounds) == false) {
			return;
		}
		if (elementList != null) {
			for (BoxedElement bel : elementList) {
				if (bbox.intersects(bel.getBBox()))
					resultSet.add(bel.el);
			}
		}
		if (children != null) {
			for (ElementQuadTreeNode child : children) {
				child.get(bbox, resultSet);
			}
		}
	}

	/**
	 * Splits this quadtree node into 4 subnodes.
	 */
	private void split() {
		if (bounds.getHeight() <= 5 || bounds.getWidth() <= 5) {
			log.error("Do not split more due to too small bounds: " + bounds);
			return;
		}

		int halfLat = (bounds.getMinLat() + bounds.getMaxLat()) / 2;
		int halfLong = (bounds.getMinLong() + bounds.getMaxLong()) / 2;
		Area[] childBounds = new Area[4];
		
		childBounds[0] = new Area(bounds.getMinLat(), bounds.getMinLong(),
				halfLat, halfLong);
		childBounds[1] = new Area(halfLat, bounds.getMinLong(),
				bounds.getMaxLat(), halfLong);
		childBounds[2] = new Area(bounds.getMinLat(), halfLong, halfLat,
				bounds.getMaxLong());
		childBounds[3] = new Area(halfLat, halfLong, bounds.getMaxLat(),
				bounds.getMaxLong());

		List<List<BoxedElement>> childElems = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			childElems.add(new ArrayList<>());
		}
		boolean modified = false;
		Iterator<BoxedElement> iter = elementList.iterator();
		while (iter.hasNext()) {
			BoxedElement bel = iter.next();
			int count = 0;
			int lastIndex = -1;
			for (int i = 0; i < 4; i++) {
				if (childBounds[i].intersects(bel.getBBox())) {
					count++;
					lastIndex = i;
					if (childBounds[i].insideBoundary(bel.getBBox())) {
						break;
					}
				}
			}
			if (count == 1) {
				childElems.get(lastIndex).add(bel);
				iter.remove();
				modified = true;
			}
		}
		if (modified) {
			children = new ElementQuadTreeNode[4];
			for (int i = 0; i < 4; i++) {
				children[i] = new ElementQuadTreeNode(childBounds[i], childElems.get(i));
			}
		}
		if (elementList.isEmpty())
			elementList = null;
	}

	public void clear() {
		this.children = null;
		elementList = null;
	}
	
	private class BoxedElement{
		private Area bbox;
		private final Element el;
		
		Area getBBox() {
			if (el instanceof Way) {
				if (bbox == null)
					bbox = Area.getBBox(((Way) el).getPoints());
				return bbox;
			}
			return Area.getBBox(Arrays.asList(((Node) el).getLocation()));
		}
		
		BoxedElement(Element el) {
			this.el = el;
		}
		
		int getSize() {
			if (el instanceof Way)
				return ((Way) el).getPoints().size();
			return 1;
		}
	}
}

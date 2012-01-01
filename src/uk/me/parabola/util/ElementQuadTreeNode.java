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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Way;

public final class ElementQuadTreeNode {

	private static final Logger log = Logger.getLogger(ElementQuadTreeNode.class);

	/**
	 * A static empty list used for node objects. They have one coord only and
	 * it is too costly to create a list for each node
	 */
	private static final List<Coord> EMPTY_LIST = Collections.emptyList();
	
	/** The maximum number of coords in the quadtree node. */
	private static final int MAX_POINTS = 1000;

	/** Maps elements to its coords located in this quadtree node. */ 
	private Map<Element, List<Coord>>  elementMap;

	/** The bounds of this quadtree node */
	private final Area bounds;
	private final Rectangle boundsRect;

	/** Flag if this node and all subnodes are empty */
	private Boolean empty;

	/** The subnodes in case this node is not a leaf */
	private ElementQuadTreeNode[] children;

	public static final class ElementQuadTreePolygon {
		private final java.awt.geom.Area javaArea;
		private final Area bbox;

		public ElementQuadTreePolygon(java.awt.geom.Area javaArea) {
			this.javaArea = javaArea;
			Rectangle bboxRect = javaArea.getBounds();
			bbox = new Area(bboxRect.y, bboxRect.x, bboxRect.y
					+ bboxRect.height, bboxRect.x + bboxRect.width);
		}

		public ElementQuadTreePolygon(List<Coord> points) {
			this(new java.awt.geom.Area(Java2DConverter.createPolygon(points)));
		}

		public ElementQuadTreePolygon(Collection<List<Coord>> polygonList) {
			this.javaArea = new java.awt.geom.Area();
			for (List<Coord> polygon : polygonList) {
				javaArea.add(new java.awt.geom.Area(Java2DConverter
						.createPolygon(polygon)));
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

	/**
	 * Retrieves if this quadtree node (and all subnodes) contains any elements.
	 * @return <code>true</code> this quadtree node does not contain any elements; <code>false</code> else
	 */
	public boolean isEmpty() {
		if (empty == null) {
			if (isLeaf()) {
				empty = elementMap.isEmpty();
			} else {
				empty = true;
				for (ElementQuadTreeNode child : children) {
					if (child.isEmpty()==false) {
						empty = false;
						break;
					}
				}
			}
		}
		return empty;
	}
	
	
	/**
	 * Retrieves the number of coords hold by this quadtree node and all subnodes.
	 * @return the number of coords
	 */
	public long getSize() {
		if (isLeaf()) {
			int items = 0;
			for (List<Coord> points : elementMap.values()) {
				if (points == EMPTY_LIST) {
					items++;
				} else {
					items += points.size();
				}
			}
			return items;
		} else {
			int items = 0;
			for (ElementQuadTreeNode child : children) {
					items += child.getSize();
			}
			return items;
		}
	}

	/**
	 * Retrieves the depth of this quadtree node. Leaves have depth 1.
	 * @return the depth of this quadtree node
	 */
	public int getDepth() {
		if (isLeaf()) {
			return 1;
		} else {
			int maxDepth = 0;
			for (ElementQuadTreeNode node : children) {
				maxDepth = Math.max(node.getDepth(), maxDepth);
			}
			return maxDepth + 1;
		}
	}

	private ElementQuadTreeNode(Area bounds, Map<Element, List<Coord>> elements) {
		this.bounds = bounds;
		boundsRect = new Rectangle(bounds.getMinLong(), bounds.getMinLat(),
				bounds.getWidth(), bounds.getHeight());
		this.children = null;
		elementMap =elements;
		empty = elementMap.isEmpty();
		
		checkSplit();		
	}
	
	
	public ElementQuadTreeNode(Area bounds, Collection<Element> elements) {
		this.bounds = bounds;
		boundsRect = new Rectangle(bounds.getMinLong(), bounds.getMinLat(),
					bounds.getWidth(), bounds.getHeight());
		this.children = null;

		this.elementMap = new HashMap<Element, List<Coord>>();
		
		for (Element el : elements) {
			if (el instanceof Way) {
				List<Coord> points = ((Way) el).getPoints();
				// no need to create a copy of the points because the list is never changed
				elementMap.put(el, points);
			} else if (el instanceof Node) {
				elementMap.put(el, EMPTY_LIST);
			}
		}
		empty = elementMap.isEmpty();
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
		if (getSize() > MAX_POINTS) {
			split();
		}
	}

	/**
	 * Removes the element from this quadtree node and all subnodes.
	 * @param elem the element to be removed
	 */
	public void remove(Element elem) {
		if (isLeaf()) {
			elementMap.remove(elem);
			empty = elementMap.isEmpty();
		} else {
			if (elem instanceof Node) {
				Node n = (Node) elem;
				for (ElementQuadTreeNode child : children) {
					if (child.getBounds().contains(n.getLocation())) {
						child.remove(elem);
						if (child.isEmpty()) {
							// update the empty flag
							empty = null;
						}
						break;
					}
				}
			} else if (elem instanceof Way) {
				for (ElementQuadTreeNode child : children) {
					for (Coord c : ((Way) elem).getPoints()) {
						if (child.getBounds().contains(c)) {
							// found one point covered by the child
							// => remove the element and check the next child
							child.remove(elem);
							if (empty != null && child.isEmpty()) {
								empty = null;
							}
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * Retrieves all elements that intersects the given bounding box.
	 * @param bbox the bounding box
	 * @param resultList results are stored in this collection
	 * @return the resultList
	 */
	public Set<Element> get(Area bbox, Set<Element> resultList) {
		if (isEmpty()) {
			return resultList;
		}
		if (isLeaf()) {
			if (bbox.getMinLat() <= bounds.getMinLat()
					&& bbox.getMaxLat() >= bounds.getMaxLat()
					&& bbox.getMinLong() <= bounds.getMinLong()
					&& bbox.getMaxLong() >= bounds.getMaxLong()) {

				// the bounding box is contained completely in the bbox
				// => add all points without further check
				resultList.addAll(elementMap.keySet());
			} else {
				// check each point
				for (Entry<Element, List<Coord>> elem : elementMap.entrySet()) {
					if (elem.getKey() instanceof Node) {
						Node n = (Node) elem.getKey();
						if (bbox.contains(n.getLocation())) {
							resultList.add(n);
						}
					} else if (elem.getKey() instanceof Way) {
						// no need to check - the element is already in the result list
						if (resultList.contains(elem.getKey())) {
							continue;
						}
						for (Coord c : elem.getValue()) {
							if (bbox.contains(c)) {
								resultList.add(elem.getKey());
								break;
							}
						}
					}
				}
			}
		} else {
			for (ElementQuadTreeNode child : children) {
				if (child.isEmpty() == false
						&& bbox.intersects(child.getBounds())) {
					resultList = child.get(bbox, resultList);
				}
			}
		}
		return resultList;
	}

	/**
	 * Retrieves all elements that intersects the given polygon.
	 * @param polygon the polygon
	 * @param resultList results are stored in this collection
	 * @return the resultList
	 */
	public Set<Element> get(ElementQuadTreePolygon polygon,
			Set<Element> resultList) {
		if (isEmpty()) {
			return resultList;
		}
		if (polygon.getBbox().intersects(getBounds())) {
			if (isLeaf()) {
				for (Entry<Element, List<Coord>> elem : elementMap.entrySet()) {
					if (resultList.contains(elem.getKey())) {
						continue;
					}
					if (elem.getKey() instanceof Node) {
						Node n = (Node)elem.getKey();
						Coord c = n.getLocation();
						if (polygon.getArea().contains(c.getLongitude(),
								c.getLatitude())) {
							resultList.add(n);
						}
					} else if (elem.getKey() instanceof Way) {
						for (Coord c : elem.getValue()) {
							if (polygon.getArea().contains(c.getLongitude(),
									c.getLatitude())) {
								resultList.add(elem.getKey());
								break;
							}
						}
					}
				}
			} else {
				for (ElementQuadTreeNode child : children) {
					if (child.isEmpty()==false 
							&& polygon.getArea().intersects(
									child.getBoundsAsRectangle())) {
						java.awt.geom.Area subArea = (java.awt.geom.Area) polygon
								.getArea().clone();

						subArea.intersect(Java2DConverter.createBoundsArea(new Area(child.getBounds()
								.getMinLat() - 1, child.getBounds()
								.getMinLong() - 1, child.getBounds()
								.getMaxLat() + 1, child.getBounds()
								.getMaxLong() + 1))
								);
						child.get(new ElementQuadTreePolygon(subArea),
									resultList);
					}
				}
			}
		}
		return resultList;

	}

	/**
	 * Retrieves if this quadtree node is a leaf.
	 * @return <code>true</code> this node is a leaf
	 */
	public boolean isLeaf() {
		return elementMap != null;
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
		children = new ElementQuadTreeNode[4];
		Area[] childBounds = new Area[4];
		
		childBounds[0] = new Area(bounds.getMinLat(), bounds.getMinLong(),
				halfLat, halfLong);
		childBounds[1] = new Area(halfLat, bounds.getMinLong(),
				bounds.getMaxLat(), halfLong);
		childBounds[2] = new Area(bounds.getMinLat(), halfLong, halfLat,
				bounds.getMaxLong());
		childBounds[3] = new Area(halfLat, halfLong, bounds.getMaxLat(),
				bounds.getMaxLong());

		List<Map<Element, List<Coord>>> childElems = new ArrayList<Map<Element, List<Coord>>>(4);
		for (int i = 0; i < 4; i++) {
			childElems.add(new HashMap<Element, List<Coord>>());
		}
		for (Entry<Element,List<Coord>> elem : elementMap.entrySet()) {
			if (elem.getKey() instanceof Node) {
				Node node = (Node) elem.getKey();
				for (int i = 0; i < childBounds.length; i++) {
					if (childBounds[i].contains(node.getLocation())) {
						childElems.get(i).put(node, EMPTY_LIST);
						break;
					}
				}
			} else if (elem.getKey() instanceof Way) {
				List<List<Coord>> points = new ArrayList<List<Coord>>(4);
				for (int i = 0; i < 4; i++) {
					// usually ways are quite local
					// therefore there is a high probability that only one child is covered
					// dim the new list as the old list
					points.add(new ArrayList<Coord>(elem.getValue().size()));
				}
				for (Coord c : elem.getValue()) {
					for (int i = 0; i < childBounds.length; i++) {
						if (childBounds[i].contains(c)) {
							points.get(i).add(c);
							break;
						}
					}				
				}
				for (int i = 0; i< 4; i++) {
					if (points.get(i).isEmpty()==false) {
						childElems.get(i).put(elem.getKey(), points.get(i));
					}
				}
			}
		}
		
		for (int i = 0; i < 4; i++) {
			children[i] = new ElementQuadTreeNode(childBounds[i], childElems.get(i));
		}
		
		elementMap = null;
	}

	public void clear() {
		this.children = null;
		elementMap = new HashMap<Element, List<Coord>>();
	}
}

package uk.me.parabola.util;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;

public final class ElementQuadTreeNode {

	private static final Logger log = Logger.getLogger(ElementQuadTreeNode.class);

	private static final int MAX_POINTS = 1000;

	private MultiHashMap<Coord, Element> points;
	private final Area bounds;
	private Area coveredBounds;

	public Area getCoveredBounds() {
		return coveredBounds;
	}

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

	public ElementQuadTreeNode(Area bounds) {
		this(bounds, Collections.<Element> emptyList());
	}
	
	
	public ElementQuadTreeNode(Collection<Element> elements) {
		this.children = null;

		int minLat = Integer.MAX_VALUE;
		int maxLat = Integer.MIN_VALUE;
		int minLong = Integer.MAX_VALUE;
		int maxLong = Integer.MIN_VALUE;

		this.points = new MultiHashMap<Coord, Element>();
		for (Element el : elements) {
			Collection<Coord> coords = null;
			if (el instanceof Relation) {
				continue;
			} else if (el instanceof Way) {
				Way w = (Way) el;
				if (w.isClosed()) {
					coords = w.getPoints().subList(0, w.getPoints().size()-1);	
				} else {
					coords = w.getPoints();
				}
			} else if (el instanceof Node) {
				coords = Collections.singleton(((Node) el).getLocation());
			}
			
			for (Coord c : coords) {
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
				points.add(c, el);
			}

		}
		coveredBounds = new Area(minLat, minLong, maxLat, maxLong);
		this.bounds = coveredBounds;
		
		if (points.size() > MAX_POINTS) {
			split();
		} 
	}
	
	public int getDepth() {
		if (isLeaf()) {
			return 1;
		} else {
			int maxDepth = 0;
			for (ElementQuadTreeNode node :children) {
				maxDepth = Math.max(node.getDepth(), maxDepth);
			}
			return maxDepth+1;
		}
	}
	
//	public void outputBounds(String basename, int level) {
////		if (level > 8 ) {
////			return;
////		}
//		
//		if (isLeaf()) {
//			GpxCreator.createAreaGpx(basename+level+"_"+points.keySet().size(), coveredBounds);
////			GpxCreator.createGpx(basename+level+"p"+points.keySet().size(), new ArrayList<Coord>(), new ArrayList<Coord>(points.keySet()));
//		} else {
////			GpxCreator.createAreaGpx(basename+level, coveredBounds);
//			int i = 0;
//			for (ElementQuadTreeNode node : children) {
//				i++;
//				node.outputBounds(basename+i+"_", level+1);
//			}
//		}
//	}

	public ElementQuadTreeNode(Area bounds, Collection<Element> elements) {
		this.bounds = bounds;
		this.children = null;

		int minLat = Integer.MAX_VALUE;
		int maxLat = Integer.MIN_VALUE;
		int minLong = Integer.MAX_VALUE;
		int maxLong = Integer.MIN_VALUE;

		this.points = new MultiHashMap<Coord, Element>();
		for (Element el : elements) {
			Collection<Coord> coords = null;
			if (el instanceof Relation) {
				continue;
			} else if (el instanceof Way) {
				Way w = (Way) el;
				if (w.isClosed()) {
					coords = w.getPoints().subList(0, w.getPoints().size()-1);	
				} else {
					coords = w.getPoints();
				}
			} else if (el instanceof Node) {
				coords = Collections.singleton(((Node) el).getLocation());
			}
			
			for (Coord c : coords) {
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
				points.add(c, el);
			}

		}
		if (minLat > maxLat || minLong > maxLong) {
			coveredBounds = new Area(bounds.getMinLat(), bounds.getMinLong(), bounds.getMinLat()+1, bounds.getMinLong()+1);
		} else {
			coveredBounds = new Area(minLat, minLong, maxLat, maxLong);
		}

		if (points.size() > MAX_POINTS) {
			split();
		} 
	}

	public Area getBounds() {
		return this.bounds;
	}
	
	public Rectangle getRectBounds() {
		return new Rectangle(bounds.getMinLong(), bounds.getMinLat(), bounds.getWidth(), bounds.getHeight());
	}

	private boolean add(Coord c, Element element) {
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
			points.add(c,element);
			if (points.size() > MAX_POINTS)
				split();
			return true;
		} else {
			for (ElementQuadTreeNode nodes : children) {
				if (nodes.getBounds().contains(c)) {
					return nodes.add(c, element);
				}
			}
			return false;
		}
	}

	public boolean add(Element c) {
		if (c instanceof Relation) {
			log.error("Relations are not supported by this quadtree implementation. Skipping relation "
					+ c.toBrowseURL());
			return false;
		} else if (c instanceof Way) {
			// add all points to the tree
			Way w = (Way) c;
			List<Coord> points;
			if (w.isClosed()) {
				points = w.getPoints().subList(0, w.getPoints().size()-1);	
			} else {
				points = w.getPoints();
			}
			
			boolean allOk = true;
			for (Coord cp : points) {
				allOk = add(cp, c) && allOk;
			}
			return allOk;
		} else if (c instanceof Node) {
			return add(((Node) c).getLocation(), c);
		} else {
			log.error("Unsupported element type: "+c);
			return false;
		}
	}

	public Set<Element> get(Area bbox, Set<Element> resultList) {
		if (isLeaf()) {
			if (bbox.getMinLat() <= coveredBounds.getMinLat()
					&& bbox.getMaxLat() >= coveredBounds.getMaxLat()
					&& bbox.getMinLong() <= coveredBounds.getMinLong()
					&& bbox.getMaxLong() >= coveredBounds.getMaxLong()) {

				// the bounding box is contained completely in the bbox
				// => add all points without further check
				for (List<Element> elem : points.values())
					resultList.addAll(elem);
			} else {
				// check each point
				for (Entry<Coord, List<Element>> e : points.entrySet()) {
					if (bbox.contains(e.getKey())) {
						resultList.addAll(e.getValue());
					}
				}
			}
		} else {
			for (ElementQuadTreeNode child : children) {
				if (bbox.intersects(child.getCoveredBounds())) {
					resultList = child.get(bbox, resultList);
				}
			}
		}
		return resultList;
	}

	public Set<Element> get(ElementQuadTreePolygon polygon,
			Set<Element> resultList) {
		if (polygon.getBbox().intersects(getBounds())) {
			if (isLeaf()) {
				for (Entry<Coord, List<Element>> e : points.entrySet()) {
					if (polygon.getArea().contains(e.getKey().getLongitude(),
							e.getKey().getLatitude())) {
						resultList.addAll(e.getValue());
					}
				}
			} else {
				for (ElementQuadTreeNode child : children) {
					if (polygon.getArea().intersects(child. getRectBounds())) {
						java.awt.geom.Area subArea = (java.awt.geom.Area) polygon
								.getArea().clone();
						subArea.intersect(createArea(child.getBounds()));
						if (subArea.isEmpty() == false)
							child.get(new ElementQuadTreePolygon(subArea), resultList);
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
		if (bounds.getHeight() <= 5 || bounds.getWidth() <= 5) {
			log.error("Do not split more due to too small bounds: "+bounds);
			return;
		}

		int halfLat = (bounds.getMinLat() + bounds.getMaxLat()) / 2;
		int halfLong = (bounds.getMinLong() + bounds.getMaxLong()) / 2;
		children = new ElementQuadTreeNode[4];

		Area swBounds = new Area(bounds.getMinLat(), bounds.getMinLong(),
				halfLat, halfLong);
		Area nwBounds = new Area(halfLat, bounds.getMinLong(),
				bounds.getMaxLat(), halfLong);
		Area seBounds = new Area(bounds.getMinLat(), halfLong, halfLat,
				bounds.getMaxLong());
		Area neBounds = new Area(halfLat, halfLong, bounds.getMaxLat(),
				bounds.getMaxLong());
		
		children[0] = new ElementQuadTreeNode(swBounds);
		children[1] = new ElementQuadTreeNode(nwBounds);
		children[2] = new ElementQuadTreeNode(seBounds);
		children[3] = new ElementQuadTreeNode(neBounds);
		
		MultiHashMap<Coord, Element> copyPoints = points;
		points = null;
		for (Entry<Coord, List<Element>> c : copyPoints.entrySet()) {
			for (Element el : c.getValue())
				add(c.getKey(), el);
		}
	}

	public void clear() {
		this.children = null;
		points = new MultiHashMap<Coord, Element>();
		coveredBounds = new Area(Integer.MAX_VALUE, Integer.MAX_VALUE,
				Integer.MIN_VALUE, Integer.MIN_VALUE);
	}
}

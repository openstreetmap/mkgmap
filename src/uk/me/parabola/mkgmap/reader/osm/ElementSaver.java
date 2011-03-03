/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.reader.osm;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LineClipper;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This is where we save the elements read from any of the file formats that
 * are in OSM format.  OSM format means that there are nodes, ways and relations
 * and they have tags.
 *
 * Both the XML format and the binary format use this.
 *
 * In the early days of mkgmap, the nodes and ways were converted as soon
 * as they were encountered in the input file.  After relations that is not
 * possible, you have to save up all the nodes and ways as they might be
 * needed for relations.
 *
 * We also want access to the other ways/nodes to generate sea polygons,
 * prepare for routing etc.
 *
 * @author Steve Ratcliffe
 */
public class ElementSaver {
	private static final Logger log = Logger.getLogger(ElementSaver.class);

	private Map<Long, Coord> coordMap = new HashMap<Long, Coord>(50000);

	private Map<Long, Node> nodeMap;
	private Map<Long, Way> wayMap;
	private Map<Long, Relation> relationMap;

	private final Map<Long, List<Map.Entry<String,Relation>>> deferredRelationMap = new HashMap<Long, List<Map.Entry<String,Relation>>>();

	// This is an explicitly given bounding box from the input file command line etc.
	private Area boundingBox;

	// This is a calculated bounding box, which is only available if there is
	// no given bounding box.
	private int minLat = Utils.toMapUnit(180.0);
	private int minLon = Utils.toMapUnit(180.0);
	private int maxLat = Utils.toMapUnit(-180.0);
	private int maxLon = Utils.toMapUnit(-180.0);

	// Options
	private final boolean ignoreTurnRestrictions;
	private final Double minimumArcLength;

	private boolean roadsReachBoundary;

	/** name of the tag that contains a ;-separated list of tagnames that should be removed after all elements have been processed */
	public static final String MKGMAP_REMOVE_TAG = "mkgmap:removetags";
	/** tagvalue of the {@link ElementSaver#MKGMAP_REMOVE_TAG} if all tags should be removed */
	public static final String MKGMAP_REMOVE_TAG_ALL_KEY = "mkgmap:ALL";

	public ElementSaver(EnhancedProperties args) {
		if (args.getProperty("preserve-element-order", false)) {
			nodeMap = new LinkedHashMap<Long, Node>(5000);
			wayMap = new LinkedHashMap<Long, Way>(5000);
			relationMap = new LinkedHashMap<Long, Relation>();
		} else {
			nodeMap = new HashMap<Long, Node>();
			wayMap = new HashMap<Long, Way>();
			relationMap = new HashMap<Long, Relation>();
		}

		String rsa = args.getProperty("remove-short-arcs", null);
		if(rsa != null)
			minimumArcLength = (rsa.length() > 0)? Double.parseDouble(rsa) : 0.0;
		else
			minimumArcLength = null;

		ignoreTurnRestrictions = args.getProperty("ignore-turn-restrictions", false);
	}

	/**
	 * {@inheritDoc}
	 *
	 * We use this to calculate a bounding box in the situation where none is
	 * given.  In the usual case where there is a bounding box, then nothing
	 * is done.
	 *
	 * @param co The point.
	 */
	public void addPoint(long id, Coord co) {
		coordMap.put(id, co);
		if (boundingBox == null) {
			if (co.getLatitude() < minLat)
				minLat = co.getLatitude();
			if (co.getLatitude() > maxLat)
				maxLat = co.getLatitude();

			if (co.getLongitude() < minLon)
				minLon = co.getLongitude();
			if (co.getLongitude() > maxLon)
				maxLon = co.getLongitude();
		}
	}

	/**
	 * Add the given node and save it. The node should have tags.
	 *
	 * @param node The osm node.
	 */
	public void addNode(Node node) {
		nodeMap.put(node.getId(), node);
	}

	/**
	 * Add the given way.
	 *
	 * @param way The osm way.
	 */
	public void addWay(Way way) {
		wayMap.put(way.getId(), way);
	}

	/**
	 * Add the given relation.
	 *
	 * @param rel The osm relation.
	 */
	public void addRelation(Relation rel) {
		String type = rel.getTag("type");
		if (type != null) {
			if ("multipolygon".equals(type) || "boundary".equals(type)) {
				rel = createMultiPolyRelation(rel); 
			} else if("restriction".equals(type)) {
				if (ignoreTurnRestrictions)
					rel = null;
				else
					rel = new RestrictionRelation(rel);
			}
		}

		if(rel != null) {
			long id = rel.getId();
			relationMap.put(rel.getId(), rel);
			
			rel.processElements();

			List<Map.Entry<String,Relation>> entries = deferredRelationMap.remove(id);
			if (entries != null)
				for (Map.Entry<String,Relation> entry : entries)
					entry.getValue().addElement(entry.getKey(), rel);
		}
	}

	/**
	 * Create a multipolygon relation.  Has to be here as they use shared maps.
	 * Would like to change how the constructor works so that was not needed.
	 * @param rel The original relation, that the result will replace.
	 * @return A new multi polygon relation, based on the input relation.
	 */
	public Relation createMultiPolyRelation(Relation rel) {
		return new MultiPolygonRelation(rel, wayMap, getBoundingBox());
	}
	
	public SeaPolygonRelation createSeaPolyRelation(Relation rel) {
		return new SeaPolygonRelation(rel, wayMap, getBoundingBox());
	}

	public void setBoundingBox(Area bbox) {
		boundingBox = bbox;
	}

	public Coord getCoord(long id) {
		return coordMap.get(id);
	}

	public Node getNode(long id) {
		return nodeMap.get(id);
	}

	public Way getWay(long id) {
		return wayMap.get(id);
	}

	public Relation getRelation(long id) {
		return relationMap.get(id);
	}
	
	public void finishLoading() {
		coordMap = null;
		finishMultiPolygons();
	}

	/**
	 * After the input file is read, this is called to convert the saved information
	 * into the general intermediate format.
	 *
	 * @param converter The Converter to use.
	 */
	public void convert(OsmConverter converter) {

		// We only do this if an explicit bounding box was given.
		if (boundingBox != null && minimumArcLength != null)
			makeBoundaryNodes();

		if(minimumArcLength != null)
			removeShortArcsByMergingNodes(minimumArcLength);

		converter.setBoundingBox(getBoundingBox());

		for (Relation r : relationMap.values())
			converter.convertRelation(r);

		for (Node n : nodeMap.values())
			converter.convertNode(n);

		nodeMap = null;

		for (Way w: wayMap.values())
			converter.convertWay(w);

		wayMap = null;

		converter.end();

		relationMap = null;
	}


	private void finishMultiPolygons() {
		for (Way way : wayMap.values()) {
			String removeTag = way.getTag(MKGMAP_REMOVE_TAG);
			if (removeTag == null) {
				continue;
			}
			if (MKGMAP_REMOVE_TAG_ALL_KEY.equals(removeTag)) {
				if (log.isDebugEnabled())
					log.debug("Remove all tags from way",way.getId(),way.toTagString());
				way.removeAllTags();
			} else {
				String[] tagsToRemove = removeTag.split(";");
				if (log.isDebugEnabled())
					log.debug("Remove tags",Arrays.toString(tagsToRemove),"from way",way.getId(),way.toTagString());
				for (String rTag : tagsToRemove) {
					way.deleteTag(rTag);
				}
				way.deleteTag(MKGMAP_REMOVE_TAG);
			}
		}
	}

	/**
	 *
	 * "soft clip" each way that crosses a boundary by adding a point
	 * at each place where it meets the boundary
	 */
	private void makeBoundaryNodes() {
		log.info("Making boundary nodes");
		int numBoundaryNodesDetected = 0;
		int numBoundaryNodesAdded = 0;
		for(Way way : wayMap.values()) {
			List<Coord> points = way.getPoints();

			// clip each segment in the way against the bounding box
			// to find the positions of the boundary nodes - loop runs
			// backwards so we can safely insert points into way
			for (int i = points.size() - 1; i >= 1; --i) {
				Coord[] pair = { points.get(i - 1), points.get(i) };
				Coord[] clippedPair = LineClipper.clip(getBoundingBox(), pair);
				// we're only interested in segments that touch the
				// boundary
				if (clippedPair != null) {
					// the segment touches the boundary or is
					// completely inside the bounding box
					if (clippedPair[1] != points.get(i)) {
						// the second point in the segment is outside
						// of the boundary
						assert clippedPair[1].getOnBoundary();
						// insert boundary point before the second point
						points.add(i, clippedPair[1]);
						// it's a newly created point so make its
						// highway count one
						clippedPair[1].incHighwayCount();
						++numBoundaryNodesAdded;
						if(!roadsReachBoundary && way.getTag("highway") != null)
							roadsReachBoundary = true;
					} else if(clippedPair[1].getOnBoundary())
						++numBoundaryNodesDetected;

					if (clippedPair[1].getOnBoundary()) {
						// the point is on the boundary so make sure
						// it becomes a node
						clippedPair[1].incHighwayCount();
					}

					if (clippedPair[0] != points.get(i - 1)) {
						// the first point in the segment is outside
						// of the boundary
						assert clippedPair[0].getOnBoundary();
						// insert boundary point after the first point
						points.add(i, clippedPair[0]);
						// it's a newly created point so make its
						// highway count one
						clippedPair[0].incHighwayCount();
						++numBoundaryNodesAdded;
						if(!roadsReachBoundary && way.getTag("highway") != null)
							roadsReachBoundary = true;
					} else if (clippedPair[0].getOnBoundary())
						++numBoundaryNodesDetected;

					if (clippedPair[0].getOnBoundary()) {
						// the point is on the boundary so make sure
						// it becomes a node
						clippedPair[0].incHighwayCount();
					}
				}
			}
		}

		log.info("Making boundary nodes - finished (" + numBoundaryNodesAdded + " added, " + numBoundaryNodesDetected + " detected)");
	}

	private void removeShortArcsByMergingNodes(double minArcLength) {
		// keep track of how many arcs reach a given point
		Map<Coord, Integer> arcCounts = new IdentityHashMap<Coord, Integer>();
		log.info("Removing short arcs (min arc length = " + minArcLength + "m)");
		log.info("Removing short arcs - counting arcs");
		for (Way w : wayMap.values()) {
			List<Coord> points = w.getPoints();
			int numPoints = points.size();
			if (numPoints >= 2) {
				// all end points have 1 arc
				incArcCount(arcCounts, points.get(0), 1);
				incArcCount(arcCounts, points.get(numPoints - 1), 1);
				// non-end points have 2 arcs but ignore points that
				// are only in a single way
				for (int i = numPoints - 2; i >= 1; --i) {
					Coord p = points.get(i);
					// if this point is a CoordPOI it may become a
					// node later even if it isn't actually a junction
					// between ways at this time - so for the purposes
					// of short arc removal, consider it to be a node
					if (p.getHighwayCount() > 1 || p instanceof CoordPOI)
						incArcCount(arcCounts, p, 2);
				}
			}
		}

		// replacements maps those nodes that have been replaced to
		// the node that replaces them
		Map<Coord, Coord> replacements = new IdentityHashMap<Coord, Coord>();
		Map<Way, Way> complainedAbout = new IdentityHashMap<Way, Way>();
		boolean anotherPassRequired = true;
		int pass = 0;
		int numWaysDeleted = 0;
		int numNodesMerged = 0;

		while (anotherPassRequired && pass < 10) {
			anotherPassRequired = false;
			log.info("Removing short arcs - PASS " + ++pass);
			Way[] ways = wayMap.values().toArray(new Way[wayMap.size()]);
			for (Way way : ways) {
				List<Coord> points = way.getPoints();
				if (points.size() < 2) {
					log.info("  Way " + way.getTag("name") + " (" + way.toBrowseURL() + ") has less than 2 points - deleting it");
					wayMap.remove(way.getId());
					++numWaysDeleted;
					continue;
				}
				// scan through the way's points looking for nodes and
				// check to see that the nodes are not too close to
				// each other
				int previousNodeIndex = 0; // first point will be a node
				Coord previousPoint = points.get(0);
				double arcLength = 0;
				for (int i = 0; i < points.size(); ++i) {
					Coord p = points.get(i);

					// check if this point is to be replaced because
					// it was previously merged into another point
					Coord replacement = null;
					Coord r = p;
					while ((r = replacements.get(r)) != null) {
						replacement = r;
					}

					if (replacement != null) {
						assert !p.getOnBoundary() : "Boundary node replaced";
						p = replacement;
						// replace point in way
						points.set(i, p);
						if (i == 0)
							previousPoint = p;
						anotherPassRequired = true;
					}

					if (i == 0) {
						// first point in way is a node so preserve it
						// to ensure it won't be filtered out later
						p.preserved(true);

						// nothing more to do with this point
						continue;
					}

					// this is not the first point in the way

					if (p == previousPoint) {
						log.info("  Way " + way.getTag("name") + " (" + way.toBrowseURL() + ") has consecutive identical points at " + p.toOSMURL() + " - deleting the second point");
						points.remove(i);
						// hack alert! rewind the loop index
						--i;
						anotherPassRequired = true;
						continue;
					}

					arcLength += p.distance(previousPoint);
					previousPoint = p;

					// this point is a node if it has an arc count
					Integer arcCount = arcCounts.get(p);

					if (arcCount == null) {
						// it's not a node so go on to next point
						continue;
					}

					// preserve the point to stop the node being
					// filtered out later
					p.preserved(true);

					Coord previousNode = points.get(previousNodeIndex);
					if (p == previousNode) {
						// this node is the same point object as the
						// previous node - leave it for now and it
						// will be handled later by the road loop
						// splitter
						previousNodeIndex = i;
						arcLength = 0;
						continue;
					}

					boolean mergeNodes = false;

					if (p.equals(previousNode)) {
						// nodes have identical coordinates and are
						// candidates for being merged

						// however, to avoid trashing unclosed loops
						// (e.g. contours) we only want to merge the
						// nodes when the length of the arc between
						// the nodes is small

						if(arcLength == 0 || arcLength < minArcLength)
							mergeNodes = true;
						else if(complainedAbout.get(way) == null) {
							log.info("  Way " + way.getTag("name") + " (" + way.toBrowseURL() + ") has unmerged co-located nodes at " + p.toOSMURL() + " - they are joined by a " + (int)(arcLength * 10) / 10.0 + "m arc");
							complainedAbout.put(way, way);
						}
					}
					else if(minArcLength > 0 && minArcLength > arcLength) {
						// nodes have different coordinates but the
						// arc length is less than minArcLength so
						// they will be merged
						mergeNodes = true;
					}

					if (!mergeNodes) {
						// keep this node and go look at the next point
						previousNodeIndex = i;
						arcLength = 0;
						continue;
					}

					if (previousNode.getOnBoundary() && p.getOnBoundary()) {
						if (p.equals(previousNode)) {
							// the previous node has identical
							// coordinates to the current node so it
							// can be replaced but to avoid the
							// assertion above we need to forget that
							// it is on the boundary
							previousNode.setOnBoundary(false);
						} else {
							// both the previous node and this node
							// are on the boundary and they don't have
							// identical coordinates
							if(complainedAbout.get(way) == null) {
								log.warn("  Way " + way.getTag("name") + " (" + way.toBrowseURL() + ") has short arc (" + String.format("%.2f", arcLength) + "m) at " + p.toOSMURL() + " - but it can't be removed because both ends of the arc are boundary nodes!");
								complainedAbout.put(way, way);
							}
							break; // give up with this way
						}
					}

					// reset arc length
					arcLength = 0;

					// do the merge
					++numNodesMerged;
					if (p.getOnBoundary()) {
						// current point is a boundary node so we need
						// to merge the previous node into this node
						replacements.put(previousNode, p);
						// add the previous node's arc count to this
						// node
						incArcCount(arcCounts, p, arcCounts.get(previousNode) - 1);
						// remove the preceding point(s) back to and
						// including the previous node
						for(int j = i - 1; j >= previousNodeIndex; --j) {
							points.remove(j);
						}
					} else {
						// current point is not on a boundary so merge
						// this node into the previous one
						replacements.put(p, previousNode);
						// add this node's arc count to the node that
						// is replacing it
						incArcCount(arcCounts, previousNode, arcCount - 1);
						// reset previous point to be the previous
						// node
						previousPoint = previousNode;
						// remove the point(s) back to the previous
						// node
						for (int j = i; j > previousNodeIndex; --j) {
							points.remove(j);
						}
					}

					// hack alert! rewind the loop index
					i = previousNodeIndex;
					anotherPassRequired = true;
				}
			}
		}

		if (anotherPassRequired)
			log.error("Removing short arcs - didn't finish in " + pass + " passes, giving up!");
		else
			log.info("Removing short arcs - finished in", pass, "passes (", numNodesMerged, "nodes merged,", numWaysDeleted, "ways deleted)");
	}

	private void incArcCount(Map<Coord, Integer> map, Coord p, int inc) {
		Integer i = map.get(p);
		if(i != null)
			inc += i;
		map.put(p, inc);
	}

	public Map<Long, Way> getWays() {
		return wayMap;
	}

	/**
	 * Get the bounding box.  This is either the one that was explicitly included in the input
	 * file, or if none was given, the calculated one.
	 */
	public Area getBoundingBox() {
		if (boundingBox != null) {
			return boundingBox;
		} else if (minLat == Utils.toMapUnit(180.0) && maxLat == Utils.toMapUnit(-180.0)) {
			return new Area(0, 0, 0, 0);
		} else {
			return new Area(minLat, minLon, maxLat, maxLon);
		}
	}

	public void deferRelation(long id, Relation rel, String role) {
		// The relation may be defined later in the input.
		// Defer the lookup.
		Map.Entry<String,Relation> entry =
				new AbstractMap.SimpleEntry<String,Relation>(role, rel);

		List<Map.Entry<String,Relation>> entries = deferredRelationMap.get(id);
		if (entries == null) {
			entries = new ArrayList<Map.Entry<String,Relation>>();
			deferredRelationMap.put(id, entries);
		}

		entries.add(entry);
	}
}

/*
 * Copyright (C) 2006 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap.reader.osm.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Exit;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LineClipper;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.RoadNetwork;
import uk.me.parabola.mkgmap.reader.osm.CoordPOI;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GeneralRelation;
import uk.me.parabola.mkgmap.reader.osm.MultiPolygonRelation;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.RestrictionRelation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads and parses the OSM XML format.
 *
 * @author Steve Ratcliffe
 */
class Osm5XmlHandler extends DefaultHandler {
	private static final Logger log = Logger.getLogger(Osm5XmlHandler.class);

	private int mode;

	private Map<Long, Coord> coordMap = new HashMap<Long, Coord>(50000);
	// currently, nodeIdMap is only used for the short arc removal
	private Map<Coord, Long> nodeIdMap = new IdentityHashMap<Coord, Long>();
	private Map<Long, Node> nodeMap;
	private Map<Long, Way> wayMap;
	private Map<Long, Relation> relationMap;
	private final Map<String, Long> fakeIdMap = new HashMap<String, Long>();
	private final List<Node> exits = new ArrayList<Node>();
	private final List<Way> motorways = new ArrayList<Way>();

	private static final int MODE_NODE = 1;
	private static final int MODE_WAY = 2;
	private static final int MODE_BOUND = 3;
	private static final int MODE_RELATION = 4;
	private static final int MODE_BOUNDS = 5;

	private static final long CYCLEWAY_ID_OFFSET = 0x10000000;

	private Node currentNode;
	private Way currentWay;
	private Relation currentRelation;
	private long currentElementId;

	private OsmConverter converter;
	private MapDetails mapper;
	private Area bbox;
	private Runnable endTask;

	private long nextFakeId = 1;

	private final boolean makeOppositeCycleways;
	private final boolean makeCycleways;
	private final boolean ignoreBounds;
	private final boolean ignoreTurnRestrictions;
	private final boolean linkPOIsToWays;
	private final boolean routing;
	private final Double minimumArcLength;
	private final String frigRoundabouts;

	public Osm5XmlHandler(EnhancedProperties props) {
		if(props.getProperty("make-all-cycleways", false)) {
			makeOppositeCycleways = makeCycleways = true;
		}
		else {
			makeOppositeCycleways = props.getProperty("make-opposite-cycleways", false);
			makeCycleways = props.getProperty("make-cycleways", false);
		}
		linkPOIsToWays = props.getProperty("link-pois-to-ways", false);
		ignoreBounds = props.getProperty("ignore-osm-bounds", false);
		routing = props.containsKey("route");
		String rsa = props.getProperty("remove-short-arcs", null);
		if(rsa != null)
			minimumArcLength = (rsa.length() > 0)? Double.parseDouble(rsa) : 0.0;
		else
			minimumArcLength = null;
		frigRoundabouts = props.getProperty("frig-roundabouts");
		ignoreTurnRestrictions = props.getProperty("ignore-turn-restrictions", false);
		if (props.getProperty("preserve-element-order", false)) {
			nodeMap = new LinkedHashMap<Long, Node>(5000);
			wayMap = new LinkedHashMap<Long, Way>(5000);
			relationMap = new LinkedHashMap<Long, Relation>();
		} else {
			nodeMap = new HashMap<Long, Node>(5000);
			wayMap = new HashMap<Long, Way>(5000);
			relationMap = new HashMap<Long, Relation>();
		}
	}

	/**
	 * Receive notification of the start of an element.
	 *
	 * @param uri		The Namespace URI, or the empty string if the
	 *                   element has no Namespace URI or if Namespace
	 *                   processing is not being performed.
	 * @param localName  The local name (without prefix), or the
	 *                   empty string if Namespace processing is not being
	 *                   performed.
	 * @param qName	  The qualified name (with prefix), or the
	 *                   empty string if qualified names are not available.
	 * @param attributes The attributes attached to the element.  If
	 *                   there are no attributes, it shall be an empty
	 *                   Attributes object.
	 * @throws SAXException Any SAX exception, possibly
	 *                                  wrapping another exception.
	 * @see ContentHandler#startElement
	 */
	public void startElement(String uri, String localName,
	                         String qName, Attributes attributes)
			throws SAXException
	{

		if (mode == 0) {
			if (qName.equals("node")) {
				mode = MODE_NODE;

				addNode(attributes.getValue("id"),
						attributes.getValue("lat"),
						attributes.getValue("lon"));

			} else if (qName.equals("way")) {
				mode = MODE_WAY;
				addWay(attributes.getValue("id"));
			} else if (qName.equals("relation")) {
				mode = MODE_RELATION;
				currentRelation = new GeneralRelation(idVal(attributes.getValue("id")));
			} else if (qName.equals("bound")) {
				mode = MODE_BOUND;
				if(!ignoreBounds) {
					String box = attributes.getValue("box");
					setupBBoxFromBound(box);
				}
			} else if (qName.equals("bounds")) {
				mode = MODE_BOUNDS;
				if(!ignoreBounds)
					setupBBoxFromBounds(attributes);
			}

		} else if (mode == MODE_NODE) {
			startInNode(qName, attributes);
		} else if (mode == MODE_WAY) {
			startInWay(qName, attributes);
		} else if (mode == MODE_RELATION) {
			startInRelation(qName, attributes);
		}
	}

	private void startInRelation(String qName, Attributes attributes) {
		if (qName.equals("member")) {
			long id = idVal(attributes.getValue("ref"));
			Element el;
			String type = attributes.getValue("type");
			if ("way".equals(type)){
				el = wayMap.get(id);
			} else if ("node".equals(type)) {
				el = nodeMap.get(id);
				if(el == null) {
					// we didn't make a node for this point earlier,
					// do it now (if it exists)
					Coord co = coordMap.get(id);
					if(co != null) {
						el = new Node(id, co);
						nodeMap.put(id, (Node)el);
					}
				}
			} else if ("relation".equals(type)) {
				el = relationMap.get(id);
			} else
				el = null;
			if (el != null) // ignore non existing ways caused by splitting files
				currentRelation.addElement(attributes.getValue("role"), el);
		} else if (qName.equals("tag")) {
			currentRelation.addTag(attributes.getValue("k"), attributes.getValue("v"));
		}
	}

	private void startInWay(String qName, Attributes attributes) {
		if (qName.equals("nd")) {
			long id = idVal(attributes.getValue("ref"));
			addNodeToWay(id);
		} else if (qName.equals("tag")) {
			String key = attributes.getValue("k");
			String val = attributes.getValue("v");
			currentWay.addTag(key, val);
		}
	}

	private void startInNode(String qName, Attributes attributes) {
		if (qName.equals("tag")) {
			String key = attributes.getValue("k");
			String val = attributes.getValue("v");

			// We only want to create a full node for nodes that are POI's
			// and not just point of a way.  Only create if it has tags that
			// are not in a list of ignorables ones such as 'created_by'
			if (currentNode != null || !key.equals("created_by")) {
				if (currentNode == null) {
					Coord co = coordMap.get(currentElementId);
					currentNode = new Node(currentElementId, co);
					nodeMap.put(currentElementId, currentNode);
				}

				if((val.equals("motorway_junction") ||
				    val.equals("services")) &&
				   key.equals("highway")) {
					exits.add(currentNode);
					currentNode.addTag("osm:id", "" + currentElementId);
				}

				currentNode.addTag(key, val);
			}
		}
	}

	/**
	 * Receive notification of the end of an element.
	 *
	 * @param uri	   The Namespace URI, or the empty string if the
	 *                  element has no Namespace URI or if Namespace
	 *                  processing is not being performed.
	 * @param localName The local name (without prefix), or the
	 *                  empty string if Namespace processing is not being
	 *                  performed.
	 * @param qName	 The qualified name (with prefix), or the
	 *                  empty string if qualified names are not available.
	 * @throws SAXException Any SAX exception, possibly
	 *                                  wrapping another exception.
	 * @see ContentHandler#endElement
	 */
	public void endElement(String uri, String localName, String qName)
					throws SAXException
	{
		if (mode == MODE_NODE) {
			if (qName.equals("node"))
				endNode();

		} else if (mode == MODE_WAY) {
			if (qName.equals("way")) {
				mode = 0;
				String highway = currentWay.getTag("highway");
				if(highway != null ||
				   "ferry".equals(currentWay.getTag("route"))) {
					// if the way is a roundabout but isn't already
					// flagged as "oneway", flag it here
					if("roundabout".equals(currentWay.getTag("junction"))) {
						if(currentWay.getTag("oneway") == null) {
							currentWay.addTag("oneway", "yes");
						}
						if(currentWay.getTag("mkgmap:frig_roundabout") == null) {
							if(frigRoundabouts != null)
								currentWay.addTag("mkgmap:frig_roundabout", frigRoundabouts);
						}
					}
					String cycleway = currentWay.getTag("cycleway");
					if(makeOppositeCycleways &&
					   cycleway != null &&
					   !"cycleway".equals(highway) &&
					   currentWay.isBoolTag("oneway") &&
					   ("opposite".equals(cycleway) ||
						"opposite_lane".equals(cycleway) ||
						"opposite_track".equals(cycleway))) {
						// what we have here is a oneway street
						// that allows bicycle traffic in both
						// directions -- to enable bicyle routing
						// in the reverse direction, we synthesise
						// a cycleway that has the same points as
						// the original way
						long cycleWayId = currentWay.getId() + CYCLEWAY_ID_OFFSET;
						Way cycleWay = new Way(cycleWayId);
						wayMap.put(cycleWayId, cycleWay);
						// this reverses the direction of the way but
						// that isn't really necessary as the cycleway
						// isn't tagged as oneway
						List<Coord> points = currentWay.getPoints();
						for(int i = points.size() - 1; i >= 0; --i)
							cycleWay.addPoint(points.get(i));
						cycleWay.copyTags(currentWay);
						//cycleWay.addTag("highway", "cycleway");
						String name = currentWay.getTag("name");
						if(name != null)
							name += " (cycleway)";
						else
							name = "cycleway";
						cycleWay.addTag("name", name);
						cycleWay.addTag("oneway", "no");
						cycleWay.addTag("access", "no");
						cycleWay.addTag("bicycle", "yes");
						cycleWay.addTag("foot", "no");
						log.info("Making " + cycleway + " cycleway '" + cycleWay.getTag("name") + "'");
					}
					else if(makeCycleways &&
							cycleway != null &&
							!"cycleway".equals(highway) &&
							("track".equals(cycleway) ||
							 "lane".equals(cycleway) ||
							 "both".equals(cycleway) ||
							 "left".equals(cycleway) ||
							 "right".equals(cycleway))) {
						// what we have here is a highway with a
						// separate track for cycles -- to enable
						// bicyle routing, we synthesise a cycleway
						// that has the same points as the original
						// way
						long cycleWayId = currentWay.getId() + CYCLEWAY_ID_OFFSET;
						Way cycleWay = new Way(cycleWayId);
						wayMap.put(cycleWayId, cycleWay);
						List<Coord> points = currentWay.getPoints();
						for (Coord point : points)
							cycleWay.addPoint(point);
						cycleWay.copyTags(currentWay);
						if(currentWay.getTag("bicycle") == null)
							currentWay.addTag("bicycle", "no");
						//cycleWay.addTag("highway", "cycleway");
						String name = currentWay.getTag("name");
						if(name != null)
							name += " (cycleway)";
						else
							name = "cycleway";
						cycleWay.addTag("name", name);
						cycleWay.addTag("access", "no");
						cycleWay.addTag("bicycle", "yes");
						cycleWay.addTag("foot", "no");
						log.info("Making " + cycleway + " cycleway '" + cycleWay.getTag("name") + "'");
					}
				}
				if("motorway".equals(highway) ||
				   "trunk".equals(highway))
					motorways.add(currentWay);
				currentWay = null;
				// ways are processed at the end of the document,
				// may be changed by a Relation class
			}
		} else if (mode == MODE_BOUND) {
			if (qName.equals("bound"))
				mode = 0;
		} else if (mode == MODE_BOUNDS) {
			if (qName.equals("bounds"))
				mode = 0;
		} else if (mode == MODE_RELATION) {
			if (qName.equals("relation")) {
				mode = 0;
				endRelation();
			}
		}
	}

	private void endNode() {
		mode = 0;

		currentElementId = 0;
		currentNode = null;
	}

	private void endRelation() {
		String type = currentRelation.getTag("type");
		if (type != null) {
			if ("multipolygon".equals(type))
				currentRelation = new MultiPolygonRelation(currentRelation, wayMap);
			else if("restriction".equals(type)) {

				if(ignoreTurnRestrictions)
					currentRelation = null;
				else
					currentRelation = new RestrictionRelation(currentRelation);
			}
		}
		if(currentRelation != null) {
			relationMap.put(currentRelation.getId(), currentRelation);
			currentRelation.processElements();
			currentRelation = null;
		}
	}

	/**
	 * Receive notification of the end of the document.
	 *
	 * We add the background polygon here.  As this is going to be big it
	 * may be split up further down the chain.
	 *
	 * @throws SAXException Any SAX exception, possibly wrapping
	 * another exception.
	 */
	public void endDocument() throws SAXException {

		for (Node e : exits) {
			String refTag = Exit.TAG_ROAD_REF;
			if(e.getTag(refTag) == null) {
				for (Way w : motorways) {
					String ref = w.getTag("ref");
					if (w.getPoints().contains(e.getLocation())) {
						if(ref != null) {
							log.info("Adding " + refTag + "=" + ref + " to exit" + e.getTag("ref"));
							e.addTag(refTag, ref);
						}
						else {
							log.warn("Motorway exit is positioned on a motorway that doesn't have a 'ref' tag");
						}
						break;
					}
				}
			}
		}

		coordMap = null;
		for (Relation r : relationMap.values())
			converter.convertRelation(r);

		for (Node n : nodeMap.values())
			converter.convertNode(n);

		nodeMap = null;

		if(minimumArcLength != null) {
			if(bbox != null)
				makeBoundaryNodes();
			removeShortArcsByMergingNodes(minimumArcLength);
		}

		nodeIdMap = null;

		for (Way w: wayMap.values())
			converter.convertWay(w);

		wayMap = null;

		RoadNetwork roadNetwork = mapper.getRoadNetwork();
		for (Relation r : relationMap.values()) {
			if(r instanceof RestrictionRelation) {
				((RestrictionRelation)r).addRestriction(roadNetwork);
			}
		}

		relationMap = null;

		if(bbox != null) {
			mapper.addToBounds(new Coord(bbox.getMinLat(),
						     bbox.getMinLong()));
			mapper.addToBounds(new Coord(bbox.getMinLat(),
						     bbox.getMaxLong()));
			mapper.addToBounds(new Coord(bbox.getMaxLat(),
						     bbox.getMinLong()));
			mapper.addToBounds(new Coord(bbox.getMaxLat(),
						     bbox.getMaxLong()));
		}

		// Run a finishing task.
		endTask.run();
	}

	// "soft clip" each way that crosses a boundary by adding a point
	//  at each place where it meets the boundary
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
				Coord[] clippedPair = LineClipper.clip(bbox, pair);
				// we're only interested in segments that touch the
				// boundary
				if(clippedPair != null) {
					// the segment touches the boundary or is
					// completely inside the bounding box
					if(clippedPair[1] != points.get(i)) {
						// the second point in the segment is outside
						// of the boundary
						assert clippedPair[1].getOnBoundary();
						// insert boundary point before the second point
						points.add(i, clippedPair[1]);
						// it's a newly created point so make its
						// highway count one
						clippedPair[1].incHighwayCount();
						++numBoundaryNodesAdded;

					}
					else if(clippedPair[1].getOnBoundary())
						++numBoundaryNodesDetected;

					if(clippedPair[1].getOnBoundary()) {
						// the point is on the boundary so make sure
						// it becomes a node
						clippedPair[1].incHighwayCount();
					}

					if(clippedPair[0] != points.get(i - 1)) {
						// the first point in the segment is outside
						// of the boundary
						assert clippedPair[0].getOnBoundary();
						// insert boundary point after the first point
						points.add(i, clippedPair[0]);
						// it's a newly created point so make its
						// highway count one
						clippedPair[0].incHighwayCount();
						++numBoundaryNodesAdded;
					}
					else if(clippedPair[0].getOnBoundary())
						++numBoundaryNodesDetected;

					if(clippedPair[0].getOnBoundary()) {
						// the point is on the boundary so make sure
						// it becomes a node
						clippedPair[0].incHighwayCount();
					}
				}
			}
		}
		log.info("Making boundary nodes - finished (" + numBoundaryNodesAdded + " added, " + numBoundaryNodesDetected + " detected)");
	}

	private void incArcCount(Map<Coord, Integer> map, Coord p, int inc) {
		Integer i = map.get(p);
		if(i != null)
			inc += i;
		map.put(p, inc);
	}

	private void removeShortArcsByMergingNodes(double minArcLength) {
		// keep track of how many arcs reach a given point
		Map<Coord, Integer> arcCounts = new IdentityHashMap<Coord, Integer>();
		log.info("Removing short arcs - counting arcs");
		for(Way w : wayMap.values()) {
			List<Coord> points = w.getPoints();
			int numPoints = points.size();
			if(numPoints >= 2) {
				// all end points have 1 arc
				incArcCount(arcCounts, points.get(0), 1);
				incArcCount(arcCounts, points.get(numPoints - 1), 1);
				// non-end points have 2 arcs but ignore points that
				// are only in a single way
				for(int i = numPoints - 2; i >= 1; --i) {
					Coord p = points.get(i);
					if(p.getHighwayCount() > 1)
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
		while(anotherPassRequired && pass < 10) {
			anotherPassRequired = false;
			log.info("Removing short arcs - PASS " + ++pass);
			Way[] ways = wayMap.values().toArray(new Way[wayMap.size()]);
			for (Way way : ways) {
				List<Coord> points = way.getPoints();
				if (points.size() < 2) {
					log.info("  Way " + way.getTag("name") + " (OSM id " + way.getId() + ") has less than 2 points - deleting it");
					wayMap.remove(way.getId());
					++numWaysDeleted;
					continue;
				}
				int previousNodeIndex = 0; // first point will be a node
				Coord previousPoint = points.get(0);
				double arcLength = 0;
				for(int i = 0; i < points.size(); ++i) {
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
						String replacementId = (replacement.getOnBoundary())? "'boundary node'" : "" + nodeIdMap.get(replacement);
						log.info("  Way " + way.getTag("name") + " (OSM id " + way.getId() + ") has node " + nodeIdMap.get(p) + " replaced with node " + replacementId);
						p = replacement;
						// replace point in way
						points.set(i, p);
						if (i == 0)
							previousPoint = p;
						anotherPassRequired = true;
					}
					if (i > 0) {
						// this is not the first point in the way
						if (p == previousPoint) {
							log.info("  Way " + way.getTag("name") + " (OSM id " + way.getId() + ") has consecutive identical points (" + nodeIdMap.get(p) + ") - deleting the second point");
							points.remove(i);
							// hack alert! rewind the loop index
							--i;
							anotherPassRequired = true;
							continue;
						}
						arcLength += p.distance(previousPoint);
						previousPoint = p;
						Coord previousNode = points.get(previousNodeIndex);
						// this point is a node if it has an arc count
						Integer arcCount = arcCounts.get(p);
						if (arcCount != null) {
							// merge this node to previous node if the
							// two points have identical coordinates
							// or are closer than the minimum distance
							// allowed but they are not the same point
							// object
							if(p != previousNode &&
							   (p.equals(previousNode) ||
								(minArcLength > 0 &&
								 minArcLength > arcLength))) {
								if(previousNode.getOnBoundary() && p.getOnBoundary()) {
									// both the previous node and this node
									// are on the boundary
									if(complainedAbout.get(way) == null) {
										log.warn("  Way " + way.getTag("name") + " (OSM id " + way.getId() + ") has short arc (" + String.format("%.2f", arcLength) + "m) - but it can't be removed because both ends of the arc are boundary nodes!");
										complainedAbout.put(way, way);
									}
									break; // give up with this way
								}
								String thisNodeId = (p.getOnBoundary())? "'boundary node'" : "" + nodeIdMap.get(p);
								String previousNodeId = (previousNode.getOnBoundary())? "'boundary node'" : "" + nodeIdMap.get(previousNode);

								if(p.equals(previousNode))
									log.info("  Way " + way.getTag("name") + " (OSM id " + way.getId() + ") has zero length arc - removing it by merging node " + thisNodeId + " into " + previousNodeId);
								else
									log.info("  Way " + way.getTag("name") + " (OSM id " + way.getId() + ") has short arc (" + String.format("%.2f", arcLength) + "m) - removing it by merging node " + thisNodeId + " into " + previousNodeId);
								if(p.getOnBoundary()) {
									// current point is a boundary node so
									// we need to merge the previous node into
									// this node
									++numNodesMerged;
									replacements.put(previousNode, p);
									// add the previous node's arc
									// count to this node
									incArcCount(arcCounts, p, arcCounts.get(previousNode) - 1);
									// remove the preceding point(s)
									// back to and including the
									// previous node
									for(int j = i - 1; j >= previousNodeIndex; --j) {
										points.remove(j);
									}
									// hack alert! rewind the loop index
									i = previousNodeIndex;
									anotherPassRequired = true;
								}
								else {
									// current point is not on a boundary so
									// merge this node into the previous one
									++numNodesMerged;
									replacements.put(p, previousNode);
									// add this node's arc count to the node
									// that is replacing it
									incArcCount(arcCounts, previousNode, arcCount - 1);
									// reset previous point to be the
									// previous node
									previousPoint = previousNode;
									// remove the point(s) back to the
									// previous node
									for(int j = i; j > previousNodeIndex; --j) {
										points.remove(j);
									}
									// hack alert! rewind the loop index
									i = previousNodeIndex;
									anotherPassRequired = true;
								}
							} else {
								// the node did not need to be merged so
								// it now becomes the new previous node
								previousNodeIndex = i;
							}

							// reset arc length
							arcLength = 0;
						}
					}
				}
			}
		}

		if(anotherPassRequired)
			log.error("Removing short arcs - didn't finish in " + pass + " passes, giving up!");
		else
			log.info("Removing short arcs - finished in " + pass + " passes (" + numNodesMerged + " nodes merged, " + numWaysDeleted + " ways deleted)");
	}

	private void setupBBoxFromBounds(Attributes xmlattr) {
		try {
			setBBox(Double.parseDouble(xmlattr.getValue("minlat")),
					Double.parseDouble(xmlattr.getValue("minlon")),
					Double.parseDouble(xmlattr.getValue("maxlat")),
					Double.parseDouble(xmlattr.getValue("maxlon")));
		} catch (NumberFormatException e) {
			// just ignore it
			log.warn("NumberformatException: Cannot read bbox");
		}
	}

	private void setupBBoxFromBound(String box) {
		String[] f = box.split(",");
		try {
			setBBox(Double.parseDouble(f[0]), Double.parseDouble(f[1]),
					Double.parseDouble(f[2]), Double.parseDouble(f[3]));
			log.debug("Map bbox: " + bbox);
		} catch (NumberFormatException e) {
			// just ignore it
			log.warn("NumberformatException: Cannot read bbox");
		}
	}

	private void setBBox(double minlat, double minlong,
	                     double maxlat, double maxlong) {

		bbox = new Area(minlat, minlong, maxlat, maxlong);
		converter.setBoundingBox(bbox);
	}

	/**
	 * Save node information.  Consists of a location specified by lat/long.
	 *
	 * @param sid The id as a string.
	 * @param slat The lat as a string.
	 * @param slon The longitude as a string.
	 */
	private void addNode(String sid, String slat, String slon) {
		if (sid == null || slat == null || slon == null)
			return;
		
		try {
			long id = idVal(sid);

			Coord co = new Coord(Double.parseDouble(slat), Double.parseDouble(slon));
			coordMap.put(id, co);
			currentElementId = id;
			if (bbox == null)
				mapper.addToBounds(co);
		} catch (NumberFormatException e) {
			// ignore bad numeric data.
		}
	}

	private void addWay(String sid) {
		try {
			long id = idVal(sid);
			currentWay = new Way(id);
			wayMap.put(id, currentWay);
		} catch (NumberFormatException e) {
			// ignore bad numeric data.
		}
	}

	private void addNodeToWay(long id) {
		Coord co = coordMap.get(id);
		//co.incCount();
		if (co != null) {
			if(linkPOIsToWays) {
				// if this Coord is also a POI, replace it with an
				// equivalent CoordPOI that contains a reference to
				// the POI's Node so we can access the POI's tags
				Node node = nodeMap.get(id);
				if(node != null) {
					if(!(co instanceof CoordPOI)) {
						co = new CoordPOI(co.getLatitude(), co.getLongitude(), node);
						coordMap.put(id, co);
						// even if this point isn't actually shared by
						// multiple ways, as it may well become a node
						// later, we want to consider it a node now so
						// that it will be processed by the short arc
						// removal code
						co.incHighwayCount();
					}
					// flag this Way as having a CoordPOI so it will
					// be processed later
					currentWay.addTag("mkgmap:way-has-pois", "true");
				}
			}
			currentWay.addPoint(co);
			co.incHighwayCount(); // nodes (way joins) will have highwayCount > 1
			if(minimumArcLength != null)
				nodeIdMap.put(co, id);
		}
	}

	public void setConverter(OsmConverter converter) {
		this.converter = converter;
	}

	public void setMapper(MapDetails mapper) {
		this.mapper = mapper;
	}

	public void setEndTask(Runnable endTask) {
		this.endTask = endTask;
	}

	public void fatalError(SAXParseException e) throws SAXException {
		System.err.println("Error at line " + e.getLineNumber() + ", col "
				+ e.getColumnNumber());
		super.fatalError(e);
	}

	private long idVal(String id) {
		try {
			// attempt to parse id as a number
			return Long.parseLong(id);
		}
		catch (NumberFormatException e) {
			// if that fails, fake a (hopefully) unique value
			Long fakeIdVal = fakeIdMap.get(id);
			if(fakeIdVal == null) {
				fakeIdVal = (1L << 62) + nextFakeId++;
				fakeIdMap.put(id, fakeIdVal);
			}
			//System.out.printf("%s = 0x%016x\n", id, fakeIdVal);
			return fakeIdVal;
		}
	}
}

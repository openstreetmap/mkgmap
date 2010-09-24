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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Exit;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Operations mostly on highways that have to be performed during reading
 * the OSM input file.
 *
 * Some of this would be much better done in a style file or by extending the style system.
 */
public class HighwayHooks extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(HighwayHooks.class);

	private static final long CYCLEWAY_ID_OFFSET = 0x10000000;

	private final List<Way> motorways = new ArrayList<Way>();
	private final List<Node> exits = new ArrayList<Node>();

	private boolean makeOppositeCycleways;
	private boolean makeCycleways;
	private String frigRoundabouts;
	private ElementSaver saver;
	private boolean linkPOIsToWays;

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		if(props.getProperty("make-all-cycleways", false)) {
			makeOppositeCycleways = makeCycleways = true;
		}
		else {
			makeOppositeCycleways = props.getProperty("make-opposite-cycleways", false);
			makeCycleways = props.getProperty("make-cycleways", false);
		}
		frigRoundabouts = props.getProperty("frig-roundabouts");
		linkPOIsToWays = props.getProperty("link-pois-to-ways", false);

		return true;
	}

	public void onAddNode(Node node) {
		String val = node.getTag("highway");
		if (val != null && (val.equals("motorway_junction") || val.equals("services"))) {
			exits.add(node);
			node.addTag("osm:id", String.valueOf(node.getId()));
		}
	}

	public void onCoordAddedToWay(Way way, long id, Coord co) {
		Node currentNodeInWay = saver.getNode(id);

		if (linkPOIsToWays) {
			// if this Coord is also a POI, replace it with an
			// equivalent CoordPOI that contains a reference to
			// the POI's Node so we can access the POI's tags
			if (!(co instanceof CoordPOI) && currentNodeInWay != null) {
				// for now, only do this for nodes that have
				// certain tags otherwise we will end up creating
				// a CoordPOI for every node in the way
				final String[] coordPOITags = { "access", "barrier", "highway" };
				for (String cpt : coordPOITags) {
					if (currentNodeInWay.getTag(cpt) != null) {
						// the POI has one of the approved tags so
						// replace the Coord with a CoordPOI
						CoordPOI cp = new CoordPOI(co.getLatitude(), co.getLongitude());
						saver.addPoint(id, cp);

						// we also have to jump through hoops to
						// make a new version of Node because we
						// can't replace the Coord that defines
						// its location
						Node newNode = new Node(id, cp);
						newNode.copyTags(currentNodeInWay);
						saver.addNode(newNode);
						// tell the CoordPOI what node it's
						// associated with
						cp.setNode(newNode);
						co = cp;
						// if original node is in exits, replace it
						if (exits.remove(currentNodeInWay))
							exits.add(newNode);
						currentNodeInWay = newNode;
						break;
					}
				}
			}

			if (co instanceof CoordPOI) {
				// flag this Way as having a CoordPOI so it
				// will be processed later
				way.addTag("mkgmap:way-has-pois", "true");
				log.info("Linking POI " + currentNodeInWay.toBrowseURL() + " to way at " + co.toOSMURL());
			}
		}

		// See if the first Node of the Way has a FIXME attribute
		if (way.getPoints().isEmpty()) {
			boolean currentWayStartsWithFIXME = (currentNodeInWay != null &&
										 (currentNodeInWay.getTag("FIXME") != null ||
										  currentNodeInWay.getTag("fixme") != null));
		}
	}

	public void onAddWay(Way way) {
		String highway = way.getTag("highway");
		if (highway != null || "ferry".equals(way.getTag("route"))) {
			boolean oneway = way.isBoolTag("oneway");
			// if the first or last Node of the Way has a
			// FIXME attribute, disable dead-end-check for
			// oneways
			//if (oneway && currentWayStartsWithFIXME ||
			//		(currentNodeInWay != null && (currentNodeInWay.getTag("FIXME") != null || currentNodeInWay.getTag("fixme") != null))) {
			//	way.addTag("mkgmap:dead-end-check", "false");
			//}

			// if the way is a roundabout but isn't already
			// flagged as "oneway", flag it here
			if ("roundabout".equals(way.getTag("junction"))) {
				if (way.getTag("oneway") == null) {
					way.addTag("oneway", "yes");
				}

				if (way.getTag("mkgmap:frig_roundabout") == null) {
					if(frigRoundabouts != null)
						way.addTag("mkgmap:frig_roundabout", frigRoundabouts);
				}
			}

			String cycleway = way.getTag("cycleway");
			if (makeOppositeCycleways && cycleway != null && !"cycleway".equals(highway) && oneway &&
			   ("opposite".equals(cycleway) ||
				"opposite_lane".equals(cycleway) ||
				"opposite_track".equals(cycleway)))
			{
				// what we have here is a oneway street
				// that allows bicycle traffic in both
				// directions -- to enable bicycle routing
				// in the reverse direction, we synthesise
				// a cycleway that has the same points as
				// the original way
				Way cycleWay = makeCycleWay(way);
				cycleWay.addTag("oneway", "no");

			} else if (makeCycleways && cycleway != null && !"cycleway".equals(highway) &&
					("track".equals(cycleway) ||
					 "lane".equals(cycleway) ||
					 "both".equals(cycleway) ||
					 "left".equals(cycleway) ||
					 "right".equals(cycleway)))
			{
				// what we have here is a highway with a
				// separate track for cycles -- to enable
				// bicycle routing, we synthesise a cycleway
				// that has the same points as the original
				// way
				makeCycleWay(way);
				if (way.getTag("bicycle") == null)
					way.addTag("bicycle", "no");
			}
		}

		if("motorway".equals(highway) || "trunk".equals(highway))
			motorways.add(way);
	}

	/**
	 * Construct a cycleway that has the same points as an existing way.  Used for separate
	 * cycle lanes.
	 * @param way The original way.
	 * @return The new way, which will have the same points and have suitable cycle tags.
	 */
	private Way makeCycleWay(Way way) {
		long cycleWayId = way.getId() + CYCLEWAY_ID_OFFSET;
		Way cycleWay = new Way(cycleWayId);
		saver.addWay(cycleWay);

		// this reverses the direction of the way but
		// that isn't really necessary as the cycleway
		// isn't tagged as oneway
		List<Coord> points = way.getPoints();
		//for (int i = points.size() - 1; i >= 0; --i)
		//	cycleWay.addPoint(points.get(i));
		for (Coord point : points)
			cycleWay.addPoint(point);
		
		cycleWay.copyTags(way);

		String name = way.getTag("name");
		if(name != null)
			name += " (cycleway)";
		else
			name = "cycleway";
		cycleWay.addTag("name", name);
		cycleWay.addTag("access", "no");
		cycleWay.addTag("bicycle", "yes");
		cycleWay.addTag("foot", "no");
		cycleWay.addTag("mkgmap:synthesised", "yes");

		return cycleWay;
	}

	public void end() {
		finishExits();
	}

	private void finishExits() {
		for (Node e : exits) {
			String refTag = Exit.TAG_ROAD_REF;
			if (e.getTag(refTag) == null) {
				String exitName = e.getTag("name");
				if (exitName == null)
					exitName = e.getTag("ref");

				String ref = null;
				Way motorway = null;
				for (Way w : motorways) {
					if (w.getPoints().contains(e.getLocation())) {
						motorway = w;
						ref = w.getTag("ref");
						if(ref != null)
						    break;
					}
				}
				
				if (ref != null) {
					log.info("Adding " + refTag + "=" + ref + " to exit " + exitName);
					e.addTag(refTag, ref);
				} else if(motorway != null) {
					log.warn("Motorway exit " + exitName + " is positioned on a motorway that doesn't have a 'ref' tag (" + e.getLocation().toOSMURL() + ")");
				}
			}
		}
	}
}

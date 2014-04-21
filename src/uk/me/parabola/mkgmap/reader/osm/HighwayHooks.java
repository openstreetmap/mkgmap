/*
 * Copyright (C) 2010 - 2012.
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	private final List<Way> motorways = new ArrayList<>();
	private final List<Node> exits = new ArrayList<>();

	private boolean makeOppositeCycleways;
	private ElementSaver saver;
	private boolean linkPOIsToWays;

	private Node currentNodeInWay;

	
	private final static Set<String> usedTags = new HashSet<String>() {
		{
			add("highway");
			add("access");
			add("barrier");
		    add("FIXME");
		    add("fixme");
		    add("route");
		    add("oneway");
		    add("junction");
		    add("name");
		    add(Exit.TAG_ROAD_REF);
		    add("ref");
		    
// the following two tags are only added if the cycleway options are set 
//		    add("cycleway");
//		    add("bicycle");
		}
	};
	
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		if(props.getProperty("make-all-cycleways", false)) {
			log.error("option make-all-cycleways is deprecated, please use make-opposite-cycleways");
			makeOppositeCycleways = true;
		}
		else {
			makeOppositeCycleways = props.getProperty("make-opposite-cycleways", false);
		}
		
		linkPOIsToWays = props.getProperty("link-pois-to-ways", false);
		currentNodeInWay = null;

		if (makeOppositeCycleways) {
			// need the additional tags 
			usedTags.add("cycleway");
			usedTags.add("bicycle");
			usedTags.add("oneway:bicycle");
			usedTags.add("bicycle:oneway");
			usedTags.add("cycleway:left");
			usedTags.add("cycleway:right");
		}
		
		// add addr:street and addr:housenumber if housenumber search is enabled
		if (props.getProperty("housenumbers", false)) {
			usedTags.add("addr:street");
			usedTags.add("addr:housenumber");
		}
	
		return true;
	}

	
	public Set<String> getUsedTags() {
		return usedTags;
	}
	
	public void onAddNode(Node node) {
		String val = node.getTag("highway");
		if (val != null && (val.equals("motorway_junction") || val.equals("services"))) {
			exits.add(node);
			node.addTag("mkgmap:osmid", String.valueOf(node.getId()));
		}
	}

	public void onCoordAddedToWay(Way way, long id, Coord co) {
		currentNodeInWay = saver.getNode(id);

		if (linkPOIsToWays) {
			// if this Coord is also a POI, replace it with an
			// equivalent CoordPOI that contains a reference to
			// the POI's Node so we can access the POI's tags
			if (!(co instanceof CoordPOI) && currentNodeInWay != null) {
				// for now, only do this for nodes that have
				// certain tags otherwise we will end up creating
				// a CoordPOI for every node in the way
				final String[] coordPOITags = { "barrier", "highway" };
				for (String cpt : coordPOITags) {
					if (currentNodeInWay.getTag(cpt) != null) {
						// the POI has one of the approved tags so
						// replace the Coord with a CoordPOI
						CoordPOI cp = new CoordPOI(co);
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
				if (log.isInfoEnabled())
					log.info("Linking POI", currentNodeInWay.toBrowseURL(), "to way at", co.toOSMURL());
			}
		}
	}

	public void onAddWay(Way way) {
		String highway = way.getTag("highway");
		if (highway != null || "ferry".equals(way.getTag("route"))) {
			// if the way is a roundabout but isn't already
			// flagged as "oneway", flag it here
			if ("roundabout".equals(way.getTag("junction"))) {
				if (way.getTag("oneway") == null) {
					way.addTag("oneway", "yes");
				}
			}

			if (makeOppositeCycleways && !"cycleway".equals(highway)){
				String onewayTag = way.getTag("oneway");
				boolean oneway = way.tagIsLikeYes("oneway");
				if (!oneway & onewayTag != null && ("-1".equals(onewayTag) || "reverse".equals(onewayTag)))
					oneway = true;
				if (oneway){
					String cycleway = way.getTag("cycleway");
					boolean addCycleWay = false;
					// we have a oneway street, check if it allows bicycles to travel in opposite direction
					if ("no".equals(way.getTag("oneway:bicycle")) || "no".equals(way.getTag("bicycle:oneway"))){
						addCycleWay = true;
					}
					else if (cycleway != null && ("opposite".equals(cycleway) || "opposite_lane".equals(cycleway) || "opposite_track".equals(cycleway))){
						addCycleWay = true;	
					}
					else if ("opposite_lane".equals(way.getTag("cycleway:left")) || "opposite_lane".equals(way.getTag("cycleway:right"))){
						addCycleWay = true;
					}
					else if ("opposite_track".equals(way.getTag("cycleway:left")) || "opposite_track".equals(way.getTag("cycleway:right"))){
						addCycleWay = true;
					}
					if (addCycleWay)
						way.addTag("mkgmap:make-cycle-way", "yes");
				} 
			}
		}

		if("motorway".equals(highway) || "trunk".equals(highway))
			motorways.add(way);
	}

	public void end() {
		finishExits();
		exits.clear();
		motorways.clear();
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
					// uses an implicit call of Coord.equals()
					if (w.getPoints().contains(e.getLocation())) {
						motorway = w;
						ref = w.getTag("ref");
						if(ref != null)
						    break;
					}
				}
				
				if (ref != null) {
					log.info("Adding", refTag + "=" + ref, "to exit", exitName);
					e.addTag(refTag, ref);
				} else if(motorway != null) {
					log.warn("Motorway exit", exitName, "is positioned on a motorway that doesn't have a 'ref' tag (" + e.getLocation().toOSMURL() + ")");
				}
			}
		}
	}
}

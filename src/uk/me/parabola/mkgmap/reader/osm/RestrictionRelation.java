/*
 * Copyright (C) 2013.
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
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.RouteRestriction;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;

/**
 * Representation of an OSM turn restriction
 *
 * @author Mark Burton
 */
public class RestrictionRelation extends Relation {

    private static final Logger log = Logger.getLogger(RestrictionRelation.class);

    private Way fromWay;
    private Way toWay;
    private Way viaWay;
    private Coord viaCoord;
    private final String restriction;

    private CoordNode fromNode;
    private CoordNode toNode;
    private CoordNode viaNode;
    private final List<CoordNode> otherNodes = new ArrayList<CoordNode>();
	private byte exceptMask;
    private String messagePrefix;

	/**
	 * Create an instance based on an existing relation.  We need to do
	 * this because the type of the relation is not known until after all
	 * its tags are read in.
	 * @param other The relation to base this one on.
	 */
	public RestrictionRelation(Relation other) {

		setId(other.getId());

		final String browseURL = toBrowseURL();

		messagePrefix = "Turn restriction " + browseURL + " ";

		for (Map.Entry<String, Element> pair : other.getElements()) {
			String role = pair.getKey();
			Element el = pair.getValue();
			addElement(role, el);

			Coord location = null;

			if(viaCoord != null)
				location = viaCoord;
			else if(fromWay != null && !fromWay.getPoints().isEmpty())
				location = fromWay.getPoints().get(0);
			else if(toWay != null && !toWay.getPoints().isEmpty())
				location = toWay.getPoints().get(0);

			if(location != null)
				messagePrefix = "Turn restriction " + browseURL + " (at " + location.toOSMURL() + ") ";

			if("to".equals(role)) {
				if(toWay != null) {
					log.warn(messagePrefix + "has extra 'to' member " + el.toBrowseURL());
				}
				else if(!(el instanceof Way)) {
					log.warn(messagePrefix + "'to' member " + el.toBrowseURL() + " is not a way but it should be");
				}
				else if(((Way)el).getPoints().isEmpty()) {
					log.warn(messagePrefix + "ignoring empty 'to' way " + el.toBrowseURL());
				}
				else
					toWay = (Way)el;
			}
			else if("from".equals(role)) {
				if(fromWay != null) {
					log.warn(messagePrefix + "has extra 'from' member " + el.toBrowseURL());
				}
				else if(!(el instanceof Way)) {
					log.warn(messagePrefix + "'from' member " + el.toBrowseURL() + " is not a way but it should be");
				}
				else if(((Way)el).getPoints().isEmpty()) {
					log.warn(messagePrefix + "ignoring empty 'from' way " + el.toBrowseURL());
				}
				else
					fromWay = (Way)el;
			}
			else if("via".equals(role)) {
				if(viaCoord != null || viaWay != null) {
					log.warn(messagePrefix + "has extra 'via' member " + el.toBrowseURL());
				}
				else if(el instanceof Node) {
					viaCoord = ((Node)el).getLocation();
				}
				else if(el instanceof Way) {
					viaWay = (Way)el;
				}
				else {
					log.warn(messagePrefix + "'via' member " + el.toBrowseURL() + " is not a node or way");
				}
			}
			else if("location_hint".equals(role)) {
				// relax - we don't care about this
			}
			else {
				log.warn(messagePrefix + "unknown member role '" + role + "'");
			}
		}

		copyTags(other);

		restriction = getTag("restriction");

		// These tags are not loaded by default but if they exist issue a warning
		String[] unsupportedTags = {
		    "day_on",
		    "day_off",
		    "hour_on",
		    "hour_off" };
		for (String unsupportedTag : unsupportedTags) {
			if (getTag(unsupportedTag) != null) {
				log.warn(messagePrefix + "ignoring unsupported '" + unsupportedTag + "' tag");
			}
		}

		exceptMask = RouteRestriction.EXCEPT_FOOT | RouteRestriction.EXCEPT_EMERGENCY; 
		String except = getTag("except");
		if(except != null) {
			for(String e : except.split("[,;]")) { // be nice
				e = e.trim();
				if(e.equals("motorcar") || e.equals("motorcycle"))
					exceptMask |= RouteRestriction.EXCEPT_CAR;
				else if(e.equals("psv") || e.equals("bus"))
					exceptMask |= RouteRestriction.EXCEPT_BUS;
				else if(e.equals("taxi"))
					exceptMask |= RouteRestriction.EXCEPT_TAXI;
				else if(e.equals("delivery") || e.equals("goods"))
					exceptMask |= RouteRestriction.EXCEPT_DELIVERY;
				else if(e.equals("bicycle"))
					exceptMask |= RouteRestriction.EXCEPT_BICYCLE;
				else if(e.equals("hgv") || e.equals("truck"))
					exceptMask |= RouteRestriction.EXCEPT_TRUCK;
				else
					log.warn(messagePrefix + "ignoring unsupported vehicle class '" + e + "' in turn restriction exception");
			}
		}
	}

	public Way getFromWay() {
		return fromWay;
	}

	public void setFromWay(Way fromWay) {
		this.fromWay = fromWay;
	}

	public Way getToWay() {
		return toWay;
	}

	public void setToWay(Way toWay) {
		this.toWay = toWay;
	}

	public Coord getViaCoord() {
		return viaCoord;
	}

	public void setFromNode(CoordNode fromNode) {
		this.fromNode = fromNode;
		log.debug(messagePrefix + restriction + " 'from' node is " + fromNode.toOSMURL());
	}

	public void setToNode(CoordNode toNode) {
		this.toNode = toNode;
		log.debug(messagePrefix + restriction + " 'to' node is " + toNode.toOSMURL());
	}

	public void setViaNode(CoordNode viaNode) {
		if(this.viaNode == null)
			log.debug(messagePrefix + restriction + " 'via' node is " + viaNode.toOSMURL());
		else if(!this.viaNode.equals(viaNode))
			log.warn(messagePrefix + restriction + " 'via' node redefined from " +
					 this.viaNode.toOSMURL() + " to " + viaNode.toOSMURL());
		this.viaNode = viaNode;
	}

	public void setViaCoord(Coord viaCoord) {
		log.warn(messagePrefix + restriction + " 'via' coord redefined from " +
				 this.viaCoord.toOSMURL() + " to " + viaCoord.toOSMURL());
		this.viaCoord = viaCoord;
	}

	public void addOtherNode(CoordNode otherNode) {
		otherNodes.add(otherNode);
		log.debug(messagePrefix + restriction + " adding 'other' node " + otherNode.toOSMURL());
	}

	public boolean isValid() {
		boolean result = true;

		if(restriction == null) {
			log.warn(messagePrefix + "lacks 'restriction' tag (e.g. no_left_turn)");
			result = false;
		}

		if(fromWay == null) {
			log.warn(messagePrefix + "lacks 'from' way");
		}

		if(toWay == null) {
			log.warn(messagePrefix + "lacks 'to' way");
		}

		if(fromWay == null || toWay == null)
			return false;

		if(viaCoord == null && viaWay == null) {
			List<Coord>fromPoints = fromWay.getPoints();
			List<Coord>toPoints = toWay.getPoints();
			for(Coord fp : fromPoints) {
				for(Coord tp : toPoints) {
					if(fp == tp) {
						if(viaCoord == null) {
							viaCoord = fp;
						}
						else {
							log.warn(messagePrefix + "lacks 'via' node and the 'from' (" + fromWay.toBrowseURL() + ") and 'to' (" + toWay.toBrowseURL() + ") ways connect in more than one place");
							return false;
						}
					}
				}
			}

			if(viaCoord == null) {
				log.warn(messagePrefix + "lacks 'via' node and the 'from' (" + fromWay.toBrowseURL() + ") and 'to' (" + toWay.toBrowseURL() + ") ways don't connect");
				return false;
			}

			log.warn(messagePrefix + "lacks 'via' node (guessing it should be at " + viaCoord.toOSMURL() + ", why don't you add it to the OSM data?)");
		}

		Coord v1 = viaCoord;
		Coord v2 = null;

		if(viaWay != null) {
			v1 = viaWay.getPoints().get(0);
			v2 = viaWay.getPoints().get(viaWay.getPoints().size() - 1);
		}

		Coord e1 = fromWay.getPoints().get(0);
		Coord e2 = fromWay.getPoints().get(fromWay.getPoints().size() - 1);
		if(e1 != v1 && e2 != v1 && e1 != v2 && e2 != v2) {
			log.warn(messagePrefix + "'from' way " + fromWay.toBrowseURL() + " doesn't start or end at 'via' node or way");
			result = false;
		}

		e1 = toWay.getPoints().get(0);
		e2 = toWay.getPoints().get(toWay.getPoints().size() - 1);
		if(e1 != v1 && e2 != v1 && e1 != v2 && e2 != v2) {
			log.warn(messagePrefix + "'to' way " + toWay.toBrowseURL() + " doesn't start or end at 'via' node or way");
			result = false;
		}

		if (result && viaWay != null) {
			log.warn(messagePrefix + "sorry, 'via' ways are not supported - ignoring restriction");
			result = false;
		}
		return result;
	}

	public void addRestriction(MapCollector collector) {
		if ("no_u_turn".equals(restriction)){
			if (toNode != null && fromNode == null)
				fromNode = toNode;
			else if (fromNode != null && toNode == null)
				toNode = fromNode;
		}
		if(restriction == null || viaNode == null || fromNode == null || toNode == null) {
			if (viaCoord != null)
				log.warn("can't add restriction relation", this.getId(), "type", restriction);
			return;
		}

		if(restriction.equals("no_left_turn") ||
		   restriction.equals("no_right_turn") ||
		   restriction.equals("no_straight_on") ||
		   restriction.equals("no_u_turn") ||
		   restriction.startsWith("no_turn")) {
			collector.addRestriction(fromNode, toNode, viaNode, exceptMask);
			if(restriction.startsWith("no_turn"))
				log.warn(messagePrefix + "has bad type '" + restriction + "' it should be of the form no_X_turn rather than no_turn_X - I added the restriction anyway - blocks routing to way " + toWay.toBrowseURL());
			else
				log.info(messagePrefix + restriction + " added - blocks routing to way " + toWay.toBrowseURL());
		}
		else if(restriction.equals("only_left_turn") ||
				restriction.equals("only_right_turn") ||
				restriction.startsWith("only_straight") ||
				restriction.startsWith("only_turn")) {
			if(restriction.startsWith("only_turn"))
				log.warn(messagePrefix + "has bad type '" + restriction + "' it should be of the form only_X_turn rather than only_turn_X - I added the restriction anyway - allows routing to way " + toWay.toBrowseURL());
			log.info(messagePrefix + restriction + " added - allows routing to way " + toWay.toBrowseURL());
			HashSet<CoordNode> otherNodesUnique = new HashSet<CoordNode>(otherNodes);
			for(CoordNode otherNode : otherNodesUnique) {
				if (otherNode.getId() != fromNode.getId() && otherNode.getId() != toNode.getId()) {
					log.info(messagePrefix + restriction + "  blocks routing to node " + otherNode.toOSMURL());
					collector.addRestriction(fromNode, otherNode, viaNode, exceptMask);
				} 
			}
		}
		else {
			log.warn(messagePrefix + "has unsupported type '" + restriction + "'");
		}
	}

	/** Process the members in this relation.
	 */
	public void processElements() {
		// relax
	}

	public String toString() {
		return "[restriction = " + restriction + ", from = " + fromWay.toBrowseURL() + ", to = " + toWay.toBrowseURL() + ", via = " + viaCoord.toOSMURL() + "]";
	}
	
	public boolean isOnlyXXXRestriction(){
		return restriction.startsWith("only");
	}
}

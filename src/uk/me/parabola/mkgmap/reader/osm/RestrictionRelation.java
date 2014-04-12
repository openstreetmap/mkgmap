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
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.GeneralRouteRestriction;
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

    private List<Way> fromWays = new ArrayList<>();
    private List<Way> toWays = new ArrayList<>();
    private List<Way> viaWays = new ArrayList<>();
    private List<Coord> viaPoints = new ArrayList<>();
    private Coord viaCoord;
    private String restriction;
	private byte exceptMask;
	
    private String messagePrefix;
    private boolean valid;
    private boolean evalWasCalled;

	/**
	 * Create an instance based on an existing relation.  We need to do
	 * this because the type of the relation is not known until after all
	 * its tags are read in.
	 * @param other The relation to base this one on.
	 */
	public RestrictionRelation(Relation other) {
		setId(other.getId());
		messagePrefix = "Turn restriction " + toBrowseURL();
		copyTags(other);
		for (Map.Entry<String, Element> pair : other.getElements()) {
			addElement(pair.getKey(), pair.getValue());
			
		}
	}
	
	/**
	 * 
	 */
	public void eval(){
		evalWasCalled = true;
		final String browseURL = toBrowseURL();
		fromWays.clear();
		viaWays.clear();
		toWays.clear();
		valid = true;
		// find out what kind of restriction we have and to which vehicles it applies
		exceptMask = RouteRestriction.EXCEPT_FOOT | RouteRestriction.EXCEPT_EMERGENCY;
		String specifc_type = getTag("restriction");
		int count_unknown = 0;
		Map<String, String> vehicles = getTagsWithPrefix("restriction:", true);
		if (vehicles.isEmpty() == false){
			exceptMask = (byte) 0xff;
			Iterator<Entry<String, String>> iter = vehicles.entrySet().iterator();
			while (iter.hasNext()){
				Map.Entry<String, String> entry = iter.next();
				String vehicle = entry.getKey();
				if (setExceptMask(vehicle, false) == false)
					count_unknown++;
				if (specifc_type == null)
					specifc_type = entry.getValue();
				else if (specifc_type.equals(entry.getValue()) == false){
					log.warn(messagePrefix, "is invalid, it specifies different kinds of turns");
					valid = false;
					break;
				}
			}
			if (valid && vehicles.size() == count_unknown){
				log.warn(messagePrefix, "no supported vehicle in turn restriction");				
				valid = false;
			}
		}
		restriction = specifc_type.trim();
		messagePrefix = "Turn restriction (" + restriction + ") " + browseURL;
		if(restriction.equals("no_left_turn") ||
				   restriction.equals("no_right_turn") ||
				   restriction.equals("no_straight_on") ||
				   restriction.equals("no_u_turn") ||
				   restriction.startsWith("no_turn") ||
				   restriction.equals("no_entry") ||
				   restriction.equals("no_exit") ) {
			if(restriction.startsWith("no_turn"))
				log.warn(messagePrefix, "has bad type '" + restriction + "' it should be of the form no_X_turn rather than no_turn_X");

		}  else if(restriction.equals("only_left_turn") ||
				restriction.equals("only_right_turn") ||
				restriction.startsWith("only_straight") ||
				restriction.startsWith("only_turn")) {
			if(restriction.startsWith("only_turn"))
				log.warn(messagePrefix, "has bad type '" + restriction + "' it should be of the form only_X_turn rather than only_turn_X");
		} else {
			log.warn(messagePrefix, "ignoring unsupported restriction type '" + restriction + "'");
			valid = false;
			return;
		}
		String type = getTag("type");
		if (type.startsWith("restriction:")){
			exceptMask = (byte) 0xff;
			String vehicle = type.substring("restriction:".length());
			if (setExceptMask(vehicle, false) == false) {
				log.warn(messagePrefix, "ignoring unsupported '" + vehicle + "' in turn restriction");
				valid = false;
				return;
			}
		}
		String except = getTag("except");
		if(except != null) {
			for(String vehicle : except.split("[,;]")) { // be nice
				vehicle = vehicle.trim();
				setExceptMask(vehicle, true);
			}
		}
		
		// These tags are not loaded by default but if they exist issue a warning
		String[] unsupportedTags = {
		    "day_on",
		    "day_off",
		    "hour_on",
		    "hour_off" };
		for (String unsupportedTag : unsupportedTags) {
			if (getTag(unsupportedTag) != null) {
				log.warn(messagePrefix, "ignoring unsupported '" + unsupportedTag + "' tag");
			}
		}
		
		// evaluate members
		for (Map.Entry<String, Element> pair : getElements()) {
			String role = pair.getKey();
			Element el = pair.getValue();

			Coord location = null;

			if(viaCoord != null)
				location = viaCoord;
			else if(!fromWays.isEmpty() && !fromWays.get(0).getPoints().isEmpty())
				location = fromWays.get(0).getPoints().get(0);
			else if(!toWays.isEmpty() && !toWays.get(0).getPoints().isEmpty())
				location = toWays.get(0).getPoints().get(0);
			else if(!viaWays.isEmpty() && !viaWays.get(0).getPoints().isEmpty())
				location = viaWays.get(0).getPoints().get(0);

			if(location != null)
				messagePrefix = "Turn restriction (" + restriction + ") " + browseURL + " (at " + location.toOSMURL() + ")";

			if("to".equals(role)) {
				if(!(el instanceof Way)) {
					log.warn(messagePrefix, "'to' member", el.toBrowseURL(), "is not a way but it should be");
				}
				else if(((Way)el).getPoints().isEmpty()) {
					log.warn(messagePrefix, "ignoring empty 'to' way", el.toBrowseURL());
				}
				else
					toWays.add((Way)el);
			}
			else if("from".equals(role)) {
				if(!(el instanceof Way)) {
					log.warn(messagePrefix, "'from' member", el.toBrowseURL(), "is not a way but it should be");
				}
				else if(((Way)el).getPoints().isEmpty()) {
					log.warn(messagePrefix, "ignoring empty 'from' way", el.toBrowseURL());
				}
				else
					fromWays.add((Way)el);
			}
			else if("via".equals(role)) {
				if(el instanceof Node) {
					if (viaCoord != null){
						log.warn(messagePrefix, "has extra 'via' node", el.toBrowseURL());
						valid = false;						
					} else
						viaCoord = ((Node)el).getLocation();
				}
				else if(el instanceof Way) {
					if (viaCoord != null){
						log.warn(messagePrefix, "has extra 'via' way", el.toBrowseURL());
						valid = false;						
					} else
						viaWays.add((Way)el);
				}
				else {
					log.warn(messagePrefix, "'via' member", el.toBrowseURL(), "is not a node or way");
				}
			}
			else if("location_hint".equals(role)) {
				// relax - we don't care about this
			}
			else {
				log.warn(messagePrefix, "unknown member role '" + role + "'");
			}
		}

		
		if (!valid)
			return;
		
		if ("no_entry".equals(restriction) == false){
			if (fromWays.size() > 1){
				log.warn(messagePrefix, "multiple 'from' members are only accepted for no_entry restrictions");
				valid = false;
				return;
			}
		}
		if ("no_exit".equals(restriction) == false){
			if (toWays.size() > 1){
				log.warn(messagePrefix, "multiple 'to' members are only accepted for no_exit restrictions");
				valid = false;
				return;
			}
		} 
		if (viaWays.isEmpty() && viaCoord == null && fromWays.size() == 1 && toWays.size() == 1){
			Way fromWay = fromWays.get(0);
			Way toWay = toWays.get(0);
			List<Coord>fromPoints = fromWay.getPoints();
			List<Coord>toPoints = toWay.getPoints();
			int countSame = 0;
			for(Coord fp : fromPoints) {
				for(Coord tp : toPoints) {
					if(fp == tp){
						countSame++;
						viaCoord = fp;
					}
				}
			}
			if (countSame > 1){
				log.warn(messagePrefix, "lacks 'via' node and way and the 'from' (", fromWay.toBrowseURL(), ") and 'to' (", toWay.toBrowseURL(), ") ways connect in more than one place");
				valid = false;
			} else if (viaCoord == null){
				log.warn(messagePrefix, "lacks 'via' node and the 'from' (" + fromWay.toBrowseURL() + ") and 'to' (" + toWay.toBrowseURL() + ") ways don't connect");
				valid = false;
			} else {
				log.warn(messagePrefix, "lacks 'via' node (guessing it should be at", viaCoord.toOSMURL() + ", why don't you add it to the OSM data?)");				
			}
		}
		if(restriction == null) {
			log.warn(messagePrefix, "lacks 'restriction' tag (e.g. no_left_turn)");
			valid = false;
		}

		if(fromWays.isEmpty() ) {
			log.warn(messagePrefix, "lacks 'from' way");
			valid = false;
		}

		if(toWays.isEmpty()) {
			log.warn(messagePrefix, "lacks 'to' way");
			valid = false;
		}

		if ((fromWays.size() > 1 || toWays.size() > 1) && viaWays.isEmpty() == false){
			log.warn(messagePrefix, "via way(s) are not supported with multiple from or to ways");
			valid = false;
		}
		if (toWays.size() == 1 && fromWays.size() == 1 && viaWays.isEmpty()){
			if ("no_u_turn".equals(restriction) && fromWays.get(0).equals(toWays.get(0))){
				log.warn(messagePrefix,"no_u_turn with equal 'from' and 'to' way is ignored");
				valid = false;
			}
		}
		if (!valid)
			return;
		for (List<Way> ways : Arrays.asList(fromWays,viaWays,toWays)){
			for (Way way : ways){
				if (way.getPoints().size() < 2){
					log.warn(messagePrefix,"way",way.toBrowseURL(),"has less than 2 points, restriction is ignored");
					valid = false;
				} else {
					if (way.getPoints().get(0) == way.getPoints().get(way.getPoints().size()-1)){
						log.warn(messagePrefix, "way", way.toBrowseURL(), "starts and ends at same node, this is not supported");
						valid = false;
					}
				}
			}
		}
		if (!valid)
			return;
		if (viaWays.isEmpty() == false){
			for (Way way:viaWays){
				way.getPoints().get(0).setViaNodeOfRestriction(true);
				way.getPoints().get(way.getPoints().size()-1).setViaNodeOfRestriction(true);
				way.setViaWay(true);
			}
		}
		if (viaCoord != null) 
			viaCoord.setViaNodeOfRestriction(true);
	}

	/** 
	 * match the vehicle type in a restriction with the garmin type
	 * and modify the exceptMask 
	 * @param vehicle
	 * @param b true: restriction should not apply for vehicle, false: restriction should apply  
	 * @return true if vehicle has a matching flag in the garmin format
	 */
	private boolean setExceptMask(String vehicle, boolean b){
		byte flag = 0;
		if(vehicle.equals("motorcar") || vehicle.equals("motorcycle"))
			flag = RouteRestriction.EXCEPT_CAR;
		else if(vehicle.equals("motor_vehicle"))
			flag = (byte) (~(RouteRestriction.EXCEPT_BICYCLE | RouteRestriction.EXCEPT_FOOT) & 0xff);
		else if(vehicle.equals("psv") || vehicle.equals("bus"))
			flag = RouteRestriction.EXCEPT_BUS;
		else if(vehicle.equals("taxi"))
			flag = RouteRestriction.EXCEPT_TAXI;
		else if(vehicle.equals("delivery") || vehicle.equals("goods"))
			flag = RouteRestriction.EXCEPT_DELIVERY;
		else if(vehicle.equals("bicycle"))
			flag = RouteRestriction.EXCEPT_BICYCLE;
		else if(vehicle.equals("hgv") || vehicle.equals("truck"))
			flag = RouteRestriction.EXCEPT_TRUCK;
		else if(vehicle.equals("emergency"))
			flag = RouteRestriction.EXCEPT_EMERGENCY;
		else if(vehicle.equals("foot"))
			flag = RouteRestriction.EXCEPT_FOOT;
		if (flag == 0){
			log.warn(messagePrefix, "ignoring unsupported vehicle class '" + vehicle + "' in turn restriction");
			return false;
		} else {
			if (b)
				exceptMask |= flag;
			else 
				exceptMask &= ~flag;
		}
		return true;			
	}
	
	public boolean isFromWay(Way way) {
		return fromWays.contains(way);
	}

	public boolean isToWay(Way way) {
		return toWays.contains(way);
	}

	public void replaceViaCoord(Coord oldP, Coord newP) {
		for (int i = 0; i < viaPoints.size(); i++){
			if (viaPoints.get(i) == oldP){
				viaPoints.set(i, newP);
				if (log.isDebugEnabled()){
					log.debug(messagePrefix, restriction, "'via' coord redefined from",
							oldP.toOSMURL(), "to", newP.toOSMURL());
				}
				return;
			}
		}
	}
	
	public boolean isValid() {
		if (!evalWasCalled){
			log.warn("internal error: RestrictionRelation.isValid() is called before eval()");
			eval();
		}
		if(!valid)
			return false;
		
		if (viaPoints.isEmpty() == false)
			viaCoord = viaPoints.get(0);
		
		if(viaCoord == null && viaWays.isEmpty()) {
			valid = false;
			return false;
		}
		
		viaPoints.clear();
		Coord v1 = viaCoord;
		Coord v2 = viaCoord;
		if (viaWays.isEmpty() == false){
			v1 = viaWays.get(0).getPoints().get(0);
			v2 = viaWays.get(0).getPoints().get(viaWays.get(0).getPoints().size()-1);
		}
		// check if all from ways are connected at the given via point or with the given via ways
		for (Way fromWay : fromWays){
			Coord e1 = fromWay.getPoints().get(0);
			Coord e2 = fromWay.getPoints().get(fromWay.getPoints().size() - 1);
			if (e1 == v1 || e2 == v1)
				viaCoord = v1;
			else if (e1 == v2 || e2 == v2)
				viaCoord = v2;
			else {
				log.warn(messagePrefix, "'from' way", fromWay.toBrowseURL(), "doesn't start or end at 'via' node or way");
				valid = false;
			} 
		}
		if (!valid)
			return false;
		viaPoints.add(viaCoord);
		// check if via ways are connected in the given order
		for (int i = 0; i < viaWays.size();i++){
			Way way = viaWays.get(i);
			Coord v = viaPoints.get(viaPoints.size()-1);
			if (way.getPoints().get(0) == v)
				v2 = way.getPoints().get(way.getPoints().size()-1);
			else if (way.getPoints().get(way.getPoints().size()-1) == v)
				v2 = way.getPoints().get(0);
			else {
				log.warn(messagePrefix, "'via' way", way.toBrowseURL(), "doesn't start or end at",v.toDegreeString());
				valid = false;
			}
			viaPoints.add(v2);
		}
		if (!valid)
			return false;
		// check if all to ways are connected to via point or last via way
		Coord lastVia = viaPoints.get(viaPoints.size()-1);
		for (Way toWay : toWays){
			Coord e1 = toWay.getPoints().get(0);
			Coord e2 = toWay.getPoints().get(toWay.getPoints().size() - 1);
			if(e1 != lastVia && e2 != lastVia) {
				log.warn(messagePrefix, "'to' way", toWay.toBrowseURL(), "doesn't start or end at 'via' node or way");
				valid = false;
			} 
		}
		if (valid && !viaWays.isEmpty() && isOnlyXXXRestriction()){
			log.warn(messagePrefix, "check: 'via' way(s) are used in",restriction,"restriction");
		}
		if (valid && viaWays.size() > 1){
			log.warn(messagePrefix, "sorry, multiple via ways are not (yet) supported");
			valid = false;
		}
		if (!valid)
			return false;
		for (Coord v: viaPoints)
			v.setViaNodeOfRestriction(true);
		return valid;
	}

	public void addRestriction(MapCollector collector, IdentityHashMap<Coord, CoordNode> nodeIdMap) {
		if (!valid)
			return;
		
	    List<CoordNode> viaNodes = new ArrayList<>();
		for (Coord v: viaPoints){
			CoordNode vn = nodeIdMap.get(v);
			if (vn == null){
				log.error(messagePrefix,"via node is not a routing node");
				return;
			}
			else 
				viaNodes.add(vn);
		}
		List<NodeOnWay> fromNodes = new ArrayList<>(); 
		for (Way fromWay: fromWays){
			CoordNode fromNode = findNextNode(fromWay, viaNodes.get(0));
			if (fromNode == null){
				log.warn(messagePrefix, "can't find routable 'from' node on 'from' way",fromWay);
				return;
			}
			fromNodes.add(new NodeOnWay(fromNode, fromWay));
		}
		Coord currNode = viaNodes.get(0);
		
		for (Way viaWay: viaWays){
			if (viaWay.getPoints().get(0).getId() == 0 || viaWay.getPoints().get(viaWay.getPoints().size()-1).getId() == 0){
				log.error(messagePrefix, "'via' way", viaWay.toBrowseURL(), "doesn't start or end with CoordNode");
				return;
			}
			Coord nextNode = null;
			if (viaWay.getPoints().get(0) == currNode)
				nextNode = viaWay.getPoints().get(viaWay.getPoints().size()-1);
			else 
				nextNode = viaWay.getPoints().get(0);
			Coord co = findNextNode(viaWay, currNode);
			if (nextNode != co){
				if (co == null)
					return;
				log.warn(messagePrefix, "'via' way", viaWay.toBrowseURL(), "is also connected to other roads between end-points at",co.toDegreeString()); 
				return;
			}
			currNode = nextNode;
		} 
		List<NodeOnWay> toNodes = new ArrayList<>(); 
		for (Way toWay: toWays){
			CoordNode toNode = findNextNode(toWay, currNode);
			if (toNode == null){
				log.warn(messagePrefix, "can't find routable 'to' node on 'to' way",toWay);
				return;
			}
			toNodes.add(new NodeOnWay(toNode, toWay));
		}
		
		if(restriction == null || fromNodes.isEmpty() || toNodes.isEmpty()) {
			log.error("internal error: can't add valid restriction relation", this.getId(), "type", restriction);
			return;
		}
		
		GeneralRouteRestriction grr;
		List<Long> viaWayIds = new ArrayList<>();
		for (Way viaWay : viaWays)
			viaWayIds.add(viaWay.getId());
		if(restriction.startsWith("no_")){
			for (NodeOnWay fromNode : fromNodes){
				for (NodeOnWay toNode : toNodes){
					grr = new GeneralRouteRestriction("not", exceptMask, messagePrefix);
					grr.setFromNode(fromNode.node);
					grr.setFromWayId(fromNode.way.getId());
					grr.setToNode(toNode.node);
					grr.setToWayId(toNode.way.getId());
					grr.setViaNodes(viaNodes);
					grr.setViaWayIds(viaWayIds);

					int numAdded = collector.addRestriction(grr);
					if (numAdded == 0){
						return; // message was created before
					}
					if(restriction.startsWith("no_turn"))
						log.warn(messagePrefix, "has bad type '" + restriction + "' it should be of the form no_X_turn rather than no_turn_X - I added the restriction anyway - blocks routing to way", toNode.way.toBrowseURL());
					else 
						log.info(messagePrefix, restriction, "added - blocks routing to way", toNode.way.toBrowseURL());
				}
			}
		}
		else if(restriction.startsWith("only_")){
			log.info(messagePrefix, restriction, "added - allows routing to way", toWays.get(0).toBrowseURL());
			grr = new GeneralRouteRestriction("only", exceptMask, messagePrefix);
			grr.setFromNode(fromNodes.get(0).node);
			grr.setFromWayId(fromNodes.get(0).way.getId());
			grr.setToNode(toNodes.get(0).node);
			grr.setToWayId(toNodes.get(0).way.getId());
			grr.setViaNodes(viaNodes);
			grr.setViaWayIds(viaWayIds);
			int numAdded = collector.addRestriction(grr);
			if (numAdded == 0){
				return; // message was created before
			}
		}
		else {
			log.warn(messagePrefix, "has unsupported type '" + restriction + "'");
		}
	}

	/** Process the members in this relation.
	 */
	public void processElements() {
		// relax
	}

	public String toString() {
		return "[restriction = " + restriction + ", from = " + fromWays.get(0).toBrowseURL() + ", to = " + toWays.get(0).toBrowseURL() + ", via = " + viaCoord.toOSMURL() + "]";
	}
	
	public boolean isOnlyXXXRestriction(){
		return restriction.startsWith("only");
	}
	
	/**
	 * First next CoordNode on the way, starting search at start
	 * @param way the way
	 * @param currNode the start node (
	 * @return the next node or null if none was found
	 */
	private CoordNode findNextNode(Way way, Coord currNode){
		List<Coord> points = way.getPoints();
		if (points.get(0) == currNode){
			for (int i = 1; i < points.size(); i++){
				Coord co = points.get(i);
				if (co.getId() != 0)
					return (CoordNode)co;
			}
		} else if (points.get(points.size()-1) == currNode){
			for (int i = points.size()-2; i >= 0; --i){
				Coord co = points.get(i);
				if (co.getId() != 0)
					return (CoordNode)co;
			}
		} else 
			log.error(messagePrefix,"way",way.toBrowseURL(),"doesn't have required node");
			
		return null;
	}

	/**
	 * Check if the via node(s) of the restriction are all inside 
	 * or on the bbox and if the restriction itself is valid. 
	 * @param bbox
	 * @return true if restriction is okay
	 */
	public boolean isValid(Area bbox) {
		if (!isValid())
			return false;
		if (viaCoord != null && bbox.contains(viaCoord) == false)
			return false;
		int countInside = 0;
		for (Way viaWay : viaWays){
			if(bbox.contains(viaWay.getPoints().get(0)))
				++countInside;
			if(bbox.contains(viaWay.getPoints().get(viaWay.getPoints().size()-1)))
				++countInside;
		}
		if (countInside > 0 && countInside < viaWays.size() * 2){
			log.warn(messagePrefix,"via way crosses tile boundary. Don't know how to save that, ignoring it");
			return false;
		}
		return true;
	}

	public List<Coord> getViaCoords() {
		return viaPoints;
	}

	public Set<Long> getConnectedWayIds(Coord via){
		Set<Long> wayIds = new HashSet<>();
		for (List<Way> ways : Arrays.asList(fromWays,viaWays,toWays)){
			for (Way way : ways){
				List<Coord> points = way.getPoints();
				if (points.get(0) == via || points.get(points.size()-1) == via)
					wayIds.add(way.getId());
			}
		}
		return wayIds;
	}
	
	public Set<Long> getWayIds(){
		Set<Long> wayIds = new HashSet<>();
		for (List<Way> ways : Arrays.asList(fromWays,viaWays,toWays)){
			for (Way way : ways){
				wayIds.add(way.getId());
			}
		}
		return wayIds;
	}
	
	public boolean replaceWay(Way way, Way nWay) {
		boolean matched = false;
		for (List<Way> ways : Arrays.asList(fromWays,viaWays,toWays)){
			for (int i = 0; i < ways.size(); i++){
				if (ways.get(i) == way){
					ways.set(i, nWay);
					matched = true;
				}
			}
		}
		if (way.isViaWay())
			nWay.setViaWay(true);
		return matched;
	}

	// helper class 
	private class NodeOnWay{
		final CoordNode node;
		final Way way;
		public NodeOnWay(CoordNode node, Way way) {
			this.node = node;
			this.way = way;
		}
	}

	/**
	 * check if restriction is still valid if the given way is removed
	 * @param way
	 * @return
	 */
	public boolean isValidWithoputWay(Way way) {
		if (viaWays.contains(way))
			return false;
		if (fromWays.contains(way)){
			fromWays.remove(way);
			if (fromWays.isEmpty())
				return false;
			// else it must be a no_entry restriction which still is valid
		}
		if (toWays.contains(way)){
			toWays.remove(way);
			if (toWays.isEmpty())
				return false;
			// else it must be a no_exit restriction which still is valid
		}
		return true;
	}
}

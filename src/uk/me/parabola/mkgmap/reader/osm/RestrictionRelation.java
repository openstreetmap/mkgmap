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
import java.util.Collections;
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
 * @author Mark Burton, GerdP
 */
public class RestrictionRelation extends Relation {
    private static final Logger log = Logger.getLogger(RestrictionRelation.class);

    private List<Long> fromWayIds = new ArrayList<>(2);
    private List<Long> toWayIds = new ArrayList<>(2);
    private List<Long> viaWayIds = new ArrayList<>(2);
    private List<Coord> viaPoints = new ArrayList<>(2);
    private Coord viaCoord;
    private String restriction;
	private byte exceptMask;
	private char dirIndicator; 
    private String messagePrefix;
    private boolean valid;
    private boolean evalWasCalled;
    
	// These tags are not loaded by default but if they exist issue a warning
	final static String[] unsupportedTags = { "day_on", "day_off", "hour_on", "hour_off" };
	
	final static List<String> supportedRestrictions = Arrays.asList(
		"no_right_turn", "no_left_turn", "no_u_turn", "no_straight_on", 
		"only_right_turn", "only_left_turn", "only_straight_on", 
		"no_entry", "no_exit"	     
	);
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
	 * The evaluation should happen after style processing.
	 * Normally this is called from the {@link RelationStyleHook}
	 * Performs also diverse plausibility checks.
	 * @param bbox tile boundary
	 */
	public void eval(Area bbox){
		if (evalWasCalled){
			log.error(messagePrefix,"internal error: eval() was already called");
			fromWayIds.clear();
			toWayIds.clear();
			viaWayIds.clear();
		}
		if (getTag("type") == null){
			// style removed the tag
			log.info(messagePrefix, "type tag was removed, relation is ignored");				
			valid = false;
			return;
		}
		
	    List<Way> fromWays = new ArrayList<>();
	    List<Way> toWays = new ArrayList<>();
	    List<Way> viaWays = new ArrayList<>();
		evalWasCalled = true;
		final String browseURL = toBrowseURL();
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
		if (specifc_type == null){
			// style removed the tag
			log.info(messagePrefix, "no valid restriction tag found");				
			valid = false;
			return;
		}
		restriction = specifc_type.trim();
		
		messagePrefix = "Turn restriction (" + restriction + ") " + browseURL;
		if (supportedRestrictions.contains(restriction) == false){
			log.warn(messagePrefix, "ignoring unsupported restriction type '" + restriction + "'");
			valid = false;
			return;
		}
		
		String dirInfo = "";
		if (restriction.contains("left"))
			dirInfo += "l";
		if (restriction.contains("right"))
			dirInfo += "r";
		if (restriction.contains("straight"))
			dirInfo += "s";
		if (restriction.endsWith("u_turn"))
			dirInfo += "u";
		if (dirInfo.length() > 1){
			log.warn(messagePrefix, "ignoring unsupported restriction type '" + restriction + "'");
			valid = false;
			return;
		} else if (dirInfo.length() == 1){
			dirIndicator = dirInfo.charAt(0);
		} else 
			dirIndicator = '?';
		
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
				if (fromPoints.get(0) != viaCoord && fromPoints.get(fromPoints.size()-1) != viaCoord ||
						toPoints.get(0) != viaCoord && toPoints.get(toPoints.size()-1) != viaCoord){
					log.warn(messagePrefix, "lacks 'via' node and the 'from' (" + fromWay.toBrowseURL() + ") and 'to' (" + toWay.toBrowseURL() + ") ways don't connect at an end point");
					valid = false;
				} else
					log.warn(messagePrefix, "lacks 'via' node (guessing it should be at", viaCoord.toOSMURL() + ", why don't you add it to the OSM data?)");				
			}
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
			log.warn(messagePrefix, "'via' way(s) are not supported with multiple 'from' or 'to' ways");
			valid = false;
		}
		if (toWays.size() == 1 && fromWays.size() == 1 && viaWays.isEmpty()){
			if ("no_u_turn".equals(restriction) && fromWays.get(0).equals(toWays.get(0))){
				log.warn(messagePrefix,"no_u_turn with equal 'from' and 'to' way and via node is ignored");
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
						if (ways == toWays && dirIndicator != '?')
							continue; // we try to determine the correct part in RoadNetwork 
						log.warn(messagePrefix, "way", way.toBrowseURL(), "starts and ends at same node, don't know which one to use");
						valid = false;
					}
				}
			}
		}
		if (!valid)
			return;
		if (viaPoints.isEmpty() == false)
			viaCoord = viaPoints.get(0);
		
		if(viaCoord == null && viaWays.isEmpty()) {
			valid = false;
			return;
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
			return;
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
		
		// check if all via points are inside the bounding box
		int countInside = 0;
		for (Coord via: viaPoints){
			if(bbox.contains(via))
				++countInside;
		}
		if (countInside == 0)
			valid = false;
		else if (countInside > 0 && countInside < viaPoints.size()){
			log.warn(messagePrefix,"via way crosses tile boundary. Don't know how to save that, ignoring it");
			valid = false;
		}
		
		if (!valid)
			return;
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
		if (valid && !viaWays.isEmpty() && restriction.startsWith("only")){
			log.warn(messagePrefix, "check: 'via' way(s) are used in",restriction,"restriction");
		}
		if (valid){
			// make sure that via way(s) don't appear in the from or to lists 
			for (Way w: viaWays){
				if (fromWays.contains(w)){
					log.warn(messagePrefix, "'via' way",w.toBrowseURL(),"appears also as 'from' way");
					valid = false;
				}
				if (toWays.contains(w)){
					log.warn(messagePrefix, "'via' way",w.toBrowseURL(),"appears also as 'to' way");
					valid = false;
				}
			}
		}
		if (valid){
			for (Way w: fromWays)
				fromWayIds.add(w.getId());
			for (Way w: toWays)
				toWayIds.add(w.getId());
			for (Way w: viaWays){
				w.setViaWay(true);
				viaWayIds.add(w.getId());
			}
			for (Coord v: viaPoints)
				v.setViaNodeOfRestriction(true);
		}
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
	
	public boolean isFromWay(long wayId) {
		return fromWayIds.contains(wayId);
	}

	public boolean isToWay(long wayId) {
		return toWayIds.contains(wayId);
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

		if (viaNodes.size() > 6){
			log.warn(messagePrefix,"has more than 6 via nodes, this is not supported");
			return;
		}
		if(restriction == null){
			log.error("internal error: can't add valid restriction relation", this.getId(), "type", restriction);
			return;
		}
		int addedRestrictions = 0;
		GeneralRouteRestriction grr;
		if(restriction.startsWith("no_")){
			for (long fromWayId : fromWayIds){
				for (long toWayId : toWayIds){
					grr = new GeneralRouteRestriction("not", exceptMask, messagePrefix);
					grr.setFromWayId(fromWayId);
					grr.setToWayId(toWayId);
					grr.setViaNodes(viaNodes);
					grr.setViaWayIds(viaWayIds);
					grr.setDirIndicator(dirIndicator);
					addedRestrictions += collector.addRestriction(grr);
				}
			}
			if (log.isInfoEnabled())
				log.info(messagePrefix, restriction, "translated to",addedRestrictions,"img file restrictions");
		}
		else if(restriction.startsWith("only_")){
			grr = new GeneralRouteRestriction("only", exceptMask, messagePrefix);
			grr.setFromWayId(fromWayIds.get(0));
			grr.setToWayId(toWayIds.get(0));
			grr.setViaNodes(viaNodes);
			grr.setViaWayIds(viaWayIds);
			grr.setDirIndicator(dirIndicator);
			int numAdded = collector.addRestriction(grr);
			if (numAdded > 0)
				log.info(messagePrefix, restriction, "added - allows routing to way", toWayIds.get(0));

		}
		else {
			log.error("mkgmap internal error: unknown restriction", restriction);
		}
	}

	/** Process the members in this relation.
	 */
	public void processElements() {
		// relax
	}

	public String toString() {
		String s = "[restriction id = " + getId() + "(" + restriction + ")";
		if (!fromWayIds.isEmpty() && !toWayIds.isEmpty() && viaCoord != null )
			s += ", from = " + fromWayIds.get(0) + ", to = " + toWayIds.get(0) + ", via = " + viaCoord.toOSMURL() + "]";
		else 
			s += "]";
		return s;
	}
	
	/**
	 * @return true if restriction is usable
	 */
	public boolean isValid() {
		assert evalWasCalled;
		return valid;
	}

	public List<Coord> getViaCoords() {
		assert evalWasCalled;
		return viaPoints;
	}

	/**
	 * 
	 * @return a Set with the OSM IDs of all ways used in the restriction 
	 */
	public Set<Long> getWayIds(){
		assert evalWasCalled;
		Set<Long> wayIds = new HashSet<>();
		wayIds.addAll(fromWayIds);
		wayIds.addAll(viaWayIds);
		wayIds.addAll(toWayIds);
		return wayIds;
	}
	
	/**
	 * Replace 
	 * @param oldWayId
	 * @param newWayId
	 * @return
	 */
	public boolean replaceWay(long oldWayId, long newWayId) {
		assert evalWasCalled;
		boolean matched = false;
		for (List<Long> ways: Arrays.asList(fromWayIds, viaWayIds, toWayIds)){
			for (int i = 0; i < ways.size(); i++){
				if (ways.get(i) == oldWayId){
					ways.set(i, newWayId);
					matched = true;
				}
			}
		}
		return matched;
	}

	/**
	 * check if restriction is still valid if the way with the given id is not in the map
	 * @param wayId
	 * @return
	 */
	public boolean isValidWithoputWay(long wayId) {
		assert evalWasCalled;
		if (viaWayIds.contains(wayId))
			return false;
		fromWayIds.remove(wayId);
		if (fromWayIds.isEmpty())
			return false;
		// else it must be a no_entry restriction which still is valid

		toWayIds.remove(wayId);
		if (toWayIds.isEmpty())
			return false;
		// else it must be a no_exit restriction which still is valid
		return true;
	}

	/**
	 * A via way may be connected to other ways between the end points.
	 * We have to create a complete path for that. 
	 * @param way
	 * @param nodeIndices
	 */
	public void updateViaWay(Way way, List<Integer> nodeIndices) {
		if (!valid)
			return;
		if (viaWayIds.contains(way.getId()) == false)
			return;
		Coord first = way.getPoints().get(nodeIndices.get(0));
		Coord last = way.getPoints().get(
				nodeIndices.get(nodeIndices.size() - 1));
		int posFirst = -1;
		for (int i = 0; i < viaPoints.size(); i++) {
			if (first == viaPoints.get(i)) {
				posFirst = i;
				break;
			}
		}
		int posLast = -1;
		for (int i = 0; i < viaPoints.size(); i++) {
			if (last == viaPoints.get(i)) {
				posLast = i;
				break;
			}
		}
		if (posFirst < 0  || posLast < 0 || Math.abs(posLast-posFirst) > 1){
			log.error(messagePrefix, "internal error: via way doesn't contain expected points");
			valid = false;
			return;
		}
		List<Coord> midPoints = new ArrayList<>();
		for (int i = 1; i + 1 < nodeIndices.size(); i++) {
			midPoints.add(way.getPoints().get(nodeIndices.get(i)));
		}
		if (posFirst < posLast)
			viaPoints.addAll(posFirst + 1, midPoints); 
		else {
			Collections.reverse(midPoints);
			viaPoints.addAll(posLast + 1, midPoints);
		}
		
		int wayPos = viaWayIds.indexOf(way.getId());
		for (int i = 0; i < midPoints.size(); i++){
			viaWayIds.add(wayPos+1, way.getId());
		}
		if (viaPoints.size() != viaWayIds.size()+1){
			log.error("internal error: number of via points and via ways no longer fits");
			valid = false;
		} else if (viaPoints.size() > 6){
			log.warn(messagePrefix,"has more than 6 via nodes, this is not supported");
			valid = false;
		} 
	}
}

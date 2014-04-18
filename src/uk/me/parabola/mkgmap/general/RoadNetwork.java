/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 13-Jul-2008
 */
package uk.me.parabola.mkgmap.general;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.GeneralRouteRestriction;
import uk.me.parabola.imgfmt.app.net.NOD1Part;
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.imgfmt.app.net.RouteArc;
import uk.me.parabola.imgfmt.app.net.RouteCenter;
import uk.me.parabola.imgfmt.app.net.RouteNode;
import uk.me.parabola.imgfmt.app.net.RouteRestriction;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This holds the road network.  That is all the roads and the nodes
 * that connect them together.
 * 
 * @see <a href="http://www.movable-type.co.uk/scripts/latlong.html">Distance / bearing calculations</a>
 * @author Steve Ratcliffe
 */
public class RoadNetwork {
	private static final Logger log = Logger.getLogger(RoadNetwork.class);

	public static final int NO_EMERGENCY = 0;
	public static final int NO_DELIVERY = 1;
	public static final int NO_CAR = 2;
	public static final int NO_BUS = 3;
	public static final int NO_TAXI = 4;
	public static final int NO_FOOT = 5;
	public static final int NO_BIKE = 6;
	public static final int NO_TRUCK = 7;
	public static final int NO_MAX = 8;

	private final static int MAX_RESTRICTIONS_ARCS = 7;
	private final Map<Long, RouteNode> nodes = new LinkedHashMap<Long, RouteNode>();

	// boundary nodes
	// a node should be in here if the nodes boundary flag is set
	private final List<RouteNode> boundary = new ArrayList<RouteNode>();
	private final List<RoadDef> roadDefs = new ArrayList<RoadDef>();
	private List<RouteCenter> centers = new ArrayList<RouteCenter>();
	private int adjustTurnHeadings ;
	private boolean checkRoundabouts;
	private boolean checkRoundaboutFlares;
	private int maxFlareLengthRatio ;
	private boolean reportSimilarArcs;

	public void config(EnhancedProperties props) {
		String ath = props.getProperty("adjust-turn-headings");
		if(ath != null) {
			if(ath.length() > 0)
				adjustTurnHeadings = Integer.decode(ath);
			else
				adjustTurnHeadings = RouteNode.ATH_DEFAULT_MASK;
		}
		checkRoundabouts = props.getProperty("check-roundabouts", false);
		checkRoundaboutFlares = props.getProperty("check-roundabout-flares", false);
		maxFlareLengthRatio = props.getProperty("max-flare-length-ratio", 0);

		reportSimilarArcs = props.getProperty("report-similar-arcs", false);
	}

	public void addRoad(MapRoad road) {
		//mapRoads.add(road);
		roadDefs.add(road.getRoadDef()); //XXX

		CoordNode lastCoord = null;
		int lastIndex = 0;
		double roadLength = 0;
		double arcLength = 0;
		int pointsHash = 0;

		List<Coord> coordList = road.getPoints();
		int npoints = coordList.size();
		for (int index = 0; index < npoints; index++) {
			Coord co = coordList.get(index);

			if (index > 0) {
				double d = co.distance(coordList.get(index-1));
				arcLength += d;
				roadLength += d;
			}

			long id = co.getId();

			pointsHash += co.hashCode();

			if (id == 0)
				// not a routing node
				continue;

			// The next coord determines the heading
			// If this is the not the first node, then create an arc from
			// the previous node to this one (and back again).
			if (lastCoord != null) {
				long lastId = lastCoord.getId();
				if(log.isDebugEnabled()) {
					log.debug("lastId = " + lastId + " curId = " + id);
					log.debug("from " + lastCoord.toDegreeString() 
							  + " to " + co.toDegreeString());
					log.debug("arclength=" + arcLength + " roadlength=" + roadLength);
				}

				RouteNode node1 = getNode(lastId, lastCoord);
				RouteNode node2 = getNode(id, co);

				if(node1 == node2)
					log.error("Road " + road.getRoadDef() + " contains consecutive identical nodes at " + co.toOSMURL() + " - routing will be broken");
				else if(arcLength == 0)
					log.warn("Road " + road.getRoadDef() + " contains zero length arc at " + co.toOSMURL());

				Coord forwardBearingPoint = coordList.get(lastIndex + 1);
				if(lastCoord.equals(forwardBearingPoint)) {
					// bearing point is too close to last node to be
					// useful - try some more points
					for(int bi = lastIndex + 2; bi <= index; ++bi) {
						if(!lastCoord.equals(coordList.get(bi))) {
							forwardBearingPoint = coordList.get(bi);
							break;
						}
					}
				}
				Coord reverseBearingPoint = coordList.get(index - 1);
				if(co.equals(reverseBearingPoint)) {
					// bearing point is too close to this node to be
					// useful - try some more points
					for(int bi = index - 2; bi > lastIndex; --bi) {
						if(!co.equals(coordList.get(bi))) {
							reverseBearingPoint = coordList.get(bi);
							break;
						}
					}
				}
				
				double forwardInitialBearing = lastCoord.bearingTo(forwardBearingPoint);
				double forwardDirectBearing = (co == forwardBearingPoint) ? forwardInitialBearing: lastCoord.bearingTo(co); 

				double reverseInitialBearing = co.bearingTo(reverseBearingPoint);
				double reverseDirectBearing = (lastCoord == reverseBearingPoint) ? reverseInitialBearing: co.bearingTo(lastCoord); 

				// TODO: maybe detect cases where bearing was already calculated above 
				double forwardFinalBearing = reverseBearingPoint.bearingTo(co); 
				double reverseFinalBearing = forwardBearingPoint.bearingTo(lastCoord);

				double directLength = (lastIndex + 1 == index) ? arcLength : lastCoord.distance(co);
				// Create forward arc from node1 to node2
				RouteArc arc = new RouteArc(road.getRoadDef(),
											node1,
											node2,
											forwardInitialBearing,
											forwardFinalBearing,
											forwardDirectBearing,
											arcLength,
											arcLength,
											directLength,
											pointsHash);
				arc.setForward();
				node1.addArc(arc);
				node2.addIncomingArc(arc);
				
				// Create the reverse arc
				arc = new RouteArc(road.getRoadDef(),
								   node2, node1,
								   reverseInitialBearing,
								   reverseFinalBearing,
								   reverseDirectBearing,
								   arcLength,
								   arcLength,
								   directLength,
								   pointsHash);
				node2.addArc(arc);
				node1.addIncomingArc(arc);
			} else {
				// This is the first node in the road
				road.getRoadDef().setNode(getNode(id, co));
			}

			lastCoord = (CoordNode) co;
			lastIndex = index;
			arcLength = 0;
			pointsHash = co.hashCode();
		}
		road.getRoadDef().setLength(roadLength);
	}

	private RouteNode getNode(long id, Coord coord) {
		RouteNode node = nodes.get(id);
		if (node == null) {
			node = new RouteNode(coord);
			nodes.put(id, node);
			if (node.isBoundary())
				boundary.add(node);
		}
		return node;
	}

	public List<RoadDef> getRoadDefs() {
		return roadDefs;
	}

	/**
	 * Split the network into RouteCenters.
	 *
	 * The resulting centers must satisfy several constraints,
	 * documented in NOD1Part.
	 */
	private void splitCenters() {
		if (nodes.isEmpty())
			return;
		assert centers.isEmpty() : "already subdivided into centers";

		// sort nodes by NodeGroup 
		List<RouteNode> nodeList = new ArrayList<>(nodes.values());
		nodes.clear(); // return to GC
		for (int group = 0; group <= 4; group++){
			NOD1Part nod1 = new NOD1Part();
			int n = 0;
			for (RouteNode node : nodeList) {
				if (node.getGroup() != group)
					continue;
				if(!node.isBoundary()) {
					if(checkRoundabouts)
						node.checkRoundabouts();
					if(checkRoundaboutFlares)
						node.checkRoundaboutFlares(maxFlareLengthRatio);
					if(reportSimilarArcs)
						node.reportSimilarArcs();
				}
				if(adjustTurnHeadings != 0)
					node.tweezeArcs(adjustTurnHeadings);
				nod1.addNode(node);
				n++;
			}
			if (n > 0)
				centers.addAll(nod1.subdivide());
		}
	}

	public List<RouteCenter> getCenters() {
		if (centers.isEmpty()){
			addArcsToMajorRoads();
			splitCenters();
		}
		return centers;
	}

	/**
	 * add indirect arcs for each road class (in descending order)
	 */
	private void addArcsToMajorRoads() {
		long t1 = System.currentTimeMillis();
		
		for (RoadDef rd: roadDefs){
			if (rd.getRoadClass() >= 1)
				rd.getNode().addArcsToMajorRoads(rd);
		}
		log.info(" added major road arcs in " + (System.currentTimeMillis() - t1) + " ms");
	}

	/**
	 * Get the list of nodes on the boundary of the network.
	 *
	 * Currently empty.
	 */
	public List<RouteNode> getBoundary() {
		return boundary;
	}

	/**
	 * One restriction forbids to travel a specific combination of arcs.
	 * We know two kinds: 3 nodes with two arcs and one via node or 4 nodes with 3 arcs
	 * and two via nodes. Maybe more nodes are possible, but we don't know for sure how
	 * to write them (2014-04-02). 
	 * Depending on the data in grr we create one or more such restrictions.
	 * A restriction with 4 (or more) nodes is added to each via node.     
	 * 
	 * The OSM restriction gives a from way id and a to way id and one or more 
	 * via nodes. It is possible that the to-way is a loop, so we have to identify
	 * the correct arc. 
	 * @param grr the object that holds the details about the route restriction
	 */
	public int addRestriction(GeneralRouteRestriction grr) {
		if (grr.getType() == GeneralRouteRestriction.RestrType.TYPE_NO_TROUGH)
			return addNoThroughRoute(grr);
		String sourceDesc = grr.getSourceDesc();
		
		List<RouteNode> viaNodes = new ArrayList<>();
		for (CoordNode via : grr.getViaNodes()){
			RouteNode vn = nodes.get(via.getId());
			if (vn == null){
				log.error(sourceDesc, "can't locate 'via' RouteNode with id", via.getId());
				return 0;
			}
			viaNodes.add(vn);
		}
		
		long firstViaId = grr.getViaNodes().get(0).getId();
		long lastViaId = grr.getViaNodes().get(grr.getViaNodes().size()-1).getId();
		RouteNode firstViaNode = nodes.get(firstViaId);
		RouteNode lastViaNode = nodes.get(lastViaId);
		List<List<RouteArc>> viaArcsList = new ArrayList<>();
		if (grr.getViaNodes().size() != grr.getViaWayIds().size() + 1){
			log.error(sourceDesc, "internal error: number of via nodes and via ways doesn't fit");
			return 0;
		}
		for (int i = 1; i < grr.getViaNodes().size(); i++){
			RouteNode vn = viaNodes.get(i-1);
			Long viaWayId = grr.getViaWayIds().get(i-1);
			List<RouteArc> viaArcs = vn.getDirectArcsTo(viaNodes.get(i), viaWayId);
			if (viaArcs.isEmpty()){
				log.error(sourceDesc, "can't locate arc from 'via' node at",vn.getCoord().toOSMURL(),"to next 'via' node on way",viaWayId);
				return 0;
			}
			viaArcsList.add(viaArcs);
		}
		
		// determine the from node and arc(s)
		long fromId = 0;
		RouteNode fn = null;
		if (grr.getFromNode() != null){
			fromId = grr.getFromNode().getId();
			// polish input data provides id
			fn = nodes.get(fromId);
			if (fn == null ){
				log.error(sourceDesc, "can't locate 'from' RouteNode with id", fromId);
				return 0; 
			}
		} else {
			List<RouteArc> possibleFromArcs = firstViaNode.getDirectArcsOnWay(grr.getFromWayId());
			for (RouteArc arc : possibleFromArcs){
				if (fn == null)
					fn = arc.getDest();
				else if (fn != arc.getDest()){
					log.warn(sourceDesc, "found different 'from' arcs for way",grr.getFromWayId(),"restriction is ignored");
					return 0;
				}
			}
			if (fn == null){
				log.warn(sourceDesc, "can't locate 'from' RouteNode for 'from' way", grr.getFromWayId());
				return 0;
			} else 
				fromId = fn.getCoord().getId();
		}
		List<RouteArc> fromArcs = fn.getDirectArcsTo(firstViaNode, grr.getFromWayId()); 
		if (fromArcs.isEmpty()){
			log.error(sourceDesc, "can't locate arc from 'from' node ",fromId,"to 'via' node",firstViaId,"on way",grr.getFromWayId());
			return 0;
		}
		
		// a bit more complex: determine the to-node and arc(s) 
		RouteNode uTurnNode = (viaNodes.size() > 1) ? viaNodes.get(viaNodes.size()-2): fn;
		long uTurnWay = (viaNodes.size() > 1) ? grr.getViaWayIds().get(grr.getViaWayIds().size()-1) : grr.getFromWayId(); 
		
		RouteNode tn = null;
		long toId = 0; 
		List<RouteArc> toArcs = new ArrayList<>();
		if (grr.getToNode() != null){ 
			// polish input data provides id
			toId = grr.getToNode().getId();
			tn = nodes.get(toId);
			if (tn == null ){
				log.error(sourceDesc, "can't locate 'to' RouteNode with id", toId);
				return 0; 
			}
		} else {
			// we can have multiple arcs between last via node and to node. The 
			// arcs can be on the same OSM way or on different OSM ways.
			// We can have multiple arcs with different RoadDef objects that refer to the same 
			// OSM way id. The direction indicator tells us what arc is probably meant.
			List<RouteArc> possibleToArcs = lastViaNode.getDirectArcsOnWay(grr.getToWayId());
			RouteArc fromArc = fromArcs.get(0);

			boolean ignoreAngle = false;
			if (fromArc.getLengthInMeter() <= 0.0001)
				ignoreAngle = true;
			if (grr.getDirIndicator() == '?')
				ignoreAngle = true;
			log.info(sourceDesc, "found", possibleToArcs.size(), "candidates for to-arc");

			// group the available arcs by angle 
			Map<Integer, List<RouteArc>> angleMap = new TreeMap<>();
			for (RouteArc arc : possibleToArcs){
				if (arc.getLengthInMeter() <= 0.0001)
					ignoreAngle = true;
				Integer angle = Math.round(getAngle(fromArc, arc));
				List<RouteArc> list = angleMap.get(angle);
				if (list == null){
					list = new ArrayList<>();
					angleMap.put(angle, list);
				}
				list.add(arc);
			}

			// find the group that fits best 
			Iterator<Entry<Integer, List<RouteArc>>> iter = angleMap.entrySet().iterator();
			Integer bestAngle = null;
			while (iter.hasNext()){
				Entry<Integer, List<RouteArc>> entry = iter.next();
				if (ignoreAngle || matchDirectionInfo(entry.getKey(), grr.getDirIndicator()) ){
					if (bestAngle == null)
						bestAngle = entry.getKey();
					else {
						bestAngle = getBetterAngle(bestAngle, entry.getKey(), grr.getDirIndicator());
					}
				}
			}
			
			if (bestAngle == null){
				log.warn(sourceDesc,"the angle of the from and to way don't match the restriction");
				return 0;
			} else 
				toArcs = angleMap.get(bestAngle);
		}
		if (toArcs.isEmpty()){
			log.error(sourceDesc, "can't locate arc from 'via' node ",lastViaId,"to 'to' node",toId,"on way",grr.getToWayId());
			return 0;
		}
		
		List<RouteArc> badArcs = new ArrayList<>();
		
		if (grr.getType() == GeneralRouteRestriction.RestrType.TYPE_NOT){
			for (RouteArc toArc: toArcs){
				badArcs.add(toArc);
			}
		}
		else if (grr.getType() == GeneralRouteRestriction.RestrType.TYPE_ONLY){
			// this is the inverse logic, grr gives the allowed path, we have to find the others
			int uTurns = 0;
			
			for (RouteArc badArc : lastViaNode.arcsIteration()){
				if (!badArc.isDirect() || toArcs.contains(badArc))
					continue;
				if (badArc.getDest() == uTurnNode && badArc.getRoadDef().getId() == uTurnWay){
					// ignore u-turn
					++uTurns;
					continue;
				}
				badArcs.add(badArc);
			}
			if (badArcs.isEmpty()){
				if (uTurns > 0)
					log.warn(sourceDesc, "restriction ignored because it forbids only u-turn");
				else
					log.warn(sourceDesc, "restriction ignored because it has no effect");
				return 0;
			}
		}
		
		// create all possible paths for which the restriction applies 
		List<List<RouteArc>> arcLists = new ArrayList<>();
		arcLists.add(fromArcs);
		arcLists.addAll(viaArcsList);
		arcLists.add(badArcs);
		if (arcLists.size() > MAX_RESTRICTIONS_ARCS){
			log.warn(sourceDesc, "has more than", MAX_RESTRICTIONS_ARCS, "arcs, this is not supported");
			return 0;
		}
		
		// remove arcs which cannot be travelled by the vehicles listed in the restriction
		for (int i = 0; i < arcLists.size(); i++){
			List<RouteArc> arcs =  arcLists.get(i);
			int countNoEffect = 0;
			int countOneway= 0;
			for (int j = arcs.size()-1; j >= 0; --j){
				RouteArc arc = arcs.get(j);
				if (isUsable(arc.getRoadDef().getTabAAccess(), grr.getExceptionMask()) == false){
					countNoEffect++;
					arcs.remove(j);
				}
				else if (arc.getRoadDef().isOneway()){
					if (!arc.isForward()){
						countOneway++;
						arcs.remove(j);
					}
				}
			}
			String arcType = null;
			if (arcs.isEmpty()){
				if (i == 0)
					arcType = "from way is";
				else if (i == arcLists.size()-1){
					if (grr.getType() == GeneralRouteRestriction.RestrType.TYPE_ONLY)
						arcType = "all possible other ways are";
					else 
						arcType = "to way is";
				}
				else 
					arcType = "via way is";
				String reason;
				if (countNoEffect > 0 & countOneway > 0)
					reason = "wrong direction in oneway or not accessible for restricted vehicles";
				else if (countNoEffect > 0)
					reason = "not accessible for restricted vehicles";
				else 
					reason = "wrong direction in oneway";
				log.warn(sourceDesc, "restriction ignored because",arcType,reason);
				return 0;
			}
		}
		
		// determine all possible combinations of arcs. In most cases,
		// this will be 0 or one, but if the style creates multiple roads for one
		// OSM way, this can be a larger number
		int numCombis = 1;
		int [] indexes = new int[arcLists.size()];
		for (int i = 0; i < indexes.length; i++){
			List<RouteArc> arcs =  arcLists.get(i);
			numCombis *= arcs.size();
		}
		List<RouteArc> path = new ArrayList<>();
		int added = 0;
		for (int i = 0; i < numCombis; i++){
			for (RouteNode vn : viaNodes){
				path.clear();
				boolean viaNodeFound = false;
				int pathNoAccessMask = 0;
				for (int j = 0; j < indexes.length; j++){
					RouteArc arc = arcLists.get(j).get(indexes[j]);
					if (arc.getDest() == vn || viaNodeFound == false){
						arc = getReverseArc(arc);
						if (arc.getSource() == vn)
							viaNodeFound = true;
					}
					pathNoAccessMask |= arc.getRoadDef().getTabAAccess();
					path.add(arc);
				}
				if (isUsable(pathNoAccessMask, grr.getExceptionMask())){
					vn.addRestriction(new RouteRestriction(vn, path, grr.getExceptionMask()));
					++added;
				} 
			}
			// get next combination of arcs
			++indexes[indexes.length-1];
			for (int j = indexes.length-1; j > 0; --j){
				if (indexes[j] >= arcLists.get(j).size()){
					indexes[j] = 0;
					indexes[j-1]++;
				}
			}
		}

		// double check
		if (indexes[0] != arcLists.get(0).size())
			log.error(sourceDesc, " failed to generate all possible paths");
		log.info(sourceDesc, "added",added,"route restriction(s) to img file");
		return added;
	}

	/*
	 * Match the access mask of the road and the exception mask of the 
	 * restriction. If 
	 * TabA:
	 * 	0x8000, // emergency (net pointer bit 31)
		0x4000, // delivery (net pointer bit 30)
		0x0001, // car
		0x0002, // bus
		0x0004, // taxi
		0x0010, // foot
		0x0020, // bike
		0x0040, // truck

	public final static byte EXCEPT_CAR      = 0x01;
	public final static byte EXCEPT_BUS      = 0x02;
	public final static byte EXCEPT_TAXI     = 0x04;
	public final static byte EXCEPT_DELIVERY = 0x10;
	public final static byte EXCEPT_BICYCLE  = 0x20;
	public final static byte EXCEPT_TRUCK    = 0x40;
	// additional flags that can be passed via exceptMask  
	public final static byte EXCEPT_FOOT      = 0x08; // not written as such
	public final static byte EXCEPT_EMERGENCY = (byte)0x80; // not written as such
	

	 */
	 // TODO: rewrite using named constants, unit tests 
	private boolean isUsable(int roadTabAAccess, byte exceptionMask) {
		// bit positions for car,bus,taxi,bicycle and truck are equal
		// move the other bits to match the meaning in the restriction 
		int roadNoAccess = roadTabAAccess & 0x67;
		if ((roadTabAAccess & 0x8000) != 0)
			roadNoAccess |= RouteRestriction.EXCEPT_EMERGENCY;
		if ((roadTabAAccess & 0x4000) != 0)
			roadNoAccess |= RouteRestriction.EXCEPT_DELIVERY;
		if ((roadTabAAccess & 0x010) != 0)
			roadNoAccess |= RouteRestriction.EXCEPT_FOOT;
		int access = ~roadNoAccess & 0xff;
		int restrAccess = exceptionMask;
		restrAccess = ~restrAccess & 0xff;
		if ((access & restrAccess) == 0)
			return false; // no allowed vehicle is concerned by this restriction
		return true;
	}

	private int addNoThroughRoute(GeneralRouteRestriction grr) {
		assert grr.getViaNodes() != null;
		assert grr.getViaNodes().size() == 1;
		long viaId = grr.getViaNodes().get(0).getId();
		RouteNode vn = nodes.get(viaId);
		if (vn == null){
			log.error(grr.getSourceDesc(), "can't locate 'via' RouteNode with id", viaId);
			return 0;
		}
		int added = 0;
		
		for (RouteArc out: vn.arcsIteration()){
			if (!out.isDirect())
				continue;
			int pathNoAccessMask = out.getRoadDef().getTabAAccess();
			for (RouteArc in: vn.arcsIteration()){
				if (!in.isDirect() || in == out || in.getDest() == out.getDest())
					continue;
				pathNoAccessMask |= in.getRoadDef().getTabAAccess();
				if (isUsable(pathNoAccessMask, grr.getExceptionMask())){
					vn.addRestriction(new RouteRestriction(vn, Arrays.asList(in,out), grr.getExceptionMask()));
					added++;
				} else {
					if (log.isDebugEnabled())
						log.debug(grr.getSourceDesc(),"ignored no-through-route",in,"to",out);
				}
			}
		}
		return added;
	}
	
	public void addThroughRoute(long junctionNodeId, long roadIdA, long roadIdB) {
		RouteNode node = nodes.get(junctionNodeId);
		assert node != null :  "Can't find node with id " + junctionNodeId;

		node.addThroughRoute(roadIdA, roadIdB);
	}
	
	/**
	 * Calculate the "angle" between to arcs. The arcs may not be connected.
	 * We do this by "virtually" moving the toArc so that its source 
	 * node lies on the destination node of the from arc.
	 * This makes only sense if move is not over a large distance, we assume that this 
	 * is the case as via ways should be short. 
	 * @param fromArc arc with from node as source and first via node as destination
	 * @param toArc arc with last via node as source
	 * @return angle at in degree [-180;180]
	 */
	private float getAngle(RouteArc fromArc, RouteArc toArc){
		// note that the values do not depend on the isForward() attribute
		float headingFrom = fromArc.getFinalHeading();
		float headingTo = toArc.getInitialHeading();
		float angle = headingTo - headingFrom;
		while(angle > 180)
			angle -= 360;
		while(angle < -180)
			angle += 360;
		return angle;
	}
	
	private RouteArc getReverseArc(RouteArc arc){
		return arc.getDest().getDirectArcTo(arc.getSource(), arc.getRoadDef());
	}
		
	/**
	 * Find the angle that comes closer to the direction indicated.
	 * 
	 * @param angle1 1st angle -180:180 degrees
	 * @param angle2 2nd angle -180:180 degrees
	 * @param dirIndicator l:left, r:right, u:u_turn, s: straight_on
	 * @return
	 */
	private Integer getBetterAngle (Integer angle1, Integer angle2, char dirIndicator){
		switch (dirIndicator){
		case 'l':
			if (Math.abs(-90-angle2) < Math.abs(-90-angle1))
				return angle2; // closer to -90
			break;
		case 'r':
			if (Math.abs(90-angle2) < Math.abs(90-angle1))
				return angle2; // closer to 90
			break;
		case 'u': 
			double d1 = (angle1 < 0 ) ? -180-angle1 : 180-angle1; 
			double d2 = (angle2 < 0 ) ? -180-angle2 : 180-angle2; 
			if (Math.abs(d2) < Math.abs(d1))
				return angle2; // closer to -180
			break;
		case 's': 
			if (Math.abs(angle2) < Math.abs(angle1))
				return angle2; // closer to 0
			break;
		}
		
		return angle1;
	}
	
	/**
	 * Check if angle is in the range indicated by the direction
	 * @param angle the angle -180:180 degrees
	 * @param dirIndicator l:left, r:right, u:u_turn, s: straight_on 
	 * @return
	 */
	private boolean matchDirectionInfo (float angle, char dirIndicator){
		switch (dirIndicator){
		case 'l':
			if (angle < -3 && angle > - 177)
				return true;
			break;
		case 'r':
			if (angle > 3 && angle < 177)
				return true;
			break;
		case 'u':
			if (angle < -87 || angle > 93)
				return true;
			break;
		case 's':
			if (angle > -87 && angle < 87)
				return true;
			break;
		case '?':
			return true;
		}
		return false;
	}
	
	
}

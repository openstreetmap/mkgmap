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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	private final Map<Long, RouteNode> nodes = new LinkedHashMap<Long, RouteNode>();

	// boundary nodes
	// a node should be in here iff the nodes boundary flag is set
	private final List<RouteNode> boundary = new ArrayList<RouteNode>();
	//private final List<MapRoad> mapRoads = new ArrayList<MapRoad>();

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
	 * 
	 * @param grr the object that holds the details about the route restriction
	 */
	public int addRestriction(GeneralRouteRestriction grr) {
		if (grr.getType() == GeneralRouteRestriction.RestrType.TYPE_NO_TROUGH)
			return addNoThroughRoute(grr);
		String sourceDesc = grr.getSourceDesc();
		long fromId = grr.getFromNode().getId();
		long toId = grr.getToNode().getId();
		RouteNode fn = nodes.get(fromId);
		RouteNode tn = nodes.get(toId);
		if (fn == null  || tn == null){
			if (fn == null)
				log.error(sourceDesc, "can't locate 'from' RouteNode with id", fromId);
			if (tn == null)
				log.error(sourceDesc, "can't locate 'to' RouteNode with id", toId);
			return 0; 
		}
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
		RouteArc fa = firstViaNode.getDirectArcTo(fn, grr.getFromWayId()); // inverse arc gets used
		RouteArc ta = lastViaNode.getDirectArcTo(tn, grr.getToWayId());
		if (fa == null || ta == null){
			if (fa == null)
				log.error(sourceDesc, "can't locate arc from 'via' node ",firstViaId,"to 'from' node",fromId,"on way",grr.getFromWayId());
			if (ta == null)
				log.error(sourceDesc, "can't locate arc from 'via' node ",lastViaId,"to 'to' node",toId,"on way",grr.getToWayId());
			return 0;
		}

		List<RouteArc> viaArcs = new ArrayList<>();
		for (int i = 1; i < grr.getViaNodes().size(); i++){
			RouteNode vn = viaNodes.get(i-1);
			RouteArc va = vn.getDirectArcTo(viaNodes.get(i), grr.getViaWayIds().get(i-1));
			if (va == null){
				log.error(sourceDesc, "can't locate arc from 'via' node ",vn.getCoord().getId(),"to next 'via' node on way",grr.getViaWayIds().get(i));
				return 0;
			}
			if(!va.isForward() && va.getRoadDef().isOneway()) {
				// the route restriction via nodes connects to the "wrong" end of a oneway
				if ((grr.getExceptionMask() & RouteRestriction.EXCEPT_FOOT) != 0){
					// pedestrians are allowed
					log.warn(sourceDesc, "restriction via "+firstViaId + " to " + lastViaId + " ignored because via-arc is wrong direction in oneway ");
					return 0;
				} else {
					log.info(sourceDesc, "restriction via "+firstViaId + " to " + lastViaId + " added although via-arc is wrong direction in oneway, but restriction also excludes pedestrians.");
				}
			}
			viaArcs.add(va);
		}
		
		List<RouteArc> badArcs = new ArrayList<>();
		
		if (grr.getType() == GeneralRouteRestriction.RestrType.TYPE_NOT){
			if(!ta.isForward() && ta.getRoadDef().isOneway()) {
				// the route restriction connects to the "wrong" end of a oneway
				if ((grr.getExceptionMask() & RouteRestriction.EXCEPT_FOOT) != 0){
					// pedestrians are allowed
					log.warn(sourceDesc, "restriction via "+lastViaId + " to " + toId + " ignored because to-arc is wrong direction in oneway ");
					return 0;
				} else {
					log.info(sourceDesc, "restriction via "+lastViaId + " to " + toId + " added although to-arc is wrong direction in oneway, but restriction also excludes pedestrians.");
				}
			}
			badArcs.add(ta);
		}
		else if (grr.getType() == GeneralRouteRestriction.RestrType.TYPE_ONLY){
			// this is the inverse logic, rr gives the allowed path, we have to find the others
			RouteNode source = ta.getSource();
			assert source == lastViaNode;
			for (RouteArc badArc : lastViaNode.arcsIteration()){
				if (!badArc.isDirect() || badArc.getRoadDef().getId() == ta.getRoadDef().getId()) 
					continue;
				if (!badArc.isForward() && badArc.getRoadDef().isOneway()){
					// the route restriction connects to the "wrong" end of a oneway
					if ((grr.getExceptionMask() & RouteRestriction.EXCEPT_FOOT) != 0){
						// pedestrians are allowed
						continue;
					}
				}
				badArcs.add(badArc);
			}
			
		}
		int added = 0;
		if (fa.getDest() == ta.getDest()){
			log.error(sourceDesc, "check special case from = to arc ");
		}
		for (RouteArc badArc: badArcs){
			for (RouteNode vn: viaNodes){
				List<RouteArc> arcs = new ArrayList<>();
				arcs.add(fa);
				for (RouteArc va : viaArcs){
					if (va.getDest() != vn)
						arcs.add(va);
					else {
						RouteArc reverseArc = vn.getDirectArcTo(va.getSource(), va.getRoadDef().getId());
						arcs.add(reverseArc);
					}
				}
				arcs.add(badArc);
				vn.addRestriction(new RouteRestriction(vn, arcs, grr.getExceptionMask()));
				++added;
			}
		}
		return added;
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
			for (RouteArc in: vn.arcsIteration()){
				if (!in.isDirect() || in == out)
					continue;
				vn.addRestriction(new RouteRestriction(vn, Arrays.asList(in,out), grr.getExceptionMask()));
				added++;
			}
		}
		return added;
	}
	
	public void addThroughRoute(long junctionNodeId, long roadIdA, long roadIdB) {
		RouteNode node = nodes.get(junctionNodeId);
		assert node != null :  "Can't find node with id " + junctionNodeId;

		node.addThroughRoute(roadIdA, roadIdB);
	}

}

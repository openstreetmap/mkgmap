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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
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
	public static final int NO_CARPOOL = 8;
	public static final int NO_MAX = 9;

	private final Map<Long, RouteNode> nodes = new LinkedHashMap<Long, RouteNode>();

	// boundary nodes
	// a node should be in here iff the nodes boundary flag is set
	private final List<RouteNode> boundary = new ArrayList<RouteNode>();
	//private final List<MapRoad> mapRoads = new ArrayList<MapRoad>();

	private final List<RoadDef> roadDefs = new ArrayList<RoadDef>();
	private List<RouteCenter> centers = new ArrayList<RouteCenter>();
	private int adjustTurnHeadings = 0;
	private boolean checkRoundabouts;
	private boolean checkRoundaboutFlares;
	private int maxFlareLengthRatio = 0;
	private boolean reportSimilarArcs;
	private boolean outputCurveData;
	private int reportDeadEnds = 0;

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

		reportDeadEnds = props.getProperty("report-dead-ends", 1);

		reportSimilarArcs = props.getProperty("report-similar-arcs", false);

		outputCurveData = !props.getProperty("no-arc-curves", false);
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

				if(node1 == node2) {
					log.error("Road " + road.getRoadDef().getName() + " (OSM id " + road.getRoadDef().getId() + ") contains consecutive identical nodes - routing will be broken");
					log.error("  " + co.toOSMURL());
				}
				else if(arcLength == 0) {
					log.error("Road " + road.getRoadDef().getName() + " (OSM id " + road.getRoadDef().getId() + ") contains zero length arc");
					log.error("  " + co.toOSMURL());
				}

				Coord bearingPoint = coordList.get(lastIndex + 1);
				if(lastCoord.equals(bearingPoint)) {
					// bearing point is too close to last node to be
					// useful - try some more points
					for(int bi = lastIndex + 2; bi <= index; ++bi) {
						if(!lastCoord.equals(coordList.get(bi))) {
							bearingPoint = coordList.get(bi);
							break;
						}
					}
				}
				int forwardBearing = (int)lastCoord.bearingTo(bearingPoint);
				int inverseForwardBearing = (int)bearingPoint.bearingTo(lastCoord);

				bearingPoint = coordList.get(index - 1);
				if(co.equals(bearingPoint)) {
					// bearing point is too close to this node to be
					// useful - try some more points
					for(int bi = index - 2; bi > lastIndex; --bi) {
						if(!co.equals(coordList.get(bi))) {
							bearingPoint = coordList.get(bi);
							break;
						}
					}
				}
				int reverseBearing = (int)co.bearingTo(bearingPoint);
				int inverseReverseBearing = (int)bearingPoint.bearingTo(co);

				// Create forward arc from node1 to node2
				RouteArc arc = new RouteArc(road.getRoadDef(),
											node1,
											node2,
											forwardBearing,
											inverseReverseBearing,
											arcLength,
											outputCurveData,
											pointsHash);
				arc.setForward();
				node1.addArc(arc);
				node2.addIncomingArc(arc);

				// Create the reverse arc
				arc = new RouteArc(road.getRoadDef(),
								   node2, node1,
								   reverseBearing,
								   inverseForwardBearing,
								   arcLength,
								   outputCurveData,
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

		NOD1Part nod1 = new NOD1Part();

		for (RouteNode node : nodes.values()) {
			if(!node.isBoundary()) {
				if(checkRoundabouts)
					node.checkRoundabouts();
				if(checkRoundaboutFlares)
					node.checkRoundaboutFlares(maxFlareLengthRatio);
				if(reportSimilarArcs)
					node.reportSimilarArcs();
				if(reportDeadEnds != 0)
					node.reportDeadEnds(reportDeadEnds);
			}
			if(adjustTurnHeadings != 0)
				node.tweezeArcs(adjustTurnHeadings);
			nod1.addNode(node);
		}
		centers = nod1.subdivide();
	}

	public List<RouteCenter> getCenters() {
		if (centers.isEmpty())
			splitCenters();
		return centers;
	}

	/**
	 * Get the list of nodes on the boundary of the network.
	 *
	 * Currently empty.
	 */
	public List<RouteNode> getBoundary() {
		return boundary;
	}

	public void addRestriction(CoordNode fromNode, CoordNode toNode, CoordNode viaNode, byte exceptMask) {
		RouteNode fn = nodes.get(fromNode.getId());
		RouteNode tn = nodes.get(toNode.getId());
		RouteNode vn = nodes.get(viaNode.getId());

		assert fn != null : "can't locate 'from' RouteNode with id " + fromNode.getId();
		assert tn != null : "can't locate 'to' RouteNode with id " + toNode.getId();
		assert vn != null : "can't locate 'via' RouteNode with id " + viaNode.getId();

		RouteArc fa = vn.getArcTo(fn); // inverse arc gets used
		RouteArc ta = vn.getArcTo(tn);

		assert fa != null : "can't locate arc from 'via' node to 'from' node";
		assert ta != null : "can't locate arc from 'via' node to 'to' node";

		vn.addRestriction(new RouteRestriction(fa, ta, exceptMask));
    }

}

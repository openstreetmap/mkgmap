/*
 * Copyright (C) 2008
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
 * Create date: 07-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.log.Logger;

/**
 * A routing node with its connections to other nodes via roads.
 *
 * @author Steve Ratcliffe
 */
public class RouteNode implements Comparable<RouteNode> {
	private static final Logger log = Logger.getLogger(RouteNode.class);

	/*
	 * 1. instantiate
	 * 2. setCoord, addArc
	 *      arcs, coords set
	 * 3. write
	 *      node offsets set in all nodes
	 * 4. writeSecond
	 */

	// Values for the first flag byte at offset 1
	private static final int MAX_DEST_CLASS_MASK = 0x07;
	private static final int F_BOUNDARY = 0x08;
	private static final int F_RESTRICTIONS = 0x10;
	private static final int F_LARGE_OFFSETS = 0x20;
	private static final int F_ARCS = 0x40;
	// only used internally in mkgmap
	private static final int F_DISCARDED = 0x100; // node has been discarded

	private static final int MAX_MAIN_ROAD_HEADING_CHANGE = 120;
	private static final int MIN_DIFF_BETWEEN_OUTGOING_AND_OTHER_ARCS = 45;
	private static final int MIN_DIFF_BETWEEN_INCOMING_AND_OTHER_ARCS = 50;

	private int offsetNod1 = -1;

	// arcs from this node
	private final List<RouteArc> arcs = new ArrayList<RouteArc>(4);
	// restrictions at (via) this node
	private final List<RouteRestriction> restrictions = new ArrayList<RouteRestriction>();
	// arcs to this node
	private final List<RouteArc> incomingArcs = new ArrayList<RouteArc>(4);

	private int flags;

	private final CoordNode coord;
	private char latOff;
	private char lonOff;
	private List<RouteArc[]> throughRoutes;

	// contains the maximum of roads this node is on, written with the flags
	// field. It is also used for the calculation of the destination class on
	// arcs.
	private byte nodeClass;
	
	private byte nodeGroup = -1;

	public RouteNode(Coord coord) {
		this.coord = (CoordNode) coord;
		setBoundary(this.coord.getOnBoundary());
	}

	private boolean haveLargeOffsets() {
		return (flags & F_LARGE_OFFSETS) != 0;
	}

	protected void setBoundary(boolean b) {
		if (b)
			flags |= F_BOUNDARY;
		else
			flags &= (~F_BOUNDARY) & 0xff;
	}

	public boolean isBoundary() {
		return (flags & F_BOUNDARY) != 0;
	}

	public void addArc(RouteArc arc) {
		arcs.add(arc);
		byte cl = (byte) arc.getRoadDef().getRoadClass();
		if(log.isDebugEnabled())
			log.debug("adding arc", arc.getRoadDef(), cl);
		if (cl > nodeClass)
			nodeClass = cl;
		flags |= F_ARCS;
	}

	public void addIncomingArc(RouteArc arc) {
		incomingArcs.add(arc);
	}

	public void addRestriction(RouteRestriction restr) {
		restrictions.add(restr);
		flags |= F_RESTRICTIONS;
	}

	/**
	 * get all direct arcs to the given node and the given way id
	 * @param otherNode
	 * @param roadId
	 * @return
	 */
	public List<RouteArc> getDirectArcsTo(RouteNode otherNode, long roadId) {
		List<RouteArc> result = new ArrayList<>();
		for(RouteArc a : arcs){
			if(a.isDirect() && a.getDest() == otherNode){
				if(a.getRoadDef().getId() == roadId)
					result.add(a);
			}
		}
		return result;
	}

	/**
	 * get all direct arcs on a given way id  
	 * @param roadId
	 * @return
	 */
	public List<RouteArc> getDirectArcsOnWay(long roadId) {
		List<RouteArc> result = new ArrayList<>();
		for(RouteArc a : arcs){
			if(a.isDirect()){
				if(a.getRoadDef().getId() == roadId)
					result.add(a);
			}
		}
		return result;
	}
	
	/**
	 * Find arc to given node on given road. 
	 * @param otherNode
	 * @param roadDef
	 * @return
	 */
	public RouteArc getDirectArcTo(RouteNode otherNode, RoadDef roadDef) {
		for(RouteArc a : arcs){
			if(a.isDirect() && a.getDest() == otherNode){
				if(a.getRoadDef()== roadDef)
					return a;
			}
		}
		return null;
	}
	
	/**
	 * Provide an upper bound to the size (in bytes) that
	 * writing this node will take.
	 *
	 * Should be called only after arcs and restrictions
	 * have been set. The size of arcs depends on whether
	 * or not they are internal to the RoutingCenter.
	 */
	public int boundSize() {
		return 1 // table pointer
			+ 1 // flags
			+ 4 // assume large offsets required
			+ arcsSize()
			+ restrSize();
	}

	private int arcsSize() {
		int s = 0;
		for (RouteArc arc : arcs) {
			s += arc.boundSize();
		}
		return s;
	}

	private int restrSize() {
		return 2*restrictions.size();
	}

	/**
	 * Writes a nod1 entry.
	 */
	public void write(ImgFileWriter writer) {
		if(log.isDebugEnabled())
			log.debug("writing node, first pass, nod1", coord.getId());
		offsetNod1 = writer.position();
		assert offsetNod1 < 0x1000000 : "node offset doesn't fit in 3 bytes";

		assert (flags & F_DISCARDED) == 0 : "attempt to write discarded node";

		writer.put((byte) 0);  // will be overwritten later
		flags |= (nodeClass & MAX_DEST_CLASS_MASK); // max. road class of any outgoing road
		writer.put((byte) flags);

		if (haveLargeOffsets()) {
			writer.putInt((latOff << 16) | (lonOff & 0xffff));
		} else {
			writer.put3((latOff << 12) | (lonOff & 0xfff));
		}

		if (!arcs.isEmpty()) {
			boolean useCompactDirs = true;
			IntArrayList initialHeadings = new IntArrayList(arcs.size()+1);
			RouteArc lastArc = null;
			for (RouteArc arc: arcs){
				if (lastArc == null || lastArc.getIndexA() != arc.getIndexA() || lastArc.isForward() != arc.isForward()){
					int dir = RouteArc.directionFromDegrees(arc.getInitialHeading());
					dir = dir & 0xf0;
					if (initialHeadings.contains(dir)){
						useCompactDirs = false;
						break;
					}
					initialHeadings.add(dir);
				} else {
					// 
				}
				lastArc = arc;
			}
			initialHeadings.add(0); // add dummy 0 so that we don't have to check for existence
			arcs.get(arcs.size() - 1).setLast();
			lastArc = null;
			
			int index = 0;
			for (RouteArc arc: arcs){
				Byte compactedDir = null;
				if (useCompactDirs){
					if (lastArc == null || lastArc.getIndexA() != arc.getIndexA() || lastArc.isForward() != arc.isForward()){
						if (index % 2 == 0)
							compactedDir = (byte) ((initialHeadings.get(index) >> 4) | initialHeadings.getInt(index+1));
						index++;
					}
				}
				arc.write(writer, lastArc, useCompactDirs, compactedDir);
				lastArc = arc;
			}
		}

		if (!restrictions.isEmpty()) {
			restrictions.get(restrictions.size() - 1).setLast();
			for (RouteRestriction restr : restrictions)
				restr.writeOffset(writer);
		}
	}

	/**
	 * Writes a nod3 /nod4 entry.
	 */
	public void writeNod3OrNod4(ImgFileWriter writer) {
		assert isBoundary() : "trying to write nod3 for non-boundary node";

		writer.put3(coord.getLongitude());
		writer.put3(coord.getLatitude()); 
		writer.put3(offsetNod1);
	}

	public void discard() {
		// mark the node as having been discarded
		flags |= F_DISCARDED;
	}

	public int getOffsetNod1() {
		if((flags & F_DISCARDED) != 0) {
			// return something so that the program can continue
			return 0;
		}
		assert offsetNod1 != -1: "failed for node " + coord.getId() + " at " + coord.toDegreeString();
		return offsetNod1;
	}

	public void setOffsets(Coord centralPoint) {
		if(log.isDebugEnabled())
			log.debug("center", centralPoint, ", coord", coord.toDegreeString());
		setLatOff(coord.getLatitude() - centralPoint.getLatitude());
		setLonOff(coord.getLongitude() - centralPoint.getLongitude());
	}

	public Coord getCoord() {
		return coord;
	}

	private void checkOffSize(int off) {
		if (off > 0x7ff || off < -0x800)
			// does off fit in signed 12 bit quantity?
			flags |= F_LARGE_OFFSETS;
		// does off fit in signed 16 bit quantity?
		assert (off <= 0x7fff && off >= -0x8000);
	}

	private void setLatOff(int latOff) {
		if(log.isDebugEnabled())
			log.debug("lat off", Integer.toHexString(latOff));
		this.latOff = (char) latOff;
		checkOffSize(latOff);
	}

	private void setLonOff(int lonOff) {
		if(log.isDebugEnabled())
			log.debug("long off", Integer.toHexString(lonOff));
		this.lonOff = (char) lonOff;
		checkOffSize(lonOff);
	}

	/**
	 * Second pass over the nodes. Fill in pointers and Table A indices.
	 */
	public void writeSecond(ImgFileWriter writer) {
		for (RouteArc arc : arcs)
			arc.writeSecond(writer);
	}

	/**
	 * Return the node's class, which is the maximum of
	 * classes of the roads it's on.
	 */
	public int getNodeClass() {
		return nodeClass;
	}

	public Iterable<? extends RouteArc> arcsIteration() {
		return new Iterable<RouteArc>() {
			public Iterator<RouteArc> iterator() {
				return arcs.iterator();
			}
		};
	}

	public List<RouteRestriction> getRestrictions() {
		return restrictions;
	}

	public String toString() {
		return String.valueOf(coord.getId());
	}

	/*
	 * For sorting node entries in NOD 3.
	 */
	public int compareTo(RouteNode otherNode) {
		return coord.compareTo(otherNode.getCoord());
	}

	private static boolean possiblySameRoad(RouteArc raa, RouteArc rab) {

		RoadDef rda = raa.getRoadDef();
		RoadDef rdb = rab.getRoadDef();

		if(rda.getId() == rdb.getId()) {
			// roads have the same (OSM) id
			return true;
		}

		boolean bothArcsNamed = false;
		for(Label laba : rda.getLabels()) {
			if(laba != null && laba.getOffset() != 0) {
				for(Label labb : rdb.getLabels()) {
					if(labb != null && labb.getOffset() != 0) {
						bothArcsNamed = true;
						if(laba.equals(labb)) {
							// the roads have the same label
							if(rda.isLinkRoad() == rdb.isLinkRoad()) {
								// if both are a link road or both are
								// not a link road, consider them the
								// same road
								return true;
							}
							// One is a link road and the other isn't
							// so consider them different roads - this
							// is because people often give a link
							// road that's leaving some road the same
							// ref as that road but it suits us better
							// to consider them as different roads
							return false;
						}
					}
				}
			}
		}

		if(bothArcsNamed) {
			// both roads have names and they don't match
			return false;
		}

		// at least one road is unnamed
		if(rda.isRoundabout() && rdb.isRoundabout()) {
			// hopefully, segments of the same (unnamed) roundabout
			return true;
		}

		return false;
	}

	private static boolean rightTurnRequired(float inHeading, float outHeading, float otherHeading) {
		// given the headings of the incoming, outgoing and side
		// roads, decide whether a side road is to the left or the
		// right of the main road

		outHeading -= inHeading;
		while(outHeading < -180)
			outHeading += 360;
		while(outHeading > 180)
			outHeading -= 360;

		otherHeading -= inHeading;
		while(otherHeading < -180)
			otherHeading += 360;
		while(otherHeading > 180)
			otherHeading -= 360;

		return otherHeading > outHeading;
	}

	private static final int ATH_OUTGOING = 1;
	private static final int ATH_INCOMING = 2;

	public static final int ATH_DEFAULT_MASK = ATH_OUTGOING | ATH_INCOMING;

	public void tweezeArcs(int mask) {
		if(arcs.size() >= 3) {

			// detect the "shallow turn" scenario where at a junction
			// on some "main" road, the side road leaves the main
			// road at a very shallow angle and the GPS says "keep
			// right/left" when it would be better if it said "turn
			// right/left"

			// also helps to produce a turn instruction when the main
			// road bends sharply but the side road keeps close to the
			// original heading

			// the code tries to detect a pair of arcs (the "incoming"
			// arc and the "outgoing" arc) that are the "main road"
			// and the remaining arc (called the "other" arc) which is
			// the "side road"

			// having worked out the roles for the arcs, the heuristic
			// applied is that if the main road doesn't change its
			// heading by more than maxMainRoadHeadingChange, ensure
			// that the side road heading differs from the outgoing
			// heading by at least
			// minDiffBetweenOutgoingAndOtherArcs and the side road
			// heading differs from the incoming heading by at least
			// minDiffBetweenIncomingAndOtherArcs

			// list of outgoing arcs discovered at this node
			List<RouteArc> outgoingArcs = new ArrayList<RouteArc>();

			// sort incoming arcs by decreasing class/speed
			List<RouteArc> inArcs = new ArrayList<RouteArc>(incomingArcs);

			Collections.sort(inArcs, new Comparator<RouteArc>() {
					public int compare(RouteArc ra1, RouteArc ra2) {
						int c1 = ra1.getRoadDef().getRoadClass();
						int c2 = ra2.getRoadDef().getRoadClass();
						if(c1 == c2)
							return (ra2.getRoadDef().getRoadSpeed() - 
									ra1.getRoadDef().getRoadSpeed());
						return c2 - c1;
					}
				});

			// look at incoming arcs in order of decreasing class/speed
			for(RouteArc inArc : inArcs) {

				RoadDef inRoadDef = inArc.getRoadDef();

				if(!inArc.isForward() && inRoadDef.isOneway()) {
					// ignore reverse arc if road is oneway
					continue;
				}

				float inHeading = inArc.getFinalHeading();
				// determine the outgoing arc that is likely to be the
				// same road as the incoming arc
				RouteArc outArc = null;

				if(throughRoutes != null) {
					// through_route relations have the highest precedence
					for(RouteArc[] pair : throughRoutes) {
						if(pair[0] == inArc) {
							outArc = pair[1];
							log.info("Found through route from " + inRoadDef + " to " + outArc.getRoadDef());
							break;
						}
					}
				}

				if(outArc == null) {
					// next, if oa has the same RoadDef as inArc, it's
					// definitely the same road
					for(RouteArc oa : arcs) {
						if (oa.isDirect() == false)
							continue;
						if(oa.getDest() != inArc.getSource()) {
							// this arc is not going to the same node as
							// inArc came from
							if(oa.getRoadDef() == inRoadDef) {
								outArc = oa;
								break;
							}
						}
					}
				}

				if(outArc == null) {
					// next, although the RoadDefs don't match, use
					// possiblySameRoad() to see if the roads' id or
					// labels (names/refs) match
					for(RouteArc oa : arcs) {
						if (oa.isDirect() == false)
							continue;
						if(oa.getDest() != inArc.getSource()) {
							// this arc is not going to the same node as
							// inArc came from
							if((oa.isForward() || !oa.getRoadDef().isOneway()) &&
							   possiblySameRoad(inArc, oa)) {
								outArc = oa;
								break;
							}
						}
					}
				}

				if(false && outArc == null) {
					// last ditch attempt to find the outgoing arc -
					// try and find a single arc that has the same
					// road class and speed as the incoming arc
					int inArcClass = inArc.getRoadDef().getRoadClass();
					int inArcSpeed = inArc.getRoadDef().getRoadSpeed();
					for(RouteArc oa : arcs) {
						if(oa.getDest() != inArc.getSource() &&
						   oa.getRoadDef().getRoadClass() == inArcClass &&
						   oa.getRoadDef().getRoadSpeed() == inArcSpeed) {
							if(outArc != null) {
								// multiple arcs have the same road
								// class/speed as the incoming arc so
								// don't use any of them as the
								// outgoing arc
								outArc = null;
								break;
							}
							// oa has the same class/speed as inArc,
							// now check that oa is not part of
							// another road by matching names rather
							// than class/speed because they could be
							// different
							boolean paired = false;
							for(RouteArc z : arcs)
								if(z != oa && possiblySameRoad(z, oa))
									paired = true;
							if(!paired)
								outArc = oa;
						}
					}
					if(outArc != null)
						log.info("Matched outgoing arc " + outArc.getRoadDef() + " to " + inRoadDef + " using road class (" + inArcClass + ") and speed (" + inArcSpeed + ") at " + coord.toOSMURL()); 
				}

				// if we did not find the outgoing arc, give up with
				// this incoming arc
				if(outArc == null) {
					//log.info("Can't continue road " + inRoadDef + " at " + coord.toOSMURL());
					continue;
				}

				// remember that this arc is an outgoing arc
				outgoingArcs.add(outArc);

				float outHeading = outArc.getInitialHeading();
				float mainHeadingDelta = outHeading - inHeading;
				while(mainHeadingDelta > 180)
					mainHeadingDelta -= 360;
				while(mainHeadingDelta < -180)
					mainHeadingDelta += 360;
				//log.info(inRoadDef + " continues to " + outArc.getRoadDef() + " with a heading change of " + mainHeadingDelta + " at " + coord.toOSMURL());

				if(Math.abs(mainHeadingDelta) > MAX_MAIN_ROAD_HEADING_CHANGE) {
					// if the continuation road heading change is
					// greater than maxMainRoadHeadingChange don't
					// adjust anything
					continue;
				}

				for(RouteArc otherArc : arcs) {
					if (otherArc.isDirect() == false)
						continue;

					// for each other arc leaving this node, tweeze
					// its heading if its heading change from the
					// outgoing heading is less than
					// minDiffBetweenOutgoingAndOtherArcs or its
					// heading change from the incoming heading is
					// less than minDiffBetweenIncomingAndOtherArcs

					if(otherArc.getDest() == inArc.getSource() ||
					   otherArc == outArc) {
						// we're looking at the incoming or outgoing
						// arc, ignore it
						continue;
					}

					if(!otherArc.isForward() &&
					   otherArc.getRoadDef().isOneway()) {
						// ignore reverse arc if road is oneway
						continue;
					}

					if(inRoadDef.isLinkRoad() &&
					   otherArc.getRoadDef().isLinkRoad()) {
						// it's a link road leaving a link road so
						// leave the angle unchanged to avoid
						// introducing a time penalty by increasing
						// the angle (this stops the router using link
						// roads that "cut the corner" at roundabouts)
						continue;
					}

					if(outgoingArcs.contains(otherArc)) {
						// this arc was previously matched as an
						// outgoing arc so we don't want to change its
						// heading now
						continue;
					}

					float otherHeading = otherArc.getInitialHeading();
					float outToOtherDelta = otherHeading - outHeading;
					while(outToOtherDelta > 180)
						outToOtherDelta -= 360;
					while(outToOtherDelta < -180)
						outToOtherDelta += 360;
					float inToOtherDelta = otherHeading - inHeading;
					while(inToOtherDelta > 180)
						inToOtherDelta -= 360;
					while(inToOtherDelta < -180)
						inToOtherDelta += 360;

					float newHeading = otherHeading;
					if(rightTurnRequired(inHeading, outHeading, otherHeading)) {
						// side road to the right
						if((mask & ATH_OUTGOING) != 0 &&
						   Math.abs(outToOtherDelta) < MIN_DIFF_BETWEEN_OUTGOING_AND_OTHER_ARCS)
							newHeading = outHeading + MIN_DIFF_BETWEEN_OUTGOING_AND_OTHER_ARCS;
						if((mask & ATH_INCOMING) != 0 &&
						   Math.abs(inToOtherDelta) < MIN_DIFF_BETWEEN_INCOMING_AND_OTHER_ARCS) {
							float nh = inHeading + MIN_DIFF_BETWEEN_INCOMING_AND_OTHER_ARCS;
							if(nh > newHeading)
								newHeading = nh;
						}

						if(newHeading > 180)
							newHeading -= 360;
					}
					else {
						// side road to the left
						if((mask & ATH_OUTGOING) != 0 &&
						   Math.abs(outToOtherDelta) < MIN_DIFF_BETWEEN_OUTGOING_AND_OTHER_ARCS)
							newHeading = outHeading - MIN_DIFF_BETWEEN_OUTGOING_AND_OTHER_ARCS;
						if((mask & ATH_INCOMING) != 0 &&
						   Math.abs(inToOtherDelta) < MIN_DIFF_BETWEEN_INCOMING_AND_OTHER_ARCS) {
							float nh = inHeading - MIN_DIFF_BETWEEN_INCOMING_AND_OTHER_ARCS;
							if(nh < newHeading)
								newHeading = nh;
						}

						if(newHeading < -180)
							newHeading += 360;
					}
					if(Math.abs(newHeading - otherHeading) > 0.0000001) {
						otherArc.setInitialHeading(newHeading);
						log.info("Adjusting turn heading from " + otherHeading + " to " + newHeading + " at junction of " + inRoadDef + " and " + otherArc.getRoadDef() + " at " + coord.toOSMURL());
					}
				}
			}
		}
	}

	public void checkRoundabouts() {

		List<RouteArc> roundaboutArcs = new ArrayList<RouteArc>();

		for(RouteArc a : arcs) {
			// ignore ways that have been synthesised by mkgmap
			if(!a.getRoadDef().isSynthesised() && a.isDirect() && 
			   a.getRoadDef().isRoundabout()) {
				roundaboutArcs.add(a);
			}
		}
			
		if(arcs.size() > 1 && roundaboutArcs.size() == 1) {
			if(roundaboutArcs.get(0).isForward())
				log.warn("Roundabout " + roundaboutArcs.get(0).getRoadDef() + " starts at " + coord.toOSMURL());
			else
				log.warn("Roundabout " + roundaboutArcs.get(0).getRoadDef() + " ends at " + coord.toOSMURL());
		}

		if(roundaboutArcs.size() > 2) {
			for(RouteArc fa : arcs) {
				if(fa.isForward() && fa.isDirect()) {
					RoadDef rd = fa.getRoadDef();
					for(RouteArc fb : arcs) {
						if(fb != fa && fb.isDirect() && 
						   fa.getPointsHash() == fb.getPointsHash() &&
						   ((fb.isForward() && fb.getDest() == fa.getDest()) ||
							(!fb.isForward() && fb.getSource() == fa.getDest()))) {
							if(!rd.messagePreviouslyIssued("roundabout forks/overlaps")) {
								log.warn("Roundabout " + rd + " overlaps " + fb.getRoadDef() + " at " + coord.toOSMURL());
							}
						}
						else if(fa != fb && fb.isForward()) {
							if(!rd.messagePreviouslyIssued("roundabout forks/overlaps")) {
								log.warn("Roundabout " + rd + " forks at " + coord.toOSMURL());
							}
						}
					}
				}
			}
		}
	}

	// determine "distance" between two nodes on a roundabout
	private int roundaboutSegmentLength(final RouteNode n1, final RouteNode n2) {
		List<RouteNode> seen = new ArrayList<RouteNode>();
		int len = 0;
		RouteNode n = n1;
		boolean checkMoreLinks = true;
		while(checkMoreLinks && !seen.contains(n)) {
			checkMoreLinks = false;
			seen.add(n);
			for(RouteArc a : n.arcs) {
				if(a.isForward() &&
				   a.getRoadDef().isRoundabout() &&
				   !a.getRoadDef().isSynthesised()) {
					len += a.getLength();
					n = a.getDest();
					if(n == n2)
						return len;
					checkMoreLinks = true;
					break;
				}
			}
		}
		// didn't find n2
		return Integer.MAX_VALUE;
	}

	// sanity check roundabout flare roads - the flare roads connect a
	// two-way road to a roundabout using short one-way segments so
	// the resulting sub-junction looks like a triangle with two
	// corners of the triangle being attached to the roundabout and
	// the last corner being connected to the two-way road

	public void checkRoundaboutFlares(int maxFlareLengthRatio) {
		for(RouteArc r : arcs) {
			// see if node has a forward arc that is part of a
			// roundabout
			if(!r.isForward() || !r.isDirect() || !r.getRoadDef().isRoundabout() || r.getRoadDef().isSynthesised())
				continue;

			// follow the arc to find the first node that connects the
			// roundabout to a non-roundabout segment
			RouteNode nb = r.getDest();
			List<RouteNode> seen = new ArrayList<RouteNode>();
			seen.add(this);

			while (true) {

				if (seen.contains(nb)) {
					// looped - give up
					nb = null;
					break;
				}

				// remember we have seen this node
				seen.add(nb);

				boolean connectsToNonRoundaboutSegment = false;
				RouteArc nextRoundaboutArc = null;
				for (RouteArc nba : nb.arcs) {
					if (nba.isDirect() == false)
						continue;
					if (!nba.getRoadDef().isSynthesised()) {
						if (nba.getRoadDef().isRoundabout()) {
							if (nba.isForward())
								nextRoundaboutArc = nba;
						} else
							connectsToNonRoundaboutSegment = true;
					}
				}

				if (connectsToNonRoundaboutSegment) {
					// great, that's what we're looking for
					break;
				}

				if (nextRoundaboutArc == null) {
					// not so good, the roundabout stops in mid air?
					nb = null;
					break;
				}

				nb = nextRoundaboutArc.getDest();
			}

			if(nb == null) {
				// something is not right so give up
				continue;
			}

			// now try and find the two arcs that make up the
			// triangular "flare" connected to both ends of the
			// roundabout segment
			for(RouteArc fa : arcs) {
				if(!fa.isDirect() || !fa.getRoadDef().doFlareCheck())
					continue;

				for(RouteArc fb : nb.arcs) {
					if(!fb.isDirect() || !fb.getRoadDef().doFlareCheck())
						continue;
					if(fa.getDest() == fb.getDest()) {
						// found the 3rd point of the triangle that
						// should be connecting the two flare roads

						// first, special test required to cope with
						// roundabouts that have a single flare and no
						// other connections - only check the flare
						// for the shorter of the two roundabout
						// segments

						if(roundaboutSegmentLength(this, nb) >=
						   roundaboutSegmentLength(nb, this))
							continue;

						if(maxFlareLengthRatio > 0) {
							// if both of the flare roads are much
							// longer than the length of the
							// roundabout segment, they are probably
							// not flare roads at all but just two
							// roads that meet up - so ignore them
							final int maxFlareLength = roundaboutSegmentLength(this, nb) * maxFlareLengthRatio;
							if(maxFlareLength > 0 &&
							   fa.getLength() > maxFlareLength &&
							   fb.getLength() > maxFlareLength) {
								continue;
							}
						}

						// now check the flare roads for direction and
						// oneway

						// only issue one warning per flare
						if(!fa.isForward())
							log.warn("Outgoing roundabout flare road " + fa.getRoadDef() + " points in wrong direction? " + fa.getSource().coord.toOSMURL());
						else if(fb.isForward())
							log.warn("Incoming roundabout flare road " + fb.getRoadDef() + " points in wrong direction? " + fb.getSource().coord.toOSMURL());
						else if(!fa.getRoadDef().isOneway())
							log.warn("Outgoing roundabout flare road " + fa.getRoadDef() + " is not oneway? " + fa.getSource().coord.toOSMURL());

						else if(!fb.getRoadDef().isOneway())
							log.warn("Incoming roundabout flare road " + fb.getRoadDef() + " is not oneway? " + fb.getDest().coord.toOSMURL());
						else {
							// check that the flare road arcs are not
							// part of a longer way
							for(RouteArc a : fa.getDest().arcs) {
								if(a.isDirect() && a.getDest() != this && a.getDest() != nb) {
									if(a.getRoadDef() == fa.getRoadDef())
										log.warn("Outgoing roundabout flare road " + fb.getRoadDef() + " does not finish at flare? " + fa.getDest().coord.toOSMURL());
									else if(a.getRoadDef() == fb.getRoadDef())
										log.warn("Incoming roundabout flare road " + fb.getRoadDef() + " does not start at flare? " + fb.getDest().coord.toOSMURL());
								}
							}
						}
					}
				}
			}
		}
	}

	public void reportSimilarArcs() {
		for(int i = 0; i < arcs.size(); ++i) {
			RouteArc arci = arcs.get(i);
			for(int j = i + 1; j < arcs.size(); ++j) {
				RouteArc arcj = arcs.get(j);
				if(arci.getDest() == arcj.getDest() &&
				   arci.getLength() == arcj.getLength() &&
				   arci.getPointsHash() == arcj.getPointsHash()) {
					log.warn("Similar arcs (" + arci.getRoadDef() + " and " + arcj.getRoadDef() + ") from " + coord.toOSMURL());
				}
			}
		}
	}

	public void addThroughRoute(long roadIdA, long roadIdB) {
		if(throughRoutes == null)
			throughRoutes = new ArrayList<RouteArc[]>();
		boolean success = false;
		for(RouteArc arc1 : incomingArcs) {
			if(arc1.getRoadDef().getId() == roadIdA) {
				for(RouteArc arc2 : arcs) {
					if(arc2.getRoadDef().getId() == roadIdB) {
						throughRoutes.add(new RouteArc[] { arc1, arc2 });
						success = true;
						break;
					}
				}
			}
			else if(arc1.getRoadDef().getId() == roadIdB) {
				for(RouteArc arc2 : arcs) {
					if(arc2.getRoadDef().getId() == roadIdA) {
						throughRoutes.add(new RouteArc[] { arc1, arc2 });
						success = true;
						break;
					}
				}
			}
		}
		if(success)
			log.info("Added through route between ways " + roadIdA + " and " + roadIdB + " at " + coord.toOSMURL());
		else
			log.warn("Failed to add through route between ways " + roadIdA + " and " + roadIdB + " at " + coord.toOSMURL() + " - perhaps they don't meet here?");
	}

	/**
	 * For each arc on the road, check if we can add indirect arcs to 
	 * other nodes of the same road. This is done if the other node
	 * lies on a different road with a higher road class than the
	 * highest other road of the target node of the arc. We do this
	 * for both forward and reverse arcs. Multiple indirect arcs
	 * may be added for each Node. An indirect arc will 
	 * always point to a higher road than the previous arc.
	 * The length and direct bearing of the additional arc is measured 
	 * from the target node of the preceding arc to the new target node.
	 * The initial bearing doesn't really matter as it is not written
	 * for indirect arcs.  
	 * @param road
	 * @param maxRoadClass
	 */
	public void addArcsToMajorRoads(RoadDef road){
		assert road.getNode() == this;
		RouteNode current = this;
		// the nodes of this road
		List<RouteNode> nodes = new ArrayList<>();
		// the forward arcs of this road
		List<RouteArc> forwardArcs = new ArrayList<>();
		// will contain the highest other road of each node 
		IntArrayList forwardArcPositions = new IntArrayList();
		List<RouteArc> reverseArcs = new ArrayList<>();
		IntArrayList reverseArcPositions = new IntArrayList();

		// collect the nodes of the road and remember the arcs between them
		nodes.add(current);
		while (current != null){
			RouteNode next = null;
			for (int i = 0; i < current.arcs.size(); i++){
				RouteArc arc = current.arcs.get(i);
				if (arc.getRoadDef() == road){
					if (arc.isDirect()){
						if (arc.isForward()){
							next = arc.getDest();
							nodes.add(next);
							forwardArcs.add(arc);
							forwardArcPositions.add(i);
						} else {
							reverseArcPositions.add(i);
							reverseArcs.add(arc);
						}
					}
				} 
			}
			current = next;
		}
		
		if (nodes.size() < 3)
			return;
//		System.out.println(road + " " + nodes.size() + " " + forwardArcs.size());
		ArrayList<RouteArc> newArcs = new ArrayList<>(); 
		IntArrayList arcPositions = forwardArcPositions;
		List<RouteArc> roadArcs = forwardArcs;
		for (int dir = 0; dir < 2; dir++){
			// forward arcs first
			for (int i = 0; i + 2 < nodes.size(); i++){
				RouteNode sourceNode = nodes.get(i); // original source node of direct arc
				RouteNode stepNode = nodes.get(i+1); 
				RouteArc arcToStepNode = roadArcs.get(i);
				assert arcToStepNode.getDest() == stepNode;
				int currentClass = arcToStepNode.getArcDestClass();
				int finalClass = road.getRoadClass();
				if (finalClass <= currentClass)
					continue;
				newArcs.clear();
				double partialArcLength = 0;
				double pathLength = arcToStepNode.getLengthInMeter();
				for (int j = i+2; j < nodes.size(); j++){
					RouteArc arcToDest = roadArcs.get(j-1);
					partialArcLength += arcToDest.getLengthInMeter();
					pathLength += arcToDest.getLengthInMeter();
					int cl = nodes.get(j).getGroup();
					if (cl > currentClass){
						if (cl > finalClass)
							cl = finalClass;
						currentClass = cl;
						// create indirect arc from node i+1 to node j
						RouteNode destNode = nodes.get(j);
						Coord c1 = sourceNode.getCoord();
						Coord c2 = destNode.getCoord();
						RouteArc newArc = new RouteArc(road, 
								sourceNode, 
								destNode, 
								roadArcs.get(i).getInitialHeading(), // not used
								arcToDest.getFinalHeading(),  // not used
								c1.bearingTo(c2),
								partialArcLength, // from stepNode to destNode on road
								pathLength, // from sourceNode to destNode on road
								c1.distance(c2), 
								c1.hashCode() + c2.hashCode());
						if (arcToStepNode.isDirect())
							arcToStepNode.setMaxDestClass(0);
						else 
							newArc.setMaxDestClass(cl);
						if (dir == 0)
							newArc.setForward();
						newArc.setIndirect();
						newArcs.add(newArc);
						arcToStepNode = newArc;
						stepNode = destNode;
						
						partialArcLength = 0;
						if (cl >= finalClass)
							break;
					}
				}
				if (newArcs.isEmpty() == false){
					int directArcPos =  arcPositions.getInt(i);
					assert nodes.get(i).arcs.get(directArcPos).isDirect();
					assert nodes.get(i).arcs.get(directArcPos).getRoadDef() == newArcs.get(0).getRoadDef();
					assert nodes.get(i).arcs.get(directArcPos).isForward() == newArcs.get(0).isForward();
					nodes.get(i).arcs.addAll(directArcPos + 1, newArcs);
					if (dir == 0 && i > 0){
						// check if the inserted arcs change the position of the direct reverse arc
						int reverseArcPos = reverseArcPositions.get(i-1); // i-1 because first node doesn't have reverse arc 
						if (directArcPos < reverseArcPos)
							reverseArcPositions.set(i - 1, reverseArcPos + newArcs.size());
							
					}
				}
			}
			if (dir > 0)
				break;
			// reverse the arrays for the other direction
			Collections.reverse(reverseArcs);
			Collections.reverse(reverseArcPositions);
			Collections.reverse(nodes);
			arcPositions = reverseArcPositions;
			roadArcs = reverseArcs;
		}
	}

	/**
	 * Find the class group of the node. Rules:
	 * 1. Find the highest class which is used more than once.
	 * 2. Otherwise: use the class if the only one, or else the n-1 class.
     * (eg: if [1,] then use 1, if [1,2,] then use 1, if [1,2,3,] then 	use 2.
 	 * 
	 * @return the class group
	 */
	public int getGroup() {
		if (nodeGroup < 0){
			HashSet<RoadDef> roads = new HashSet<>();
			for (RouteArc arc: arcs){
				roads.add(arc.getRoadDef());
			}
			int[] classes = new int[5];
			int numClasses = 0;
			// find highest class that is used more than once
			for (RoadDef road: roads){
				int cl = road.getRoadClass();
				int n = ++classes[cl];
				if (n == 1)
					numClasses++;
				else if (n > 1 && cl > nodeGroup)
					nodeGroup = (byte) cl;
			}
			if (nodeGroup >= 0)
				return nodeGroup;
			if (numClasses == 1)
				nodeGroup = nodeClass; // only one class
			else {
				// find n-1 class 
				int n = 0;
				for (int cl = 4; cl >= 0; cl--){
					if (classes[cl] > 0){
						if (n == 1){
							nodeGroup = (byte) cl;
							break;
						}
						n++;
					}
				}
			}
		}
		return nodeGroup;
	}

	public List<RouteArc> getArcs() {
		return arcs;
	}

}

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

import java.util.ArrayList;
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
	private static final int F_BOUNDARY = 0x08;
	private static final int F_RESTRICTIONS = 0x10;
	private static final int F_LARGE_OFFSETS = 0x20;
	private static final int F_ARCS = 0x40;
	private static final int F_UNK_NEEDED = 0x04; // XXX
	// only used internally in mkgmap
	private static final int F_DISCARDED = 0x100; // node has been discarded

	private int offsetNod1 = -1;

	@Deprecated
	private final int nodeId; // XXX not needed at this point?

	// arcs from this node
	private final List<RouteArc> arcs = new ArrayList<RouteArc>();
	// restrictions at (via) this node
	private final List<RouteRestriction> restrictions = new ArrayList<RouteRestriction>();
	// arcs to this node
	private final List<RouteArc> incomingArcs = new ArrayList<RouteArc>();

	private int flags = F_UNK_NEEDED;

	private final CoordNode coord;
	private char latOff;
	private char lonOff;

	// this is for setting destination class on arcs
	// we're taking the maximum of roads this node is
	// on for now -- unsure of precise mechanic
	private int nodeClass;

	@Deprecated
	private static int nodeCount;

	@Deprecated
	public RouteNode(Coord coord) {
		this.coord = (CoordNode) coord;
		nodeId = nodeCount++; // XXX: take coord.getId() instead?
		setBoundary(this.coord.isBoundary());
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
		if (!arcs.isEmpty())
			arc.setNewDir();
		arcs.add(arc);
		int cl = arc.getRoadDef().getRoadClass();
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

	public RouteArc getArcTo(RouteNode otherNode) {
		for(RouteArc a : arcs)
			if(a.getDest() == otherNode)
				return a;

		return null;
	}

	public RouteArc getArcFrom(RouteNode otherNode) {
		for(RouteArc a : arcs)
			if(a.getSource() == otherNode)
				return a;

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
			log.debug("writing node, first pass, nod1", nodeId);
		offsetNod1 = writer.position();
		assert offsetNod1 < 0x1000000 : "node offset doesn't fit in 3 bytes";

		assert (flags & F_DISCARDED) == 0 : "attempt to write discarded node";

		writer.put((byte) 0);  // will be overwritten later
		writer.put((byte) flags);

		if (haveLargeOffsets()) {
			writer.putInt((latOff << 16) | (lonOff & 0xffff));
		} else {
			writer.put3((latOff << 12) | (lonOff & 0xfff));
		}

		if (!arcs.isEmpty()) {
			arcs.get(arcs.size() - 1).setLast();
			for (RouteArc arc : arcs)
				arc.write(writer);
		}

		if (!restrictions.isEmpty()) {
			restrictions.get(restrictions.size() - 1).setLast();
			for (RouteRestriction restr : restrictions)
				restr.writeOffset(writer);
		}
	}

	/**
	 * Writes a nod3 entry.
	 */
	public void writeNod3(ImgFileWriter writer) {
		assert isBoundary() : "trying to write nod3 for non-boundary node";

		writer.put3(coord.getLongitude());
		writer.put3(coord.getLatitude() + 0x800000); // + 180 degrees
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
		assert offsetNod1 != -1: "failed for node " + nodeId + " at " + coord.toDegreeString();
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
		return nodeId + "";
	}

	/*
	 * For sorting node entries in NOD 3.
	 */
	public int compareTo(RouteNode otherNode) {
		return coord.compareTo(otherNode.getCoord());
	}

	private static boolean possiblySameRoad(RouteArc raa, RouteArc rab) {

		if(raa == rab) {
			// the arcs are the same object
			return true;
		}

		RoadDef rda = raa.getRoadDef();
		RoadDef rdb = rab.getRoadDef();
		if(rda == rdb) {
			// the arcs share the same RoadDef
			return true;
		}

		boolean bothArcsNamed = false;
		for(Label laba : rda.getLabels()) {
			if(laba != null && laba.getOffset() != 0) {
				for(Label labb : rdb.getLabels()) {
					if(labb != null && labb.getOffset() != 0) {
						bothArcsNamed = true;
						if(laba.equals(labb)) {
							// the roads have the same name
							if(rda.isLinkRoad() == rdb.isLinkRoad()) {
								// if both are a link road or both are
								// not a link road, consider them the
								// same road
								return true;
							}
							// one's a link road and the other isn't
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

	public void tweezeArcs() {
		if(arcs.size() >= 3) {
			final int maxMainRoadHeadingChange = 120;
			final int minSideRoadHeadingChange = 45; // min change to be a "turn"

			// detect the "shallow turn" scenario where at a junction
			// on some road, the side road leaves the original road at
			// a very shallow angle and the GPS says "keep right/left"
			// when it would be better if it said "turn right/left"

			// the code tries to detect a pair of arcs (the "incoming"
			// arc and the "continuing" arc) that are the "main road"
			// and the remaining arc (called the "other" arc) which is
			// the "side road"

			// having worked out the roles for the arcs, the heuristic
			// applied is that if the main road doesn't change its
			// heading by more than maxMainRoadHeadingChange, ensure
			// that the side road heading differs from the continuing
			// heading by at least minSideRoadHeadingChange

			for(RouteArc inArc : incomingArcs) {

				int inHeading = inArc.getFinalHeading();
				// determine the outgoing (continuing) arc that is
				// likely to be the same road as the incoming arc
				RouteArc continuingArc = null;
				for(RouteArc ca : arcs) {
					if(ca.getDest() != inArc.getSource()) {
						// this arc is not going to the same node as
						// inArc came from
						if(possiblySameRoad(inArc, ca)) {
							continuingArc = ca;
							break;
						}
					}
				}
				// if we did not find the continuing arc, give up with
				// this incoming arc
				if(continuingArc == null) {
					//log.info("Can't continue road " + inArc.getRoadDef() + " at " + coord.toOSMURL());
					continue;
				}
				int continuingHeading = continuingArc.getInitialHeading();
				int mainHeadingDelta = continuingHeading - inHeading;
				while(mainHeadingDelta > 180)
					mainHeadingDelta -= 360;
				while(mainHeadingDelta < -180)
					mainHeadingDelta += 360;
				//log.info(inArc.getRoadDef() + " continues to " + continuingArc.getRoadDef() + " with a heading change of " + mainHeadingDelta + " at " + coord.toOSMURL());

				if(Math.abs(mainHeadingDelta) > maxMainRoadHeadingChange) {
					// if the continuation road heading change is
					// greater than maxMainRoadHeadingChange don't
					// adjust anything
					continue;
				}

				for(RouteArc otherArc : arcs) {

					// for each other arc leaving this node, tweeze
					// its heading if its heading change from the
					// continuation heading is less than
					// minSideRoadHeadingChange

					if(otherArc.getDest() == inArc.getSource() ||
					   otherArc == continuingArc) {
						// we're looking at the incoming or continuing
						// arc, ignore it
						continue;
					}

					if(possiblySameRoad(inArc, otherArc) ||
					   possiblySameRoad(continuingArc, otherArc)) {
						// not obviously a different road so give up
						continue;
					}

					if(inArc.getRoadDef().isLinkRoad() &&
					   otherArc.getRoadDef().isLinkRoad()) {
						// it's a link road leaving a link road so
						// leave the angle unchanged to avoid
						// introducing a time penalty by increasing
						// the angle (this stops the router using link
						// roads that "cut the corner" at roundabouts)
						continue;
					}

					int outHeading = otherArc.getInitialHeading();
					int outHeadingDelta = outHeading - continuingHeading;
					while(outHeadingDelta > 180)
						outHeadingDelta -= 360;
					while(outHeadingDelta < -180)
						outHeadingDelta += 360;
						//						log.warn("Found turn ("+ outHeadingDelta + " deg) from " + inArc.getRoadDef() + " to " + otherArc.getRoadDef() + " at " + coord.toOSMURL());
					if(outHeadingDelta > 0 &&
					   outHeadingDelta < minSideRoadHeadingChange) {
						int newHeading = continuingHeading + minSideRoadHeadingChange;
						if(newHeading > 180)
							newHeading -= 360;
						otherArc.setInitialHeading(newHeading);
						log.info("Adjusting turn heading from " + outHeading + " to " + newHeading + " at junction of " + inArc.getRoadDef() + " and " + otherArc.getRoadDef() + " at " + coord.toOSMURL());
					}
					else if(outHeadingDelta < 0 &&
							outHeadingDelta > -minSideRoadHeadingChange) {
						int newHeading = continuingHeading - minSideRoadHeadingChange;

						if(newHeading < -180)
							newHeading += 360;
						otherArc.setInitialHeading(newHeading);
						log.info("Adjusting turn heading from " + outHeading + " to " + newHeading + " at junction of " + inArc.getRoadDef() + " and " + otherArc.getRoadDef() + " at " + coord.toOSMURL());
					}
				}
			}
		}
	}

	public void checkRoundabouts() {

		List<RouteArc> roundaboutArcs = new ArrayList<RouteArc>();

		for(RouteArc a : arcs) {
			// ignore ways that have been synthesised by mkgmap
			if(!a.getRoadDef().isSynthesised() &&
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
				if(fa.isForward()) {
					RoadDef rd = fa.getRoadDef();
					for(RouteArc fb : arcs) {
						if(fb != fa &&
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

	// sanity check roundabout flare roads - the flare roads connect a
	// two-way road to a roundabout using short one-way segments so
	// the resulting sub-junction looks like a triangle with two
	// corners of the triangle being attached to the roundabout and
	// the last corner being connected to the two-way road

	public void checkRoundaboutFlares(int maxFlareLengthRatio) {
		for(RouteArc r : arcs) {
			// see if node has a forward arc that is part of a
			// roundabout
			if(!r.isForward() || !r.getRoadDef().isRoundabout())
				continue;
			// now try and find the two arcs that make up the
			// triangular "flare" connected to both ends of r
			RouteNode nb = r.getDest();
			for(RouteArc fa : arcs) {
				if(fa.getRoadDef().isRoundabout())
					continue;
				for(RouteArc fb : nb.arcs) {
					if(fb.getRoadDef().isRoundabout())
						continue;
					if(fa.getDest() == fb.getDest()) {
						// found the 3rd point of the triangle that
						// should be connecting the two flare roads

						// first, special test for roundabouts that
						// have a single flare and no other
						// connections - only check the flare for the
						// shorter of the two roundabout segments

						boolean isShortest = true;
						for(RouteArc rb : nb.arcs) {
							if(rb.getDest() == this &&
							   rb.isForward() &&
							   rb.getRoadDef().isRoundabout()) {
								isShortest = r.getLength() < rb.getLength();
								break;
							}
						}

						if(!isShortest)
							continue;

						if(maxFlareLengthRatio > 0) {
							// if both of the flare roads are much
							// longer than the length of r, they are
							// probably not flare roads at all but
							// just two roads that meet up - so ignore
							// them
							final int maxFlareLength = r.getLength() * maxFlareLengthRatio;
							if(r.getLength() > 0 &&
							   fa.getLength() > maxFlareLength &&
							   fb.getLength() > maxFlareLength) {
								continue;
							}
						}

						// now check the flare roads for direction and
						// onewayness

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
								if(a.getDest() != this && a.getDest() != nb) {
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

	public void reportDeadEnds(int level) {

		if(level > 0) {
			boolean noWayOut = true;
			boolean noWayIn = true;
			int nonSynthesisedArcs = 0;

			for(RouteArc a : arcs) {

				if(!a.getRoadDef().isSynthesised()) {

					++nonSynthesisedArcs;

					boolean oneway = a.getRoadDef().isOneway() || a.getRoadDef().isRoundabout();

					if(!oneway)
						noWayOut = noWayIn = false;

					if(a.isForward())
						noWayOut = false;
					else
						noWayIn = false;
				}
			}

			if(nonSynthesisedArcs == 0) {
				// ignore junctions that join only ways that have been
				// synthesised by mkgmap
				return;
			}

			if(noWayIn) {
				if(nonSynthesisedArcs == 1) {
					if(level > 1)
						log.warn("Oneway road " + arcs.get(0).getRoadDef() + " comes from nowhere at " + coord.toOSMURL());
				}
				else {
					String roads = null;
					for(RouteArc a : arcs) {
						if(a.getRoadDef().isSynthesised()) {
							// ignore ways that have been synthesised by
							// mkgmap
						}
						else if(roads == null)
							roads = "" + a.getRoadDef();
						else
							roads += ", " + a.getRoadDef();
					}
					log.warn("Oneway roads " + roads + " come from nowhere at " + coord.toOSMURL());
				}
			}

			if(noWayOut) {
				if(nonSynthesisedArcs == 1) {
					if(level > 1)
						log.warn("Oneway road " + arcs.get(0).getRoadDef() + " goes nowhere at " + coord.toOSMURL());
				}
				else {
					String roads = null;
					for(RouteArc a : arcs) {
						if(a.getRoadDef().isSynthesised()) {
							// ignore ways that have been synthesised by
							// mkgmap
						}
						else if(roads == null)
							roads = "" + a.getRoadDef();
						else
							roads += ", " + a.getRoadDef();
					}
					log.warn("Oneway roads " + roads + " go nowhere at " + coord.toOSMURL());
				}
			}
		}
	}
}

/*
 * Copyright (C) 2012
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

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.build.LocatorUtil;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Copies the destination tag from motorway_link and trunk_link ways to the 
 * first adjacent non link way so that the Garmin is able to display a valid
 * destination.
 * @author WanMil
 */
public class LinkDestinationHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(LinkDestinationHook.class);

	private ElementSaver saver;

	/** Maps which ways can be driven from a given Coord */
	private IdentityHashMap<Coord, Set<Way>> adjacentWays = new IdentityHashMap<Coord, Set<Way>>();
	/** Contains all _link ways that have to be processed */
	private Queue<Way> destinationLinkWays = new ArrayDeque<Way>();

	private HashSet<String> tagValues = new HashSet<String>(Arrays.asList(
			"motorway_link", "trunk_link"));

	private List<String> nameTags;

	/** Maps which nodes contains to which ways */ 
	private IdentityHashMap<Coord, Set<Way>> wayNodes = new IdentityHashMap<Coord, Set<Way>>();
	private boolean onlyMotorwayExitHint;
	
	private boolean processDestinations;
	private boolean processExits;
	
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		nameTags = LocatorUtil.getNameTags(props);
		onlyMotorwayExitHint = props.containsKey("all-exit-hints") == false;
		processDestinations = props.containsKey("process-destination");
		processExits = props.containsKey("process-exits");
		return processDestinations || processExits;
	}

	/**
	 * Fills the internal lists with the 
	 */
	private void retrieveWays() {
		// collect all ways tagged with highway 
		for (Way w : saver.getWays().values()) {
			if (w.getPoints().size() < 2) {
				// ignore one-node or zero-node ways
				continue;
			}
			String highwayTag = w.getTag("highway");
			if (highwayTag != null) {
				// the points of the way are kept so that it is easy to get
				// the adjacent ways for a given _link way
				List<Coord> points;
				if (isOnewayInDirection(w)) {
					// oneway => don't need the last point because the
					// way cannot be driven standing at the last point
					points = w.getPoints().subList(0, w.getPoints().size() - 1);
				} else if (isOnewayOppositeDirection(w)) {
					// reverse oneway => don't need the first point because the
					// way cannot be driven standing at the first point
					points = w.getPoints().subList(1, w.getPoints().size());
				} else {
					points = w.getPoints();
				}
				for (Coord c : points) {
					Set<Way> ways = adjacentWays.get(c);
					if (ways == null) {
						ways = new HashSet<Way>(4);
						adjacentWays.put(c, ways);
					}
					ways.add(w);
				}
				for (Coord c : w.getPoints()) {
					Set<Way> ways = wayNodes.get(c);
					if (ways == null) {
						ways = new HashSet<Way>(4);
						wayNodes.put(c, ways);
					}
					ways.add(w);
				}

				// if the way is a link way and has a destination tag
				// put it the list of ways that have to be processed
				if (tagValues.contains(highwayTag)
						&& w.getTag("destination") != null) {
					destinationLinkWays.add(w);
				}
			}
		}
	}
	
	/**
	 * Copies the destination tags of all motorway_link and trunk_link ways to the adjacent ways.
	 */
	private void processDestinations() {
		// process all links with a destination tag
		// while checking new ways can be added to the list
		while (destinationLinkWays.isEmpty() == false) {
			Way link = destinationLinkWays.poll();

			if (isNotOneway(link)) {
				// non oneway links cannot be handled. The destination tag is probably wrong.
				if (log.isInfoEnabled())
					log.info("Link is not oneway. Do not handle it.", link);
				continue;
			}

			// get the last point of the link to retrieve all connected ways
			Coord connectPoint = link.getPoints().get(link.getPoints().size() - 1);
			if (isOnewayOppositeDirection(link)) {
				// for reverse oneway ways it's the first point
				connectPoint = link.getPoints().get(0);
			}

			String destinationTag = link.getTag("destination");
			
			Set<Way> connectedWays = adjacentWays.get(connectPoint);
			if (connectedWays == null) {
				connectedWays = Collections.emptySet();
			} else {
				connectedWays = new HashSet<Way>(connectedWays);
			}
			connectedWays.remove(link);

			if (log.isInfoEnabled())
				log.info("Link", link);
			for (Way connection : connectedWays) {
				if (connection.getTag("destination") != null) {
					if (log.isInfoEnabled())
						log.info("Way already has destionation tag", connection);
					continue;
				}
				if (log.isInfoEnabled())
					log.info("-", connection);

				// check if the connected way can have an unambiguous direction
				// => it must not have more than one further connections
				boolean oneDestination = true;
				List<Coord> pointsToCheck;
				if (isOnewayInDirection(connection)) {
					pointsToCheck = connection.getPoints();
				} else if (isOnewayOppositeDirection(connection)) {
					pointsToCheck = new ArrayList<Coord>(
							connection.getPoints());
					Collections.reverse(pointsToCheck);
				} else {
					pointsToCheck = Collections.emptyList();
					if (log.isInfoEnabled())
						log.info("Way is not oneway - do not add destination", connection);
					oneDestination = false;
				}
				// check if the target way has an unambiguous destination
				// so it has no other connections to other destinations
				for (Coord c : pointsToCheck) {
					if (c.equals(connectPoint)) {
						break;
					}
					Set<Way> furtherConnects = adjacentWays.get(c);
					for (Way fc : furtherConnects) {
						String fcDest = fc.getTag("destination");
						if (fcDest != null
								&& destinationTag.equals(fcDest) == false) {
							if (log.isInfoEnabled())
								log.info("Ambiguous destination:", destinationTag, "!=", fcDest);
							oneDestination = false;
							break;
						}
					}
					if (oneDestination == false) {
						break;
					}
				}

				if (oneDestination) {
					if (log.isInfoEnabled())
						log.info("Add destination=" + destinationTag, "to", connection);
					connection.addTag("destination", destinationTag);

					if (tagValues.contains(connection.getTag("highway"))) {
						// it is a link so process that way too
						destinationLinkWays.add(connection);
					}
				}
			}
		}
	}
	
	/**
	 * Retrieves the name of the given element based on the name-tag-list option.
	 * @param e an OSM element
	 * @return the name or <code>null</code> if the element has no name
	 */
	private String getName(Element e) {
		if (e.getName()!= null) {
			return e.getName();
		}
		for (String nameTag : nameTags) {
			String nameTagVal = e.getTag(nameTag);
			if (nameTagVal != null) {
				return nameTagVal;
			}
		}
		return null;
	}
	
	/**
	 * Cuts off at least minLength meter of the given way and returns the cut off way tagged
	 * identical to the given way.   
	 * @param w the way to be cut 
	 * @param minLength the cut off way must have at least this length
	 * @return the cut off way or <code>null</code> if cutting not possible
	 */
	private Way cutoffWay(Way w, double minLength, Coord c1, Coord c2) {
		if (w.getPoints().size()<2) {
			return null;
		}
		
		boolean useExistingPoints = w.getPoints().size() >= 3;
		useExistingPoints = false;
		double remainLength = minLength;
		Coord lastC = w.getPoints().get(0);
		for (int i = 1; i < w.getPoints().size(); i++) {
			Coord c = w.getPoints().get(i);
			double segmentLength = lastC.distance(c);
			
			// do not use the last point of the way
			// instead it need to be split in such a case
			useExistingPoints = useExistingPoints && i < w.getPoints().size()-1;
			
			if (useExistingPoints && remainLength - segmentLength <= 0.0) {
				// create a new way with all points 0..i and identical tags
				Way precedingWay = new Way(FakeIdGenerator.makeFakeId(), new ArrayList<Coord>(w.getPoints().subList(0, i+1)));
				precedingWay.copyTags(w);
				// the first node of the new way is now used by the org and the new way
				precedingWay.getPoints().get(0).incHighwayCount();
				
				saver.addWay(precedingWay);
				// remove the points of the new way from the original way
				w.getPoints().subList(0, i).clear();
				
				// return the new way
				return precedingWay;
			} 
			
			if (useExistingPoints == false && remainLength - segmentLength <=  0.0d) {
				double frac = remainLength / segmentLength;
				// insert a new point and the minimum distance  
				Coord cConnection = lastC.makeBetweenPoint(c, frac);

				if (c1 != null && c2 != null && cConnection != null) {
					// test if the way using the new point still uses the same
					// orientation to the main motorway
					double oldAngle = getAngle(c1, c2, c);
					double newAngle = getAngle(c1, c2, cConnection);
					if (Math.signum(oldAngle) != Math.signum(newAngle)) {
						double bestAngleDiff = 180.0d;
						Coord bestCoord = cConnection;
						for (Coord cNeighbour : getDirectNeighbours(cConnection)) {
							double neighbourAngle = getAngle(c1, c2, cNeighbour);
							if (Math.signum(oldAngle) == Math.signum(neighbourAngle) && 
									Math.abs(oldAngle - neighbourAngle) < bestAngleDiff) {
								bestAngleDiff = Math.abs(oldAngle - neighbourAngle);
								bestCoord = cNeighbour;
							}
						}
						if (log.isDebugEnabled()) {
							log.debug("Changed orientation:", oldAngle, "to",
									newAngle);
							log.debug("on Link", w);
							log.debug("Corrected coord ", cConnection, "to",
									bestCoord);
						}
						cConnection = bestCoord;
					}
				}
				
				// the new point is used by the old and the new way
				cConnection.incHighwayCount();
				cConnection.incHighwayCount();
				
				w.getPoints().add(i,cConnection);
				
				// create the new way with identical tags
				Way precedingWay = new Way(FakeIdGenerator.makeFakeId(), new ArrayList<Coord>(w.getPoints().subList(0, i+1)));
				precedingWay.copyTags(w);
				
				saver.addWay(precedingWay);
				
				// remove the points of the new way from the old way
				w.getPoints().subList(0, i).clear();
				
				// return the split way
				return precedingWay;			
			} 		
			remainLength -= segmentLength;
			lastC = c;
		}
		
		// way too short
		return null;
	}
	
	/**
	 * Retrieve a list of all Coords that are the direct neighbours of
	 * the given Coord. A neighbours latitude and longitude does not differ
	 * more than one Garmin unit from the given Coord.
	 * @param c the Coord for which the neighbours should be retrieved.
	 * @return all neighbours of c
	 */
	private List<Coord> getDirectNeighbours(Coord c) {
		List<Coord> neighbours = new ArrayList<Coord>(8);
		for (int dLat = -1; dLat<2; dLat++) {
			for (int dLon = -1; dLon < 2; dLon++) {
				if (dLat == 0 && dLon == 0) {
					continue;
				}
				neighbours.add(new Coord(c.getLatitude()+dLat, c.getLongitude()+dLon));
			}
		}
		return neighbours;
	}
	
	/**
	 * Retrieves if the given node is tagged as motorway exit. So it must contain at least the tags
	 * highway=motorway_junction and one of the tags ref, name or exit_to.
	 * @param node the node to check
	 * @return <code>true</code> the node is a motorway exit, <code>false</code> the node is not a 
	 * 		motorway exit  
	 */
	private boolean isTaggedAsExit(Node node) {
		if ("motorway_junction".equals(node.getTag("highway")) == false) {
			return false;
		}
		return node.getTag("ref") != null || 
				(getName(node) != null) || 
				node.getTag("exit_to") != null;
	}
	
	/**
	 * Retrieve all nodes that are connected to the given node either in
	 * driving direction or reverse.
	 * @param node a coord
	 * @param drivingDirection <code>true</code> driving direction; <code>false</code> reverse direction
	 * @return a list of all coords an the connection ways
	 */
	private List<Entry<Coord, Way>> getNextNodes(Coord node, boolean drivingDirection) {
		List<Entry<Coord, Way>> nextNodes = new ArrayList<Entry<Coord, Way>>();
		
		Set<Way> connectedWays = wayNodes.get(node);
		for (Way w : connectedWays) {
			// get the index of the node
			int index = w.getPoints().indexOf(node);
			if (index < 0) {
				// this should not happen
				log.error("Cannot find node "+node+" in way "+w);
				continue;
			}
			
			boolean oneWayDirection = isOnewayInDirection(w);
			// calc the index of the next node
			index += (drivingDirection ? 1 : -1) * (oneWayDirection ? 1 : -1);
			
			if (index >= 0 && index < w.getPoints().size()) {
				nextNodes.add(new AbstractMap.SimpleEntry<Coord, Way>(w.getPoints().get(index), w));
			}
		}
		
		return nextNodes;
	}
	
	/**
	 * Cuts motorway_link ways connected to an exit node
	 * (highway=motorway_junction) into three parts to be able to get a hint on
	 * Garmin GPS. The mid part way is tagged additionally with the following
	 * tags:
	 * <ul>
	 * <li>mkgmap:exit_hint=true</li>
	 * <li>mkgmap:exit_hint_ref: Tagged with the ref tag value of the exit node</li>
	 * <li>mkgmap:exit_hint_exit_to: Tagged with the exit_to tag value of the exit node</li>
	 * <li>mkgmap:exit_hint_name: Tagged with the name tag value of the exit
	 * node</li>
	 * </ul>
	 * Style implementors can use the common Garmin code 0x09 for motorway_links
	 * and the motorway code 0x01 for the links with mkgmap:exit_hint=true. The
	 * naming of this middle way can be assigned from mkgmap:exit_hint_ref and
	 * mkgmap:exit_hint_name.
	 */
	private void createExitHints() {
		// collect all nodes of highway=motorway ways so that we can check if an exit node
		// belongs to a motorway or is a "subexit" within a motorway junction
		Set<Coord> highwayCoords = new HashSet<Coord>();
		for (Way w : saver.getWays().values()) {
			if ("motorway".equals(w.getTag("highway"))) {
				highwayCoords.addAll(w.getPoints());
			}
		}	
		
		// get all nodes tagged with highway=motorway_junction
		for (Node exitNode : saver.getNodes().values()) {
			if (isTaggedAsExit(exitNode) && saver.getBoundingBox().contains(exitNode.getLocation())) {
				
				boolean isHighwayExit = highwayCoords.contains(exitNode.getLocation());
				// use exits only if they are located on a motorway
				if (onlyMotorwayExitHint && isHighwayExit == false) {
					if (log.isDebugEnabled())
						log.debug("Skip non highway exit:", exitNode.toBrowseURL(), exitNode.toTagString());
					continue;
				}
				
				// retrieve all ways with this exit node
				Set<Way> exitWays = adjacentWays.get(exitNode.getLocation());
				if (exitWays==null) {
					log.debug("Exit node", exitNode, "has no connected ways. Skip it.");
					continue;
				}
				
				// if the exit is on a motorway_link it must be ensured that the hint is 
				// created on the right / left link only
				if (onlyMotorwayExitHint == false && isHighwayExit == false) {
//					List<Entry<Coord, Way>> prevNodes = getNextNodes(exitNode.getLocation(), false);
//					log.error("Prev nodes: "+prevNodes);
//					if (prevNodes.isEmpty()) {
//						log.error("Node "+exitNode+" has no predecessor way. Cannot create exit hint for it.");
//						continue;
//					}
//					if (prevNodes.size() > 1) {
//						log.error("Node "+exitNode+" has multiple predecessor ways. Cannot create exit hint for it.");
//						continue;
//					}
//					
//					// there is one predecessor way 
//					Coord predecessorNode = prevNodes.get(0).getKey();
					
//					List<Entry<Coord, Way>> nextNodes = getNextNodes(exitNode.getLocation(), true);
//					log.error("Next nodes: "+nextNodes);
//					
//					// TODO: differ drive-on-left and drive-on-right
//					// at the moment select the rightmost way
//					Entry<Coord, Way> possibleExit = null;
//					for (Entry<Coord, Way> checkWay : nextNodes) {
//						if (possibleExit == null) {
//							possibleExit = checkWay;
//						} else {
//							
//						}
//					}
					
				}

				// retrieve the next node on the highway to be able to check if 
				// the inserted node has the correct orientation 
				List<Entry<Coord, Way>> nextNodes = getNextNodes(exitNode.getLocation(), true);
				Coord nextHighwayNode = null;
				for (Entry<Coord, Way> nextNode : nextNodes) {
					if ("motorway".equals(nextNode.getValue().getTag("highway"))) {
						nextHighwayNode = nextNode.getKey();
						break;
					}
				}
				
				// use the motorway_link ways only
				for (Way w : exitWays) {
					if ("motorway_link".equals(w.getTag("highway"))) {
						log.debug("Try to cut motorway_link", w, "into three parts for giving hint to exit", exitNode);

						// now create three parts:
						// wayPart1: 10m having the original tags only
						// hintWay: 10m having the original tags plus the mkgmap:exit_hint* tags
						// w: the rest of the original way 
						
						Way wayPart1 = cutoffWay(w,10.0, exitNode.getLocation(), nextHighwayNode);
						if (wayPart1 == null) {
							log.info("Way", w, "is too short to cut at least 10m from it. Cannot create exit hint.");
							continue;
						} else {
							if (log.isDebugEnabled())
								log.debug("Cut off way", wayPart1, wayPart1.toTagString());
						}
						
						Way hintWay = cutoffWay(w,10.0, exitNode.getLocation(), nextHighwayNode);
						if (hintWay == null) {
							log.info("Way", w, "is too short to cut at least 20m from it. Cannot create exit hint.");
						} else {
							hintWay.addTag("mkgmap:exit_hint", "true");
							
							if (exitNode.getTag("ref") != null)
								hintWay.addTag("mkgmap:exit_hint_ref", exitNode.getTag("ref"));
							if (exitNode.getTag("exit_to") != null)
								hintWay.addTag("mkgmap:exit_hint_exit_to", exitNode.getTag("exit_to"));
							if (getName(exitNode) != null)
								hintWay.addTag("mkgmap:exit_hint_name", getName(exitNode));
							
							if (log.isInfoEnabled())
								log.info("Cut off exit hint way", hintWay, hintWay.toTagString());
						}

					}
				}
			}
		}
	}
	
	/**
	 * Retrieves the angle in clockwise direction between the line (cCenter, c1)
	 * and the line (cCenter, c2). 
	 * @param cCenter the common point of both lines
	 * @param c1 point of the first line
	 * @param c2 point of the second line
	 * @return the angle [-180; 180]
	 */
	private double getAngle(Coord cCenter, Coord c1, Coord c2)
	{
	    double dx1 = c1.getLongitude() - cCenter.getLongitude();
	    double dy1 = -(c1.getLatitude() - cCenter.getLatitude());

	    double dx2 = c2.getLongitude() - cCenter.getLongitude();
	    double dy2 = -(c2.getLatitude() - cCenter.getLatitude());

	    double inRads1 = Math.atan2(dy1,dx1);
	    double inRads2 = Math.atan2(dy2,dx2);

	    return Math.toDegrees(inRads2) - Math.toDegrees(inRads1);
	}

	/**
	 * Cleans all internal data that is no longer used after the hook has been processed.
	 */
	private void cleanup() {
		adjacentWays = null;
		wayNodes = null;
		destinationLinkWays = null;
		tagValues = null;
		saver = null;
		nameTags = null;
	}

// Do not return any used tag because this hook only has an effect if the tag destination is
// additionally used in the style file.
//	public Set<String> getUsedTags() {
//		// TODO Auto-generated method stub
//		return super.getUsedTags();
//	}	

	public void end() {
		log.info("LinkDestinationHook started");

		retrieveWays();
		
		if (processDestinations)
			processDestinations();
		if (processExits)
			createExitHints();
		
		cleanup();

		log.info("LinkDestinationHook finished");
	}

	/**
	 * Retrieves if the given way is tagged as oneway in the direction of the way.
	 * @param w the way
	 * @return <code>true</code> way is oneway
	 */
	private boolean isOnewayInDirection(Way w) {
		if (w.isBoolTag("oneway")) {
			return true;
		}
		
		// check if oneway is set implicitly by the highway type (motorway and motorway_link)
		String onewayTag = w.getTag("oneway");
		String highwayTag = w.getTag("highway");
		if (onewayTag == null && highwayTag != null
				&& (highwayTag.equals("motorway") || highwayTag.equals("motorway_link"))) {
			return true;
		}
		return false;
	}

	/**
	 * Retrieves if the given way is tagged as oneway but in opposite direction of the way.
	 * @param w the way
	 * @return <code>true</code> way is oneway in opposite direction
	 */
	private boolean isOnewayOppositeDirection(Way w) {
		return "-1".equals(w.getTag("oneway"));
	}

	/**
	 * Retrieves if the given way is not oneway.
	 * @param w the way
	 * @return <code>true</code> way is not oneway
	 */
	private boolean isNotOneway(Way w) {
		return "no".equals(w.getTag("oneway")) || 
				(isOnewayInDirection(w) == false
				 && isOnewayOppositeDirection(w) == false);
	}

}

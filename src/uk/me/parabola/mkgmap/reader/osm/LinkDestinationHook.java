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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Copies the destination tag from motorway_link and trunk_link ways to the 
 * first adjacent non link way so that the Garmin is able to display a valid
 * destination.
 * @author WanMil
 */
public class LinkDestinationHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger
			.getLogger(LinkDestinationHook.class);

	private ElementSaver saver;

	/** Maps which ways can be driven from a given Coord */
	private IdentityHashMap<Coord, Set<Way>> adjacentWays = new IdentityHashMap<Coord, Set<Way>>();
	/** Contains all _link ways that have to be processed */
	private Queue<Way> destinationLinkWays = new ArrayDeque<Way>();

	private HashSet<String> tagValues = new HashSet<String>(Arrays.asList(
			"motorway_link", "trunk_link"));

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		return props.containsKey("process-destination");
	}

	/**
	 * Fills the internal lists with the 
	 */
	private void retrieveWays() {
		// collect all ways tagged with highway 
		for (Way w : saver.getWays().values()) {
			String highwayTag = w.getTag("highway");
			if (highwayTag != null) {
				// the points of the way are kept so that it is easy to get
				// the adjacent ways for a given _link way
				List<Coord> points;
				if (isOnewayInDirection(w)) {
					// oneway => don't need the last point because the
					// way cannot be driven standing at the last point
					points = w.getPoints().subList(0, w.getPoints().size() - 1);
				} else if (isOnewayReverseDirection(w)) {
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

				// if the way is a link way and has a destination tag
				// put it the list of ways that have to be processed
				if (tagValues.contains(highwayTag)
						&& w.getTag("destination") != null) {
					destinationLinkWays.add(w);
				}
			}
		}
	}
	
	private void processDestionations() {
		// process all links with a destination tag
		// while checking new ways can be added to the list
		while (destinationLinkWays.isEmpty() == false) {
			Way link = destinationLinkWays.poll();

			if (isNotOneway(link)) {
				// non oneway links cannot be handled. The destination tag is probably wrong.
				if (log.isInfoEnabled())
					log.info("Link is not oneway. Do not handle it. ", link);
				continue;
			}

			// get the last point of the link to retrieve all connected ways
			Coord connectPoint = link.getPoints().get(link.getPoints().size() - 1);
			if (isOnewayReverseDirection(link)) {
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
				} else if (isOnewayReverseDirection(connection)) {
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
						String fcDest = fc.getTag("destionation");
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
						log.info("Add destination=" + destinationTag + " to", connection);
					connection.addTag("destination", destinationTag);

					if (tagValues.contains(connection.getTag("highway"))) {
						// it is a link so process that way too
						destinationLinkWays.add(connection);
					}
				}
			}
		}
	}
	

	private void cleanup() {
		adjacentWays = null;
		destinationLinkWays = null;
		tagValues = null;
		saver = null;
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
		
		processDestionations();

		cleanup();

		log.info("LinkDestinationHook finished");
	}

	private boolean isOnewayInDirection(Way w) {
		if (w.isBoolTag("oneway")) {
			return true;
		}
		String onewayTag = w.getTag("oneway");
		String highwayTag = w.getTag("highway");
		if (onewayTag == null && highwayTag != null
				&& highwayTag.endsWith("_link")) {
			return true;
		}
		return false;
	}

	private boolean isOnewayReverseDirection(Way w) {
		return "-1".equals(w.getTag("oneway"));
	}

	private boolean isNotOneway(Way w) {
		return isOnewayInDirection(w) == false
				&& isOnewayReverseDirection(w) == false;
	}

}

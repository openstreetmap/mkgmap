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

package uk.me.parabola.mkgmap.osmstyle.housenumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.NumberStyle;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LineAdder;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.MultiHashMap;

/**
 * Collects all data required for OSM house number handling and adds the
 * house number information to the roads.
 * 
 * @author WanMil
 */
public class HousenumberGenerator {

	private static final Logger log = Logger
			.getLogger(HousenumberGenerator.class);

	/** Gives the maximum distance between house number element and the matching road */
	private static final double MAX_DISTANCE_TO_ROAD = 150d;
	
	private boolean numbersEnabled;
	
	private MultiHashMap<String, MapRoad> roadByNames;
	private List<MapRoad> roads;
	private MultiHashMap<String, Element> houseNumbers;
	
	public HousenumberGenerator(Properties props) {
		this.roadByNames = new MultiHashMap<String,MapRoad>();
		this.houseNumbers = new MultiHashMap<String,Element>();
		this.roads = new ArrayList<MapRoad>();
		
		numbersEnabled=props.containsKey("housenumbers");
	}

	/**
	 * Retrieves the street name of this element.
	 * @param e an OSM element
	 * @return the street name (or {@code null} if no street name set)
	 */
	private String getStreetname(Element e) {
		String streetname = e.getTag("mkgmap:street");
		if (streetname == null) {
			streetname = e.getTag("addr:street");
		}	
		return streetname;
	}
	
	/**
	 * Adds a node for house number processing.
	 * @param n an OSM node
	 */
	public void addNode(Node n) {
		if (numbersEnabled == false) {
			return;
		}
		if (HousenumberMatch.getHousenumber(n) != null) {
			String streetname = getStreetname(n);
			if (streetname != null) {
				houseNumbers.add(streetname, n);
			}
		}
	}
	
	/**
	 * Adds a way for house number processing.
	 * @param w a way
	 */
	public void addWay(Way w) {
		if (numbersEnabled == false) {
			return;
		}
		if (HousenumberMatch.getHousenumber(w) != null) {
			String streetname = getStreetname(w);
			if (streetname != null) {
				houseNumbers.add(streetname, w);
			}
		}
	}
	
	
	/**
	 * Adds a road to be processed by the house number generator.
	 * @param osmRoad the OSM way the defines the road 
	 * @param road a road
	 */
	public void addRoad(Way osmRoad, MapRoad road) {
		roads.add(road);
		if (numbersEnabled) {
			// first try to get the streetname from mkgmap:streetname
			String name = getStreetname(osmRoad); 
			if (name != null) {
				if (log.isDebugEnabled())
					log.debug("Housenumber - Streetname:", name, "Way:",osmRoad.getId(),osmRoad.toTagString());
				roadByNames.add(name, road);
			}
		} 
	}
	
	public void addRelation(Relation r) {
		// TODO 
	}
	
	public void generate(LineAdder adder) {
		if (numbersEnabled) {
			for (Entry<String, List<Element>> numbers : houseNumbers.entrySet()) {
				List<MapRoad> possibleRoads = roadByNames.get(numbers.getKey());

				if (possibleRoads.isEmpty()) {
					continue;
				}

				match(numbers.getKey(), numbers.getValue(), possibleRoads);
			}
		}
		
		for (MapRoad r : roads) {
			adder.add(r);
		}
		
		houseNumbers.clear();
		roadByNames.clear();
		roads.clear();
	}
	
	/**
	 * Sorts house numbers by roads, road segments and position of the house number.
	 * @author WanMil
	 */
	private static class HousenumberMatchComparator implements Comparator<HousenumberMatch> {

		public int compare(HousenumberMatch o1, HousenumberMatch o2) {
			if (o1 == o2) {
				return 0;
			}
			
			if (o1.getRoad() != o2.getRoad()) {
				return o1.getRoad().hashCode() - o2.getRoad().hashCode();
			} 
			
			int dSegment = o1.getSegment() - o2.getSegment();
			if (dSegment != 0) {
				return dSegment;
			}
			
			double dFrac = o1.getSegmentFrac() - o2.getSegmentFrac();
			if (dFrac != 0d) {
				return (int)Math.signum(dFrac);
			}
			
			double dDist = o1.getDistance() - o2.getDistance();
			if (dDist != 0d) {
				return (int)Math.signum(dDist);
			}
			
			return 0;
		}
		
	}
	
	/**
	 * Matches the house numbers of one street name to its OSM elements and roads. 
	 * @param streetname name of street
	 * @param elements a list of OSM elements belonging to this street name
	 * @param roads a list of roads with the given street name
	 */
	private void match(String streetname, List<Element> elements, List<MapRoad> roads) {
		List<HousenumberMatch> numbersList = new ArrayList<HousenumberMatch>(
				elements.size());
		for (Element node : elements) {
			try {
				numbersList.add(new HousenumberMatch(node));
			} catch (IllegalArgumentException exp) {
				log.debug(exp);
			}
		}
		
		MultiHashMap<MapRoad, HousenumberMatch> roadNumbers = new MultiHashMap<MapRoad, HousenumberMatch>(); 
		
		for (HousenumberMatch n : numbersList) {
			
			for (MapRoad r : roads) {
				int node = -1;
				Coord c1 = null;
				for (Coord c2 : r.getPoints()) {
					if (c1 != null) {
						Coord cx = n.getLocation();
						double frac = getFrac(c1, c2, cx);
						double dist = distanceToSegment(c1,c2,cx,frac);
						if (dist <= MAX_DISTANCE_TO_ROAD && dist < n.getDistance()) {
							n.setDistance(dist);
							n.setSegmentFrac(frac);
							n.setRoad(r);
							n.setSegment(node);
						}
					}
					c1 = c2;
					node++;
				}
			}
			
			if (n.getRoad() != null) {
				Coord c1 = n.getRoad().getPoints().get(n.getSegment());
				Coord c2 = n.getRoad().getPoints().get(n.getSegment()+1);
				
				n.setLeft(isLeft(c1, c2, n.getLocation()));
				roadNumbers.add(n.getRoad(), n);
			}
		}
		
		// go through all roads and apply the house numbers
		for (Entry<MapRoad, List<HousenumberMatch>> roadX : roadNumbers.entrySet()) {
			MapRoad r = roadX.getKey();
			if (roadX.getValue().isEmpty()) {
				continue;
			}
			
			List<HousenumberMatch> leftNumbers = new ArrayList<HousenumberMatch>();
			List<HousenumberMatch> rightNumbers = new ArrayList<HousenumberMatch>();
			for (HousenumberMatch hr : roadX.getValue()) {
				if (hr.isLeft()) {
					leftNumbers.add(hr);
				} else {
					rightNumbers.add(hr);
				}
			}
			
			Collections.sort(leftNumbers, new HousenumberMatchComparator());
			Collections.sort(rightNumbers, new HousenumberMatchComparator());
			
			List<Numbers> numbersListing = new ArrayList<Numbers>();
			
			log.info("Housenumbers for",r.getName(),r.getCity());
			log.info("Numbers:",roadX.getValue());
			
			int n = 0;
			int nodeIndex = 0;
			int lastRoutableNodeIndex = 0;
			for (Coord p : r.getPoints()) {
				if (n == 0) {
					assert p instanceof CoordNode; 
				}

				// An ordinary point in the road.
				if (p.getId() == 0) {
					n++;
					continue;
				}

				// The first time round, this is guaranteed to be a CoordNode
				if (n == 0) {
					nodeIndex++;
					n++;
					continue;
				}

				// Now we have a CoordNode and it is not the first one.
				Numbers numbers = new Numbers();
				numbers.setNodeNumber(0);
				numbers.setRnodNumber(lastRoutableNodeIndex);
			
				applyNumbers(numbers,leftNumbers,n,true);
				applyNumbers(numbers,rightNumbers,n,false);

				if (log.isInfoEnabled()) {
					log.info("Left: ",numbers.getLeftNumberStyle(),numbers.getRnodNumber(),"Start:",numbers.getLeftStart(),"End:",numbers.getLeftEnd(), "Remaining: "+leftNumbers);
					log.info("Right:",numbers.getRightNumberStyle(),numbers.getRnodNumber(),"Start:",numbers.getRightStart(),"End:",numbers.getRightEnd(), "Remaining: "+rightNumbers);
				}
				
				numbersListing.add(numbers);
				
				lastRoutableNodeIndex = nodeIndex;
				nodeIndex++;
				n++;
			}
			
			r.setNumbers(numbersListing);
		}
	}
	
	/**
	 * Apply the given house numbers to the numbers object.
	 * @param numbers the numbers object to be configured
	 * @param housenumbers a list of house numbers
	 * @param maxSegment the highest segment number to use
	 * @param left {@code true} the left side of the street; {@code false} the right side of the street
	 */
	private void applyNumbers(Numbers numbers, List<HousenumberMatch> housenumbers, int maxSegment, boolean left) {
		NumberStyle style = NumberStyle.NONE;

		if (housenumbers.isEmpty() == false) {
			// get the sublist of housenumbers
			int maxN = -1;
			boolean even = false;
			boolean odd = false;
			for (int i = 0; i< housenumbers.size(); i++) {
				HousenumberMatch hn = housenumbers.get(i);
				if (hn.getSegment() >= maxSegment) {
					break;
				} else {
					maxN = i;
					if (hn.getHousenumber() % 2 == 0) {
						even = true;
					} else {
						odd = true;
					}
				}
			}
			
			if (maxN >= 0) {
				if (even && odd) {
					style = NumberStyle.BOTH;
				} else if (even) {
					style = NumberStyle.EVEN;
				} else {
					style = NumberStyle.ODD;
				}
				
				int start = housenumbers.get(0).getHousenumber();
				int end = housenumbers.get(maxN).getHousenumber();
				if (left) { 
					numbers.setLeftStart(start);
					numbers.setLeftEnd(end);
				} else {
					numbers.setRightStart(start);
					numbers.setRightEnd(end);
				}
				
				housenumbers.subList(0, maxN+1).clear();
			}
		}
		
		if (left)
			numbers.setLeftNumberStyle(style);
		else
			numbers.setRightNumberStyle(style);
		
	}
	
	/**
	 * Evaluates if the given point lies on the left side of the line spanned by spoint1 and spoint2.
	 * @param spoint1 first point of line
	 * @param spoint2 second point of line
	 * @param point the point to check
	 * @return {@code true} point lies on the left side; {@code false} point lies on the right side
	 */
	private boolean isLeft(Coord spoint1, Coord spoint2, Coord point) {
		
		boolean left =  ((spoint2.getLongitude() - spoint1.getLongitude())
				* (point.getLatitude() - spoint1.getLatitude()) - (spoint2.getLatitude() - spoint1
				.getLatitude()) * (point.getLongitude() - spoint1.getLongitude())) > 0;

				return left;
	}
	
	/**
	 * Calculates the distance to the given segment in meter.
	 * @param spoint1 segment point 1
	 * @param spoint2 segment point 2
	 * @param point point
	 * @return the distance in meter
	 */
	private double distanceToSegment(Coord spoint1, Coord spoint2, Coord point, double frac) {

		if (frac <= 0) {
			return spoint1.distance(point);
		} else if (frac >= 1) {
			return spoint2.distance(point);
		} else {
			return spoint1.makeBetweenPoint(spoint2, frac).distance(point);
		}

	}
	
	/**
	 * Calculates the fraction at which the given point is closest to the line segment.
	 * @param spoint1 segment point 1
	 * @param spoint2 segment point 2
	 * @param point point
	 * @return the fraction
	 */
	private double getFrac(Coord spoint1, Coord spoint2, Coord point) {

		double dx = spoint2.getLongitude() - spoint1.getLongitude();
		double dy = spoint2.getLatitude() - spoint1.getLatitude();

		if ((dx == 0) && (dy == 0)) {
			return 0;
		}

		return ((point.getLongitude() - spoint1.getLongitude()) * dx + (point
				.getLatitude() - spoint1.getLatitude()) * dy)
				/ (dx * dx + dy * dy);

	}
}

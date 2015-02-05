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

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeSet;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.NumberStyle;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LineAdder;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.FakeIdGenerator;
import uk.me.parabola.mkgmap.reader.osm.HousenumberHooks;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.POIGeneratorHook;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
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
	public static final double MAX_DISTANCE_TO_ROAD = 150d;
	
	private boolean numbersEnabled;
	
	private MultiHashMap<String, MapRoad> roadByNames;
	private List<MapRoad> roads;
	private MultiHashMap<String, Element> houseNumbers;
	private Map<Long,Node> houseNodes;
	
	private static final short streetTagKey = TagDict.getInstance().xlate("mkgmap:street");
	private static final short addrStreetTagKey = TagDict.getInstance().xlate("addr:street");
	private static final short addrInterpolationTagKey = TagDict.getInstance().xlate("addr:interpolation");	
	private static final short housenumberTagKey = TagDict.getInstance().xlate("mkgmap:housenumber");		
	public HousenumberGenerator(Properties props) {
		this.roadByNames = new MultiHashMap<String,MapRoad>();
		this.houseNumbers = new MultiHashMap<String,Element>();
		this.roads = new ArrayList<MapRoad>();
		this.houseNodes = new HashMap<>();
		
		numbersEnabled=props.containsKey("housenumbers");
	}

	/**
	 * Retrieves the street name of this element.
	 * @param e an OSM element
	 * @return the street name (or {@code null} if no street name set)
	 */
	private static String getStreetname(Element e) {
		String streetname = e.getTag(streetTagKey);
		if (streetname == null) {
			streetname = e.getTag(addrStreetTagKey);
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
		if ("true".equals(n.getTag(POIGeneratorHook.AREA2POI_TAG)))
			return; 		
		if (HousenumberMatch.getHousenumber(n) != null) {
			String streetname = getStreetname(n);
			if (streetname != null) {
				houseNumbers.add(streetname, n);
				if (n.getTag(HousenumberHooks.partOfInterpolationTagKey) != null)
					houseNodes.put(n.getId(),n);
			} else {
				if (log.isDebugEnabled())
					log.debug(n.toBrowseURL()," ignored, doesn't contain a street name.");
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
		String ai = w.getTag(addrInterpolationTagKey);
		if (ai != null){
			String nodeIds = w.getTag(HousenumberHooks.mkgmapNodeIdsTagKey);
			if (nodeIds != null){
				List<Node> nodes = new ArrayList<>();
				String[] ids = nodeIds.split(",");
				for (String id : ids){
					Node node = houseNodes.get(Long.decode(id));
					if (node != null){
						nodes.add(node);
					}
				}
				doInterpolation(w,nodes);
			}
		}
		
		
		if (HousenumberMatch.getHousenumber(w) != null) {
			String streetname = getStreetname(w);
			if (streetname != null) {
				houseNumbers.add(streetname, w);
			} else {
				if (log.isDebugEnabled()){
					if (FakeIdGenerator.isFakeId(w.getId()))
						log.debug("mp-created way ignored, doesn't contain a street name. Tags:",w.toTagString());
					else 
						log.debug(w.toBrowseURL()," ignored, doesn't contain a street name.");
				}
			}
		}
	}
	
	/**
	 * Use the information provided by the addr:interpolation tag
	 * to generate additional house number elements. This increases
	 * the likelihood that a road segment is associated with the right 
	 * number ranges. 
	 * @param w the way
	 * @param nodes list of nodes
	 */
	private void doInterpolation(Way w, List<Node> nodes) {
		int numNodes = nodes.size();
		String addrInterpolationMethod = w.getTag(addrInterpolationTagKey);
		int step = 0;
		switch (addrInterpolationMethod) {
		case "all":
		case "1":
			step = 1;
			break;
		case "even":
		case "odd":
		case "2":
			step = 2;
			break;
		default:
			break;
		}
		if (step == 0)
			return; // should not happen here
		int pos = 0;
		for (int i = 0; i+1 < numNodes; i++){
			// the way have other points, find the sequence including the pair of nodes    
			Node n1 = nodes.get(i);
			Node n2 = nodes.get(i+1);
			int pos1 = -1, pos2 = -1;
			for (int k = pos; k < w.getPoints().size(); k++){
				if (w.getPoints().get(k) == n1.getLocation()){
					pos1 = k;
					break;
				}
			}
			if (pos1 < 0){
				log.error("addr:interpolation node not found in way",w);
				return;
			}
			for (int k = pos1+1; k < w.getPoints().size(); k++){
				if (w.getPoints().get(k) == n2.getLocation()){
					pos2 = k;
					break;
				}
			}
			if (pos2 < 0){
				log.error("addr:interpolation node not found in way",w);
				return;
			}
			pos = pos2;
			String street1 = getStreetname(n1);
			String street2 = getStreetname(n2);
			if (street1 != null && street1.equals(street2)){
				try {
					HousenumberMatch m1 = new HousenumberMatch(n1);
					HousenumberMatch m2 = new HousenumberMatch(n2);
					int start = m1.getHousenumber();
					int end = m2.getHousenumber();
					int steps, usedStep;
					if (start < end){
						steps = (end - start) / step - 1;
						usedStep = step;
						
					} else {
						steps = (start - end) / step - 1;
						usedStep = - step;
					}
					if (steps <= 0){
						log.info(w.toBrowseURL(),"addr:interpolation way segment ignored, no number between",start,"and",end);
						continue;
					}
					if ("even".equals(addrInterpolationMethod) && (start % 2 != 0 || end % 2 != 0)){
						log.info(w.toBrowseURL(),"addr:interpolation=even is used with odd housenumber(s)",start,end);
						continue;
					}
					if ("odd".equals(addrInterpolationMethod) && (start % 2 == 0 || end % 2 == 0)){
						log.info(w.toBrowseURL(),"addr:interpolation=odd is used with even housenumber(s)",start,end);
						continue;
					}
					List<Coord> interpolated = getPointsOnWay(w, pos1, pos2, steps);
					if (interpolated == null || interpolated.isEmpty())
						continue;
					int hn = start; 
					StringBuilder sb = new StringBuilder();
					for (Coord co : interpolated){
						hn += usedStep;
						Node generated = new Node(FakeIdGenerator.makeFakeId(), co);
						generated.addTag(streetTagKey, street1);
						String number = String.valueOf(hn);
						generated.addTag(housenumberTagKey, number);
						if (log.isDebugEnabled()){
							sb.append(number);
							sb.append(",");
						}
						houseNumbers.add(street1, generated);
					}
					if (log.isDebugEnabled()){
						if (sb.length() > 0)
							sb.setLength(sb.length()-1);
						log.debug(w.toBrowseURL(), addrInterpolationMethod, "added interpolated number(s)" , sb.toString(),"to",street1);
					}
				} catch (IllegalArgumentException exp) {
					log.debug(exp);
				}
			}
		}
	}

	/**
	 * Calculate the wanted number of coords on a way so that they have
	 * similar distances to each other (and to the first and last point 
	 * of the way).
	 * @param points list of points that build the way
	 * @param num the wanted number 
	 * @return a list with the number of points or the empty list in 
	 * case of errors
	 */
	private List<Coord> getPointsOnWay(Way w, int i1, int i2, int num){
		List<Coord> interpolated = new ArrayList<>(num);
		if (i2 >= w.getPoints().size())
			return interpolated;
		List<Coord> points = w.getPoints().subList(i1, i2+1);
		if (points.size() < 2)
			return interpolated;
		double wayLen = 0;
		for (int i = 0; i+1 < points.size(); i++){
			wayLen += points.get(i).distance(points.get(i+1));
		}
		double ivlLen = wayLen / (num+1);
		if (ivlLen < 0.1){
			log.info("addr:interpolation",w.toBrowseURL(),"segment ignored, would generate",num,"houses with distance of",ivlLen,"m");
			return interpolated;
		}
		int pos = 0;
		double rest = 0;
		while (pos+1 < points.size()){
			Coord c1 = points.get(pos);
			Coord c2 = points.get(pos+1);
			pos++;
			double neededPartOfSegment = 0;
			double segmentLen = c1.distance(c2);
			for(;;){
				neededPartOfSegment += ivlLen - rest;
				if (neededPartOfSegment <= segmentLen){
					double fraction = neededPartOfSegment / segmentLen;
					Coord c = c1.makeBetweenPoint(c2, fraction);
					interpolated.add(c);
					if (interpolated.size() >= num)
						return interpolated;
					rest = 0;
				} else {
					rest = segmentLen - neededPartOfSegment + ivlLen;
					break;
				}
			}
			
		}
		log.warn("addr:interpolation",w.toBrowseURL(),"interpolation for segment",i1,"-",i2,"failed");
		return interpolated;
	}
	
	/**
	 * Adds a road to be processed by the house number generator.
	 * @param osmRoad the OSM way the defines the road 
	 * @param road a road
	 */
	public void addRoad(Way osmRoad, MapRoad road) {
		roads.add(road);
		if (numbersEnabled) {
			String name = getStreetname(osmRoad); 
			if (name != null) {
				if (log.isDebugEnabled())
					log.debug("Housenumber - Streetname:", name, "Way:",osmRoad.getId(),osmRoad.toTagString());
				roadByNames.add(name, road);
			}
		} 
	}
	
	/**
	 * Evaluate type=associatedStreet relations.
	 */
	public void addRelation(Relation r) {
		if (numbersEnabled == false) 
			return;
		String relType = r.getTag("type");
		// the wiki says that we should also evaluate type=street
		if ("associatedStreet".equals(relType) || "street".equals(relType)){
			List<Element> houses= new ArrayList<>();
			List<Element> streets = new ArrayList<>();
			for (Map.Entry<String, Element> member : r.getElements()) {
				if (member.getValue() instanceof Node) {
					Node node = (Node) member.getValue();
					houses.add(node);
				} else if (member.getValue() instanceof Way) {
					Way w = (Way) member.getValue();
					String role = member.getKey();
					switch (role) {
					case "house":
					case "addr:houselink":
					case "address":
						houses.add(w);
						break;
					case "street":
						streets.add(w);
						break;
					case "":
						if (w.getTag("highway") != null){
							streets.add(w);
							continue;
						}
						String buildingTag = w.getTag("building");
						if (buildingTag != null)
							houses.add(w);
						else 
							log.warn("Relation",r.toBrowseURL(),": role of member",w.toBrowseURL(),"unclear");
						break;
					default:
						log.warn("Relation",r.toBrowseURL(),": don't know how to handle member with role",role);
						break;
					}
				}
			}
			if (houses.isEmpty()){
				if ("associatedStreet".equals(relType))
					log.warn("Relation",r.toBrowseURL(),": ignored, found no houses");
				return;
			}
			String streetName = r.getTag("name");
			String streetNameFromRoads = null;
			boolean nameFromStreetsIsUnclear = false;
			if (streets.isEmpty() == false) {
				for (Element street : streets) {
					String roadName = street.getTag("name");
					if (roadName == null)
						continue;
					if (streetNameFromRoads == null)
						streetNameFromRoads = roadName;
					else if (streetNameFromRoads.equals(roadName) == false)
						nameFromStreetsIsUnclear = true;
				}
			}
			if (streetName == null){
				if (nameFromStreetsIsUnclear == false)
					streetName = streetNameFromRoads;
				else {
					log.warn("Relation",r.toBrowseURL(),": ignored, street name is not clear.");
					return;
				}

			} else {
				if (streetNameFromRoads != null){
					if (nameFromStreetsIsUnclear == false && streetName.equals(streetNameFromRoads) == false){
						log.warn("Relation",r.toBrowseURL(),": street name is not clear, using the name from the way, not that of the relation.");
						streetName = streetNameFromRoads;
					} 
					else if (nameFromStreetsIsUnclear == true){
						log.warn("Relation",r.toBrowseURL(),": street name is not clear, using the name from the relation.");
					}
				} 
			}
			int countOK = 0;
			if (streetName != null && streetName.isEmpty() == false){
				for (Element house : houses) {
					if (addStreetTagFromRel(r, house, streetName) )
						countOK++;
				}
			}
			if (countOK > 0)
				log.info("Relation",r.toBrowseURL(),": added tag mkgmap:street=",streetName,"to",countOK,"of",houses.size(),"house members");
			else 
				log.info("Relation",r.toBrowseURL(),": ignored, the house members all have a addr:street or mkgmap:street tag");
		}
	}
	
	/**
	 * Add the tag mkgmap:street=streetName to the element of the 
	 * relation if it does not already have a street name tag.
	 */
	private boolean addStreetTagFromRel(Relation r, Element house, String streetName){
		String addrStreet = getStreetname(house);
		if (addrStreet == null){
			house.addTag(streetTagKey, streetName);
			if (log.isDebugEnabled())
				log.debug("Relation",r.toBrowseURL(),": adding tag mkgmap:street=" + streetName, "to house",house.toBrowseURL());
			return true;
		}
		else if (addrStreet.equals(streetName) == false){
			if (house.getTag(streetTagKey) != null){
				log.warn("Relation",r.toBrowseURL(),": street name from relation doesn't match existing mkgmap:street tag for house",house.toBrowseURL(),"the house seems to be member of another type=associatedStreet relation");
				house.deleteTag(streetTagKey);
			}
			else 
				log.warn("Relation",r.toBrowseURL(),": street name from relation doesn't match existing name for house",house.toBrowseURL());
		}
		return false;
	}
	
	
	public void generate(LineAdder adder) {
		if (numbersEnabled) {

			TreeSet<String> sortedStreetNames = new TreeSet<>();
			for (Entry<String, List<Element>> numbers : houseNumbers.entrySet()) {
				sortedStreetNames.add(numbers.getKey());
			}

			// process the roads in alphabetical order. This is not needed
			// but helps when comparing results in the log.
			for (String streetName: sortedStreetNames){
				List<MapRoad> possibleRoads = roadByNames.get(streetName);
				if (possibleRoads.isEmpty()) {
					continue;
				}

				List<Element> numbers = houseNumbers.get(streetName);
				MultiHashMap<Integer, MapRoad> clusters = buildRoadClusters(possibleRoads);
				List<HousenumberMatch> houses = convertElements(numbers);
				for (List<MapRoad> cluster: clusters.values()){
					matchCluster(streetName, houses, cluster);
				}
				for (HousenumberMatch house : houses){
					log.error("found no street for house number element",streetName,house.getHousenumber(),house.getElement().toBrowseURL(),", distance to next possible road:",Math.round(house.getDistance()),"m");
				}
			} 		
		}
		
		for (MapRoad r : roads) {
//			if (r.getRoadDef().hasInternalNodes() == false/* && r.getRoadDef().hasHouseNumbers()*/){
//				for (Coord co : r.getPoints()){
//					if (co.isNumberNode() && co.getId() == 0){
//						r.setInternalNodes(true);
//						break;
//					}
//				}
//			}
			adder.add(r);
		}
		
		houseNumbers.clear();
		roadByNames.clear();
		roads.clear();
	}
	
	private void matchCluster(String streetName, List<HousenumberMatch> houses,
			List<MapRoad> roadsInCluster) {
		
		List<HousenumberMatch> housesNearCluster = new ArrayList<>();
		Iterator<HousenumberMatch> iter = houses.iterator();
		
		while (iter.hasNext()){
			HousenumberMatch hnm = iter.next();
			boolean foundRoad = false;
			for (MapRoad r : roadsInCluster) {
				Coord c1 = null;
				for (Coord c2 : r.getPoints()) {
					if (c1 != null) {
						Coord cx = hnm.getLocation();
						double dist = cx.shortestDistToLineSegment(c1, c2);
						if (hnm.getDistance() > dist)
							hnm.setDistance(dist);
						if (dist <= MAX_DISTANCE_TO_ROAD){
							housesNearCluster.add(hnm);
							foundRoad = true;
							break;
						}
					}
					c1 = c2;
				}
				if (foundRoad){
					iter.remove();
					break;
				}
			}
		}
		// we now have a list of houses and a list of roads that are in one area
		MultiHashMap<HousenumberMatch, MapRoad> badRoadMatches = new MultiHashMap<>();
		for (;;){
			int oldBad = badRoadMatches.size();
//			log.debug(streetName, roadsInCluster);
			boolean isOK = assignHouseNumbersToRoads(0, streetName, housesNearCluster, roadsInCluster, badRoadMatches);
			if (isOK || oldBad ==  badRoadMatches.size())
				break;
			log.error("repeating cluster for",streetName);
		}
	}

	private static boolean assignHouseNumbersToRoads(int depth, String streetName,
			List<HousenumberMatch> housesNearCluster,
			List<MapRoad> roadsInCluster,
			MultiHashMap<HousenumberMatch, MapRoad> badRoadMatches) {
		if (housesNearCluster.isEmpty())
			return true;
		log.error("current cluster",streetName,roadsInCluster);
		MultiHashMap<MapRoad, HousenumberMatch> roadNumbers = new MultiHashMap<MapRoad, HousenumberMatch>(); 
		Collections.sort(housesNearCluster, new Comparator<HousenumberMatch>() {
			public int compare(HousenumberMatch o1, HousenumberMatch o2) {
				if (o1 == o2)
					return 0;
				int d = o1.getHousenumber() - o2.getHousenumber();
				if (d != 0)
					return d;
				return o1.getSign().compareTo(o2.getSign());
			}
		}); 
		
		HousenumberMatch prev = null;
		for (HousenumberMatch hnm : housesNearCluster) {
			List<MapRoad> excludedRoads = badRoadMatches.get(hnm);
			hnm.setDistance(Double.POSITIVE_INFINITY); // make sure that we don't use an old match
			hnm.setRoad(null);
			List<HousenumberMatch> matchingRoads = new ArrayList<>();
			
			for (MapRoad r : roadsInCluster){
				if (excludedRoads.contains(r)){
					continue;
				}
				HousenumberMatch hnm2 = new HousenumberMatch(hnm.getElement(), hnm.getHousenumber(), hnm.getSign());
				findClosestRoadSegment(hnm2, r, null);
				if (hnm2.getRoad() != null){
					matchingRoads.add(hnm2);
				}
			}
			if (matchingRoads.isEmpty())
				continue;
			
			HousenumberMatch closest, best; 
			best = closest  = matchingRoads.get(0);
			
			if (matchingRoads.size() > 1){
				// multiple roads, we assume that the closest is the best
				// but we may have to check the alternatives as well
				
				Collections.sort(matchingRoads, new Comparator<HousenumberMatch>() {
					// sort by distance (smallest first)
					public int compare(HousenumberMatch o1, HousenumberMatch o2) {
						if (o1 == o2)
							return 0;
						return Double.compare(o1.getDistance(), o2.getDistance());
						
					}
				});
				closest  = matchingRoads.get(0);
				best = checkAngle(closest, matchingRoads);	
			}
			
			hnm.setDistance(best.getDistance());
			hnm.setSegmentFrac(best.getSegmentFrac());
			hnm.setRoad(best.getRoad());
			hnm.setSegment(best.getSegment());
			hnm.setHasAlternativeRoad(matchingRoads.size() > 1);
			if (hnm.getRoad() == null) {
				hnm.setIgnored(true);
			} else {
				Coord c1 = hnm.getRoad().getPoints().get(hnm.getSegment());
				Coord c2 = hnm.getRoad().getPoints().get(hnm.getSegment()+1);
				double segmentLength = c1.distance(c2);
				if (segmentLength < 0.5){
					// problem ?
				}
				hnm.setLeft(isLeft(c1, c2, hnm.getLocation()));
			}
			// plausibility check for duplicate house numbers
			if (prev != null && prev.getHousenumber() == hnm.getHousenumber()){
				// duplicate number (e.g. 10 and 10 or 10 and 10A or 10A and 10B)
				HousenumberMatch bad = null;
				if (prev.getSign().equals(hnm.getSign())){
					prev.setDuplicate(true);
					hnm.setDuplicate(true);
				}
				
				if (prev.getRoad() != hnm.getRoad()){
					Coord c1 = prev.getRoad().getPoints().get(prev.getSegment());
					Coord c2 = prev.getRoad().getPoints().get(prev.getSegment()+1);
					double dist1 = hnm.getLocation().shortestDistToLineSegment(c1, c2);
					if (dist1 < MAX_DISTANCE_TO_ROAD){
						hnm.setRoad(null); // ignore
						bad = hnm;
					}
				} else if (Math.abs(prev.getSegment() - hnm.getSegment()) > 1){
					double dist = prev.getLocation().distance(hnm.getLocation());
					if (dist > 100){
						int compareDist = Double.compare(prev.getDistance(), hnm.getDistance());

						if (compareDist > 0){
							roadNumbers.removeMapping(prev.getRoad(), prev);
							bad = prev;
						} else if (compareDist <= 0) {
							hnm.setRoad(null); // ignore
							bad = hnm;
						}
					}
				} else if (prev.isLeft() != hnm.isLeft()){
					//difficult case. Which side is correct ?
				}
				if (bad != null){
					bad.setIgnored(true);
					String msgType = prev.getSign().equals(hnm.getSign()) ? "duplicate" : "repeated";
					log.error("ignoring",msgType,"housenumber",streetName,bad.getSign(),"from",bad.getElement().toBrowseURL() );
				}
			}
			if (hnm.getRoad() != null) {
				roadNumbers.add(hnm.getRoad(), hnm);
			} else {
				if (hnm.isIgnored() == false)
					log.error("found no plausible road for house number element",hnm.getElement().toBrowseURL(),"(",streetName,hnm.getSign(),")");
			}
			if (!hnm.isIgnored())
				prev = hnm;
		}
		// we have now a first guess for the road and segment of each plausible house number element
		if (roadsInCluster.size() > 1){
			checkDubiousRoadMatches(streetName, housesNearCluster, roadNumbers);
		}
		int oldBad = badRoadMatches.size();
		buildNumberIntervals(streetName, roadsInCluster, roadNumbers,badRoadMatches);
		if (oldBad != badRoadMatches.size())
			return false;

		List<HousenumberMatch> failed = checkPlausibility(streetName, roadsInCluster, housesNearCluster);
		// TODO: implement algo to assign random numbers, maybe using multiple number nodes with the same position 
		if (failed.isEmpty())
			return true;
		return false;
	}


	/**
	 * If we find a sequence of house numbers like 1,3,5 or 1,2,3
	 * where the house in the middle is assigned to a different road,
	 * it is likely that this match is wrong.
	 * This typically happens when a house is rather far away from two 
	 * possible roads, but a bit closer to the wrong match. The two roads
	 * typically form an L, U, or O shape. 
	 * @param streetName common name tag (for debugging) 
	 * @param sortedHouses house number elements sorted by number
	 * @param roadNumbers the existing map which should be corrected
	 */
	private static void checkDubiousRoadMatches(String streetName,
			List<HousenumberMatch> sortedHouses,
			MultiHashMap<MapRoad, HousenumberMatch> roadNumbers) {
		
		int n = sortedHouses.size();
		
		for (int pos1 = 0; pos1 < n; pos1++){
			HousenumberMatch hnm1 = sortedHouses.get(pos1);
			if (hnm1.isIgnored() || hnm1.hasAlternativeRoad() == false)
				continue;
			int confirmed = 0;
			int falsified = 0;
			int pos2 = pos1;
			HousenumberMatch bestAlternative = null;
			double bestAlternativeDist = Double.POSITIVE_INFINITY;
			
			
			while (pos2 > 0){
				HousenumberMatch hnm2 = sortedHouses.get(pos2);
				if (hnm1.getHousenumber() - hnm2.getHousenumber() > 2)
					break;
					--pos2;
			}
			for (; pos2 < n; pos2++){
				if (confirmed > 0)
					break;
				if (pos2 == pos1)
					continue;
				HousenumberMatch hnm2 = sortedHouses.get(pos2);
				if (hnm2.isIgnored() || hnm2.getRoad() == null)
					continue;
				int deltaNum = hnm2.getHousenumber() - hnm1.getHousenumber();
				if (deltaNum > 2)
					break;
				if (deltaNum < -2)
					continue;
				double distHouses = hnm1.getLocation().distance(hnm2.getLocation());
				if (hnm2.getRoad() == hnm1.getRoad()){
					if (Math.abs(hnm1.getSegment() - hnm2.getSegment()) < 2){
						if (distHouses < 1.5 * bestAlternativeDist)
							confirmed++;
					}
					continue;
				}
				
				Coord c1 = hnm2.getRoad().getPoints().get(hnm2.getSegment());
				Coord c2 = hnm2.getRoad().getPoints().get(hnm2.getSegment()+1);
				double frac2 = getFrac(c1,c2, hnm1.getLocation());
				double dist2 = distanceToSegment(c1,c2,hnm1.getLocation(),frac2);
				if (distHouses > dist2)
					continue;
				if (distHouses > hnm1.getDistance())
					continue;
				Coord c3 = hnm1.getRoad().getPoints().get(hnm1.getSegment());
				Coord c4 = hnm1.getRoad().getPoints().get(hnm1.getSegment()+1);
				if (c1 == c3 && Math.abs(Utils.getAngle(c2, c1, c4)) < 10 ||
						c1 == c4 && Math.abs(Utils.getAngle(c2, c1, c3)) < 10 ||
						c2 == c3 && Math.abs(Utils.getAngle(c1, c2, c4)) < 10 ||
						c2 == c4 && Math.abs(Utils.getAngle(c1, c2, c3)) < 10){
					confirmed++;
					continue;
				}
				++falsified;
				if (bestAlternative == null || dist2 < bestAlternativeDist){
					bestAlternative = hnm2;
					bestAlternativeDist = dist2;
				}
				log.error("road check hnm1:",hnm1.getRoad(),hnm1,hnm1.getDistance(),",hnm2:", hnm2.getRoad(),hnm2,hnm2.getDistance(),distHouses,dist2,frac2,"hnm1 is falsified");
			}
			if (confirmed == 0 && falsified > 0){
				log.error("house number element assigned to road",hnm1.getRoad(),hnm1,hnm1.getElement().toBrowseURL(),"is closer to more plausible houses at road",bestAlternative.getRoad());
				roadNumbers.removeMapping(hnm1.getRoad(), hnm1);
				hnm1.setRoad(bestAlternative.getRoad());
				hnm1.setSegment(bestAlternative.getSegment());
				Coord c1 = bestAlternative.getRoad().getPoints().get(bestAlternative.getSegment());
				Coord c2 = bestAlternative.getRoad().getPoints().get(bestAlternative.getSegment()+1);
				double frac2 = getFrac(c1,c2, hnm1.getLocation());
				double dist2 = distanceToSegment(c1,c2,hnm1.getLocation(),frac2);
				hnm1.setSegmentFrac(frac2);
				hnm1.setDistance(dist2);
				hnm1.setLeft(isLeft(c1, c2, hnm1.getLocation()));
				roadNumbers.add(hnm1.getRoad(), hnm1);
			}
		}
	}

	/**
	 * Fill the fields in hnm.  
	 * @param hnm
	 * @param r
	 * @param toTest
	 */
	public static void findClosestRoadSegment(HousenumberMatch hnm, MapRoad r, BitSet toTest) {
		List<HousenumberMatch> sameDistMatches = new ArrayList<>();
		Coord cx = hnm.getLocation();

		hnm.setRoad(null);
		hnm.setDistance(Double.POSITIVE_INFINITY);
		for (int node = 0; node + 1 < r.getPoints().size(); node++){
			if (toTest != null && toTest.get(node) == false)
					continue;
			Coord c1 = r.getPoints().get(node);
			Coord c2 = r.getPoints().get(node + 1);
			double frac = getFrac(c1, c2, cx);
			double dist = distanceToSegment(c1,c2,cx,frac);
			if (dist <= MAX_DISTANCE_TO_ROAD) {
				if (dist < hnm.getDistance()) {
					hnm.setDistance(dist);
					hnm.setSegmentFrac(frac);
					hnm.setRoad(r);
					hnm.setSegment(node);
					sameDistMatches.clear();
				} else if (dist == hnm.getDistance() && hnm.getRoad() != r){
					HousenumberMatch sameDist = new HousenumberMatch(hnm.getElement());
					sameDist.setDistance(dist);
					sameDist.setSegmentFrac(frac);
					sameDist.setRoad(r);
					sameDist.setSegment(node);
					sameDistMatches.add(sameDist);
				}
			}
		}
		checkAngle(hnm, sameDistMatches);
	}

	private static void buildNumberIntervals(String streetName, List<MapRoad> roadsInCluster,
			MultiHashMap<MapRoad, HousenumberMatch> roadNumbers, MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		// go through all roads and apply the house numbers
		ArrayList<ExtNumbers> numberHeads = new ArrayList<>();
		ArrayList<MapRoad> roadsWithNumbers = new ArrayList<>();
		for (MapRoad r : roadsInCluster){
			List<HousenumberMatch> potentialNumbersThisRoad = roadNumbers.get(r);
			if (potentialNumbersThisRoad.isEmpty()) 
				continue;
			Collections.sort(potentialNumbersThisRoad, new Comparator<HousenumberMatch>() {
				public int compare(HousenumberMatch o1, HousenumberMatch o2) {
					if (o1 == o2)
						return 0;
					int d = o1.getHousenumber() - o2.getHousenumber();
					if (d == 0)
						d = o1.getSegment() - o2.getSegment();
					return d;
				}
			});
			Int2IntOpenHashMap usedNumbers = new Int2IntOpenHashMap();
			for (HousenumberMatch hnm : potentialNumbersThisRoad){
				int num = hnm.getHousenumber();
				int count = usedNumbers.get(num);
				usedNumbers.put(num, count + 1);
			}
			
//			int oddLeft = 0, oddRight = 0, evenLeft = 0, evenRight = 0;
//			for (HousenumberMatch  hnm: potentialNumbersThisRoad){
//				if (hnm.isLeft()){
//					if (hnm.getHousenumber() % 2 == 0) 
//						evenLeft++;
//					else 
//						oddLeft++;
//				} else {
//					if (hnm.getHousenumber() % 2 == 0)
//						evenRight++;
//					else
//						oddRight++;
//				}
//			}
//			NumberStyle expectedLeftStyle = NumberStyle.BOTH, expectedRightStyle = NumberStyle.BOTH;
//			if (evenLeft + oddLeft == 0)
//				expectedLeftStyle = NumberStyle.NONE;
//			else if (evenLeft == 0 && oddLeft > 0 || evenLeft + oddLeft > 3 && evenLeft <= 1)
//				expectedLeftStyle = NumberStyle.ODD;
//			else if (evenLeft > 0 && oddLeft == 0 || evenLeft + oddLeft > 3 && oddLeft <= 1)
//				expectedLeftStyle = NumberStyle.EVEN;
//			if (evenRight + oddRight == 0)
//				expectedRightStyle = NumberStyle.NONE;
//			else if (evenRight == 0 && oddRight > 0 || evenRight + oddRight > 3 && evenRight <= 1)
//				expectedRightStyle = NumberStyle.ODD;
//			else if (evenRight > 0 && oddRight == 0 || evenRight + oddRight > 3 && oddRight <= 1)
//				expectedRightStyle = NumberStyle.EVEN;
			int oldBad = badMatches.size();
			filterWrongDuplicates(streetName, potentialNumbersThisRoad,badMatches);
			if (oldBad != badMatches.size())
				return;
			
			List<HousenumberMatch> leftNumbers = new ArrayList<HousenumberMatch>();
			List<HousenumberMatch> rightNumbers = new ArrayList<HousenumberMatch>();
			
			for (HousenumberMatch hr : potentialNumbersThisRoad) {
				if (hr.isLeft()) {
					leftNumbers.add(hr);
				} else {
					rightNumbers.add(hr);
				}
			}
//			checkLShapes(streetName, leftNumbers);
//			checkLShapes(streetName, rightNumbers);

			Collections.sort(leftNumbers, new HousenumberMatchComparator());
			Collections.sort(rightNumbers, new HousenumberMatchComparator());
//			System.out.println(r + " sorted " + potentialNumbersThisRoad);
//			System.out.println(r + " left   " + expectedLeftStyle + leftNumbers);
//			System.out.println(r + " right  " + expectedRightStyle + rightNumbers);
//			System.out.println(r + " " + oddLeft + " " + oddRight + " " + evenLeft +" " +  evenRight);
			log.info("Housenumbers for",r.getName(),r.getCity());
			log.info("Numbers:",potentialNumbersThisRoad);
			
			optimizeNumberIntervalLengths(streetName, r, potentialNumbersThisRoad);
			int currNodePos = 0;
			int nodeIndex = 0;
			int prevNumberNodeIndex = 0;
			int prevNodePos = 0;
			ExtNumbers numbersHead = null;
			ExtNumbers currNumbers = null;
			for (Coord p : r.getPoints()) {
				if (currNodePos == 0) {
					assert p instanceof CoordNode; 
				}

				// An ordinary point in the road.
				if (p.isNumberNode() == false) {
					currNodePos++;
					continue;
				}

				// The first time round, this is guaranteed to be a CoordNode
				if (currNodePos == 0) {
					nodeIndex++;
					currNodePos++;
					continue;
				}

				// Now we have a CoordNode and it is not the first one.
				ExtNumbers numbers = new ExtNumbers(r);
				numbers.setRnodNumber(prevNumberNodeIndex);
				int leftUsed = numbers.setNumbers(leftNumbers, prevNodePos, currNodePos, true);
				int rightUsed = numbers.setNumbers(rightNumbers, prevNodePos, currNodePos, false);
				prevNodePos = currNodePos;
				// maintain chain
				numbers.prev = currNumbers;
				if (currNumbers != null)
					currNumbers.next = numbers;
				else {
					numbersHead = numbers;
				}
				currNumbers = numbers;
				leftNumbers.subList(0, leftUsed).clear();
				rightNumbers.subList(0, rightUsed).clear(); 				

				prevNumberNodeIndex = nodeIndex;
				nodeIndex++;
				currNodePos++;
			}
			if (numbersHead == null)
				return;
			
			numbersHead = numbersHead.checkChainSegments(badMatches, usedNumbers);
			if (oldBad != badMatches.size())
				return;
			numbersHead = numbersHead.checkChainSegmentLengths(badMatches);
			if (oldBad != badMatches.size())
				return;
			numbersHead = numbersHead.checkChainPlausibility(streetName, potentialNumbersThisRoad, badMatches);
			if (oldBad != badMatches.size())
				return;
//			numbersHead = numbersHead.checkChainAsRoad(streetName, potentialNumbersThisRoad, badMatches);
//			if (oldBad != badMatches.size())
//				return;
			roadsWithNumbers.add(r);
			numberHeads.add(numbersHead);
			
		}
		if (roadsWithNumbers.size() > 1){
			checkWrongRoadAssignmments(streetName, numberHeads, badMatches);
		}
		for (int i = 0; i < roadsWithNumbers.size(); i++){
			roadsWithNumbers.get(i).setNumbers(numberHeads.get(i).getNumberList());
		}
	}

	
	private static void checkWrongRoadAssignmments(String streetName,
			ArrayList<ExtNumbers> numberHeads,
			MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		for (int loop = 0; loop < 10; loop++){
			boolean changed = false;
			for (int i = 0; i < numberHeads.size(); i++){
				ExtNumbers head1 = numberHeads.get(i);
				for (ExtNumbers en1 = head1; en1 != null; en1 = en1.next){
					if (en1.hasNumbers() == false)
						continue;
					for (int j = i+1; j < numberHeads.size(); j++){
						ExtNumbers head2 = numberHeads.get(j);
						for (ExtNumbers en2 = head2; en2 != null; en2 = en2.next){
							if (en2.hasNumbers() == false)
								continue;
							changed = ExtNumbers.checkIntervals(streetName, en1, en2, badMatches);
							if (changed){
								break;
							}
						}
						if (changed)
							break;
					}
					if (changed)
						break;
				}
				if (changed)
					break;
			}
			if (!changed)
				break;
		}
	}

	/**
	 * Detect parts of roads without house numbers and change start
	 * and end of the part to house number nodes (if not already)
	 * This increases the precision of the address search
	 * and costs only a few bytes. 
	 * @param streetName 
	 * @param r
	 * @param potentialNumbersThisRoad
	 */
	private static void optimizeNumberIntervalLengths(String streetName, MapRoad r,
			List<HousenumberMatch> potentialNumbersThisRoad) {
		int numPoints = r.getPoints().size();
		boolean optimizeStart = false;
		boolean optimizeEnd = false;
		BitSet segmentsWithNumbers = new BitSet();
		for (HousenumberMatch  hnm: potentialNumbersThisRoad){
			segmentsWithNumbers.set(hnm.getSegment());
			if (hnm.getSegment() == 0  && hnm.getSegmentFrac() < 0){
				optimizeStart = true;
			}
			if (hnm.getSegment() == numPoints-2 && hnm.getSegmentFrac() > 1){
				optimizeEnd = true;
			}
		}
		
		boolean searched = false;
		
		for (int i = 0; i < numPoints; i++){
			if (segmentsWithNumbers.get(i) == searched){
				changePointToNumberNode(r,i);
				searched = !searched;
			}
		}
		if (optimizeStart){
			changePointToNumberNode(r,1);
		}
		if (optimizeEnd){
			changePointToNumberNode(r,numPoints-2);
		}
	}

	
	private static void changePointToNumberNode(MapRoad r, int i) {
		Coord co = r.getPoints().get(i);
		if (co.isNumberNode() == false){
			log.info("road",r,"changing point",i,"to number node at",co.toDegreeString(),"to increase precision for house number search");
			co.setNumberNode(true);
			r.setInternalNodes(true);
		}
	}

	/**
	 * Identify duplicate numbers and remove those which are very far from
	 * other houses with same or near numbers.
	 * @param streetName
	 * @param sortedHouseNumbers
	 * @param badMatches
	 */
	private static void filterWrongDuplicates(
			String streetName, List<HousenumberMatch> sortedHouseNumbers, MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		List<HousenumberMatch> toIgnore = new ArrayList<>();
		HashMap<String,HousenumberMatch> signs = new HashMap<>();
		final int TO_SEARCH = 6;
		for (HousenumberMatch hnm : sortedHouseNumbers){
			if (hnm.isDuplicate() == false)
				continue;
			HousenumberMatch old = signs.put(hnm.getSign(),hnm);
			if (old != null){
				// found a duplicate house number
				boolean sameSide = (hnm.isLeft() == old.isLeft());
				int pos1 = Math.min(old.getSegment(), hnm.getSegment());
				int pos2 = Math.max(old.getSegment(), hnm.getSegment());
				double dist1 = old.getLocation().distance(hnm.getLocation());
				if (dist1 < 25 && sameSide && pos1 != pos2){
					useMidPos(streetName, hnm,old);
					continue;
				}
				if (dist1 > 100 || pos2 - pos1 > 0){
					List<HousenumberMatch> betweeen = new ArrayList<>();
					for (HousenumberMatch hnm2 : sortedHouseNumbers){
						if (hnm2.getSegment() < pos1 || hnm2.getSegment() > pos2 || hnm2 == old || hnm2 == hnm)
							continue;
						if (!sameSide || sameSide && hnm2.isLeft() == hnm.isLeft())
							betweeen.add(hnm2);
					}
					if (betweeen.isEmpty()){
						if (sameSide)
							useMidPos(streetName, hnm,old);
						continue;
					}
//					int oldPos = sortedHouseNumbers.indexOf(old);
//					int currPos = sortedHouseNumbers.indexOf(hnm);
//					int foundOld = 0, foundCurr = 0;
					double[] sumDist = new double[2];
					double[] sumDistSameSide = new double[2];
					int[] confirmed = new int[2];
					int[] falsified = new int[2];
					int[] found = new int[2];
					List<HousenumberMatch> dups = Arrays.asList(hnm, old);
					for (int i = 0; i < dups.size(); i++){
						HousenumberMatch other, curr;
						if (i == 0){
							curr = dups.get(0);
							other = dups.get(1);
						} else {
							curr = dups.get(1);
							other = dups.get(0);
						}
						int pos = sortedHouseNumbers.indexOf(curr);
						
						int left = pos - 1;
						int right = pos + 1;
						HousenumberMatch hnm2;
						int stillToFind = TO_SEARCH;
						while (stillToFind > 0){
							int oldDone = stillToFind;
							if (left >= 0){
								hnm2 = sortedHouseNumbers.get(left);
								if (hnm2 != other){
									double dist = curr.getLocation().distance(hnm2.getLocation());
									sumDist[i] += dist;
									if (hnm2.isLeft() == curr.isLeft()){
										sumDistSameSide[i] += dist;
									}
									if (curr.getHousenumber() == hnm2.getHousenumber()){
										if (dist < 20)
											confirmed[i]++;
									} else {
										if (dist < 10 )
											falsified[i]++;
									}
								}
								--left;
								stillToFind--;
								if (stillToFind == 0)
									break;
							}
							if (right < sortedHouseNumbers.size()){
								hnm2 = sortedHouseNumbers.get(right);
								if (hnm2 != other){
									double dist = curr.getLocation().distance(hnm2.getLocation());
									sumDist[i] += dist;
									if (hnm2.isLeft() == curr.isLeft()){
										sumDistSameSide[i] += dist;
									}
									if (curr.getHousenumber() == hnm2.getHousenumber()){
										if (dist < 20)
											confirmed[i]++;
									} else {
										if (dist < 10 )
											falsified[i]++;
									}
								}
								stillToFind--;
								right++;
							}
							if (oldDone == stillToFind)
								break;
						}
						found[i] = TO_SEARCH - 1 - stillToFind; 
					}
					log.error("dup check 1:", streetName, old, old.getElement().toBrowseURL());
					log.error("dup check 2:", streetName, hnm, hnm.getElement().toBrowseURL());
					log.error("confirmed",Arrays.toString(confirmed),"falsified",Arrays.toString(falsified),"sum-dist",Arrays.toString(sumDist),"sum-dist-same-side",Arrays.toString(sumDistSameSide));
					HousenumberMatch bad = null;
					if (confirmed[1] > 0 && confirmed[0] == 0  && falsified[1] == 0)
						bad = dups.get(0);
					else if (confirmed[0] > 0 && confirmed[1] == 0  && falsified[0] == 0)
						bad = dups.get(1);
					else if (found[0] > 3 && sumDist[0] > sumDist[1] && sumDistSameSide[0] > sumDistSameSide[1])
						bad = dups.get(0);
					else if (found[1] > 3 && sumDist[1] > sumDist[0] && sumDistSameSide[1] > sumDistSameSide[0])
						bad = dups.get(1);
					if (bad != null){
						toIgnore.add(bad);
					} else {
						// TODO: can't say that one is wrong, combine them?
						if (old.isLeft() == hnm.isLeft()){
							useMidPos(streetName, hnm, old);
						}
						else {
							log.error("don't know which one to use, ignoring both");
							toIgnore.add(old);
							toIgnore.add(hnm);
							
						}
					}
				}
			} 
		}
		for (HousenumberMatch hnm : toIgnore){
			badMatches.add(hnm, hnm.getRoad());
			log.warn("duplicate housenumber",streetName,hnm.getSign(),"is ignored for road with id",hnm.getRoad().getRoadDef().getId(),",house:",hnm.getElement().toBrowseURL());
			sortedHouseNumbers.remove(hnm);
		}
	}


	private static void useMidPos(String streetName, HousenumberMatch hnm1, HousenumberMatch hnm2) {
		int avgSegment = (hnm1.getSegment() + hnm2.getSegment()) / 2;
		BitSet toTest = new BitSet();
		toTest.set(avgSegment);
		if (avgSegment != hnm1.getSegment()){
			findClosestRoadSegment(hnm1, hnm1.getRoad(), toTest);
		}
		if (avgSegment != hnm2.getSegment()){
			findClosestRoadSegment(hnm2, hnm2.getRoad(), toTest);
		} 							
		log.error("using same segment for duplicate housenumbers", streetName, hnm2, hnm2.getElement().toBrowseURL(), hnm1.getElement().toBrowseURL());
	}


	/**
	 * Sorts house numbers by roads, road segments and position of the house number.
	 * @author WanMil
	 */
	public static class HousenumberMatchComparator implements Comparator<HousenumberMatch> {

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
	
	private static List<HousenumberMatch> checkPlausibility(String streetName, List<MapRoad> clusteredRoads,
			List<HousenumberMatch> housesNearCluster) {
		long countError = 0;
		List<HousenumberMatch> failed = new ArrayList<>();
		Int2IntOpenHashMap tested = new Int2IntOpenHashMap();
		tested.defaultReturnValue(-1);
		for (HousenumberMatch hnm : housesNearCluster){
			if (hnm.isIgnored())
				continue;
			int num = hnm.getHousenumber();
			int countPlaces = 0;
			int countRoads = 0;
			int prevRes = tested.get(num);
			if (prevRes == 0)
				continue;
			for (MapRoad r : clusteredRoads){
				int countMatches = checkRoad(r, hnm);
				if (countMatches == 0)
					continue;
				countRoads++;
				if (countMatches > 1){
					// TODO: verify how Garmin handles this when number is found in two consecutive number intervals
					log.error(streetName,num,hnm.getElement().toBrowseURL(),"is coded in",countMatches,"different road segments");
				}
				countPlaces += countMatches;
			}
			if (countPlaces == 1){
				tested.put(num,0);
				continue;
			}
			failed.add(hnm);
			++countError;
			if (countPlaces == 0 && hnm.getRoad() != null){
				log.error(streetName, num, hnm.getElement().toBrowseURL(), "is not found in expected road", hnm.getRoad());
			}
			if (countRoads > 1)
				log.error(streetName, num, hnm.getElement().toBrowseURL(), "is coded in", countRoads, "different roads");
		}
		if (countError > 0){
			log.error("plausibility check for road cluster failed with", countError, "errors:", clusteredRoads);
		}
		return failed; 
	}

	/**
	 * Count all segments that contain the house number
	 * @param r
	 * @param hnm
	 * @return
	 */
	private static int checkRoad(MapRoad r, HousenumberMatch hnm) {
		if (r.getNumbers() == null)
			return 0;

		int matches = 0;
		Numbers last = null;
		Numbers firstMatch = null;
		int hn = hnm.getHousenumber();
		for (Numbers numbers : r.getNumbers()){
			int n = numbers.countMatches(hn);
			if (numbers.getLeftNumberStyle() == NumberStyle.NONE && numbers.getRightNumberStyle() == NumberStyle.NONE)
				continue;
			if (n > 0 && firstMatch == null)
				firstMatch = numbers;
			
			if (n == 1 && matches > 0){
				if (last.getLeftEnd() == numbers.getLeftStart() && last.getLeftEnd() == hn || 
						last.getRightEnd() == numbers.getRightStart() && last.getRightEnd() == hn ||
						last.getLeftStart() == numbers.getLeftEnd() && last.getLeftStart() == hn||
						last.getRightStart() == numbers.getRightEnd() && last.getRightStart() == hn){
					n = 0; // intervals are overlapping, probably two houses (e.g. 2a,2b) at a T junction
				}
			}
			
			matches += n;
			if (numbers.getLeftNumberStyle() != NumberStyle.NONE || numbers.getRightNumberStyle() != NumberStyle.NONE)
				last = numbers;
		}
		return matches;
	}

	/**
	 * If the closest point to a road is a junction, try to find the road
	 * segment that forms a right angle with the house 
	 * @param closestMatch one match that is closest to the node
	 * @param otherMatches  the other matches with the same distance
	 * @return the best match
	 */
	private static HousenumberMatch checkAngle(HousenumberMatch closestMatch,
			List<HousenumberMatch> otherMatches) {
		
		if (otherMatches.isEmpty())
			return closestMatch;
		HousenumberMatch bestMatch = closestMatch;
		for (HousenumberMatch alternative : otherMatches){
			if (alternative == closestMatch)
				continue;
			if (closestMatch.getDistance() < alternative.getDistance())
				break;
			// a house has the same distance to different road objects
			// if this happens at a T-junction, make sure not to use the end of the wrong road 
			Coord c1 = closestMatch.getRoad().getPoints().get(closestMatch.getSegment());
			Coord c2 = closestMatch.getRoad().getPoints().get(closestMatch.getSegment()+1);
			Coord cx = closestMatch.getLocation();
			double dist = closestMatch.getDistance();
			double dist1 = cx.distance(c1);
			double angle, altAngle;
			if (dist1 == dist)
				angle = Utils.getAngle(c2, c1, cx);
			else 
				angle = Utils.getAngle(c1, c2, cx);
			Coord c3 = alternative.getRoad().getPoints().get(alternative.getSegment());
			Coord c4 = alternative.getRoad().getPoints().get(alternative.getSegment()+1);
			
			double dist3 = cx.distance(c3);
			if (dist3 == dist)
				altAngle = Utils.getAngle(c4, c3, cx);
			else 
				altAngle = Utils.getAngle(c3, c4, cx);
			double delta = 90 - Math.abs(angle);
			double deltaAlt = 90 - Math.abs(altAngle);
			if (delta > deltaAlt){
				bestMatch = alternative;
				c1 = c3;
				c2 = c4;
			} 
		}
		if (closestMatch.getRoad() != bestMatch.getRoad()){
			log.info("check angle: using road",bestMatch.getRoad().getRoadDef().getId(),"instead of",closestMatch.getRoad().getRoadDef().getId(),"for house number",bestMatch.getSign(),bestMatch.getElement().toBrowseURL());
		} else if (closestMatch != bestMatch){
			log.info("check angle: using road segment",bestMatch.getSegment(),"instead of",closestMatch.getSegment(),"for house number element",bestMatch.getElement().toBrowseURL());
		}
		return bestMatch;
	}

	/**
	 * Evaluates if the given point lies on the left side of the line spanned by spoint1 and spoint2.
	 * @param spoint1 first point of line
	 * @param spoint2 second point of line
	 * @param point the point to check
	 * @return {@code true} point lies on the left side; {@code false} point lies on the right side
	 */
	public static boolean isLeft(Coord spoint1, Coord spoint2, Coord point) {
		if (spoint1.distance(spoint2) == 0){
			log.error("road segment length is 0");
		}

		return ((spoint2.getHighPrecLon() - spoint1.getHighPrecLon())
				* (point.getHighPrecLat() - spoint1.getHighPrecLat()) - (spoint2
				.getHighPrecLat() - spoint1.getHighPrecLat())
				* (point.getHighPrecLon() - spoint1.getHighPrecLon())) > 0;
	}
	
	/**
	 * Calculates the distance to the given segment in meter.
	 * @param spoint1 segment point 1
	 * @param spoint2 segment point 2
	 * @param point point
	 * @return the distance in meter
	 */
	public static double distanceToSegment(Coord spoint1, Coord spoint2, Coord point, double frac) {

		if (frac <= 0) {
			return spoint1.distance(point);
		} else if (frac >= 1) {
			return spoint2.distance(point);
		} else {
			return point.distToLineSegment(spoint1, spoint2);
		}

	}
	
	/**
	 * Calculates the fraction at which the given point is closest to 
	 * the infinite line going through both points.
	 * @param spoint1 segment point 1
	 * @param spoint2 segment point 2
	 * @param point point
	 * @return the fraction (can be <= 0 or >= 1 if the perpendicular is not on
	 * the line segment between spoint1 and spoint2) 
	 */
	public static double getFrac(Coord spoint1, Coord spoint2, Coord point) {
		int aLon = spoint1.getHighPrecLon();
		int bLon = spoint2.getHighPrecLon();
		int pLon = point.getHighPrecLon();
		int aLat = spoint1.getHighPrecLat();
		int bLat = spoint2.getHighPrecLat();
		int pLat = point.getHighPrecLat();
		
		double deltaLon = bLon - aLon;
		double deltaLat = bLat - aLat;

		if (deltaLon == 0 && deltaLat == 0) 
			return 0;
		else {
			// scale for longitude deltas by cosine of average latitude  
			double scale = Math.cos(Coord.int30ToRadians((aLat + bLat + pLat) / 3) );
			double deltaLonAP = scale * (pLon - aLon);
			deltaLon = scale * deltaLon;
			if (deltaLon == 0 && deltaLat == 0)
				return 0;
			else 
				return (deltaLonAP * deltaLon + (pLat - aLat) * deltaLat) / (deltaLon * deltaLon + deltaLat * deltaLat);
		}
	}
	
	private static MultiHashMap<Integer, MapRoad> buildRoadClusters(List<MapRoad> roads){
		// find roads which are very close to each other
		MultiHashMap<Integer, MapRoad> clusters = new MultiHashMap<>();
		List<MapRoad> remaining = new ArrayList<>(roads);
		
		for (int i = 0; i < remaining.size(); i++){
			MapRoad r = remaining.get(i);
			Area bbox = r.getBounds();
			clusters.add(i, r);
			while (true){
				boolean changed = false;
				for (int j = remaining.size() - 1; j > i; --j){
					MapRoad r2 = remaining.get(j);
					Area bbox2 = r2.getBounds();
					boolean combine = false;
					if (bbox.intersects(bbox2))
						combine = true;
					else if (bbox.getCenter().distance(bbox2.getCenter()) < MAX_DISTANCE_TO_ROAD)
						combine = true;
					else {
						List<Coord> toCheck = bbox2.toCoords();
						for (Coord co1: bbox.toCoords()){
							for (Coord co2 : toCheck){
								double dist = co1.distance(co2);
								if (dist < MAX_DISTANCE_TO_ROAD){
									combine = true;
									break;
								}
							}
							if (combine)
								break;
						}
					}
					if (combine){
						clusters.add(i, r2);
						bbox = new Area(Math.min(bbox.getMinLat(), bbox2.getMinLat()), 
								Math.min(bbox.getMinLong(), bbox2.getMinLong()), 
								Math.max(bbox.getMaxLat(), bbox2.getMaxLat()), 
								Math.max(bbox.getMaxLong(), bbox2.getMaxLong()));
						remaining.remove(j);
						changed = true;
					}
				}
				if (!changed)
					break;
			} 
		}
		return clusters;
	}
	
	private static List<HousenumberMatch> convertElements(List<Element> elements){
		List<HousenumberMatch> houses = new ArrayList<HousenumberMatch>(
				elements.size());
		for (Element element : elements) {
			try {
				HousenumberMatch match = new HousenumberMatch(element);
				if (match.getLocation() == null) {
					// there has been a report that indicates match.getLocation() == null
					// could not reproduce so far but catching it here with some additional
					// information. (WanMil)
					log.error("OSM element seems to have no point.");
					log.error("Element: "+element.toBrowseURL()+" " +element);
					log.error("Please report on the mkgmap mailing list.");
					log.error("Continue creating the map. This should be possible without a problem.");
				} else {
					houses.add(match);
				}
			} catch (IllegalArgumentException exp) {
				log.debug(exp);
			}
		}
		return houses;
	}
}

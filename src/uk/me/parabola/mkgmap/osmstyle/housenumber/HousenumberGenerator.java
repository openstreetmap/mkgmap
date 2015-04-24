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
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.CityInfo;
import uk.me.parabola.mkgmap.general.LineAdder;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.HousenumberHooks;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.POIGeneratorHook;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.KdTree;
import uk.me.parabola.util.Locatable;
import uk.me.parabola.util.MultiHashMap;

/**
 * Collects all data required for OSM house number handling and adds the
 * house number information to the roads.
 * 
 * @author WanMil, Gerd Petermann
 */
public class HousenumberGenerator {
	private static final Logger log = Logger.getLogger(HousenumberGenerator.class);

	/** Gives the maximum distance between house number element and the matching road */
	public static final double MAX_DISTANCE_TO_ROAD = 150d;
	
	private boolean numbersEnabled;

	// options for handling of unnamed (service?) roads	
	private int nameSearchDepth = 3;

	private MultiHashMap<String, HousenumberIvl> interpolationWays;
	private List<MapRoad> allRoads;
	private Map<Long,Integer> interpolationNodes;
	private List<HousenumberElem> houseElems;
	private HashMap<CityInfo, CityInfo> cityInfos = new HashMap<>();

	private static final short housenumberTagKey1 =  TagDict.getInstance().xlate("mkgmap:housenumber");
	private static final short housenumberTagKey2 =  TagDict.getInstance().xlate("addr:housenumber");
	private static final short streetTagKey = TagDict.getInstance().xlate("mkgmap:street");
	private static final short addrStreetTagKey = TagDict.getInstance().xlate("addr:street");
	private static final short addrInterpolationTagKey = TagDict.getInstance().xlate("addr:interpolation");
	private static final short addrPlaceTagKey = TagDict.getInstance().xlate("addr:place");
	private static final short cityTagKey = TagDict.getInstance().xlate("mkgmap:city");
	private static final short regionTagKey = TagDict.getInstance().xlate("mkgmap:region");
	private static final short countryTagKey = TagDict.getInstance().xlate("mkgmap:country");
	private static final short postalCodeTagKey = TagDict.getInstance().xlate("mkgmap:postal_code");
	
	public HousenumberGenerator(EnhancedProperties props) {
		this.interpolationWays = new MultiHashMap<>();
		this.allRoads = new ArrayList<>();
		this.interpolationNodes = new HashMap<>();
		this.houseElems = new ArrayList<>();
		
		numbersEnabled = props.containsKey("housenumbers");
		int n = props.getProperty("name-service-roads", 3);
		if (n != nameSearchDepth){
			nameSearchDepth = Math.min(25, Math.max(2, n));
			if (nameSearchDepth != n)
				System.err.println("name-service-roads=" + n + " was changed to name-service-roads=" + nameSearchDepth);
		}
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
	 * Retrieves the house number of this element.
	 * @param e an OSM element
	 * @return the house number (or {@code null} if no house number set)
	 */
	public static String getHousenumber(Element e) {
		String res = e.getTag(housenumberTagKey1); 
		if (res != null)
			return res;
		return e.getTag(housenumberTagKey2);
	}
	
	/**
	 * Parses the house number string. It accepts the first positive number part
	 * of a string. So all leading and preceding non number parts are ignored.
	 * So the following strings are accepted:
	 * <table>
	 * <tr>
	 * <th>Input</th>
	 * <th>Output</th>
	 * </tr>
	 * <tr>
	 * <td>23</td>
	 * <td>23</td>
	 * </tr>
	 * <tr>
	 * <td>-23</td>
	 * <td>23</td>
	 * </tr>
	 * <tr>
	 * <td>21-23</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>Abc 21</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>Abc 21.45</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>21 Main Street</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>Main Street</td>
	 * <td><i>IllegalArgumentException</i></td>
	 * </tr>
	 * </table>
	 * @throws IllegalArgumentException if parsing fails
	 */
	private static Integer parseHousenumber(String housenumberString) {
		if (housenumberString == null) {
			return null;
		}
		
		// the housenumber must match against the pattern <anything>number<notnumber><anything>
		int housenumber;
		Pattern p = Pattern.compile("\\D*(\\d+)\\D?.*");
		Matcher m = p.matcher(housenumberString);
		if (m.matches() == false) {
			return null;
		}
		try {
			// get the number part and parse it
			housenumber = Integer.parseInt(m.group(1));
		} catch (NumberFormatException exp) {
			return null;
		}
		return housenumber;
	}

	
	private HousenumberElem parseElement(Element el, String sign){
		String city = el.getTag(cityTagKey);
		String region = el.getTag(regionTagKey);
		String country = el.getTag(countryTagKey);
		CityInfo ci = getCityInfos(city,region,country);
		HousenumberElem house = new HousenumberElem(el, ci);
		if (house.getLocation() == null){
			// there has been a report that indicates match.getLocation() == null
			// could not reproduce so far but catching it here with some additional
			// information. (WanMil)
			log.error("OSM element seems to have no point.");
			log.error("Element: " + el.toBrowseURL() + " " + el);
			log.error("Please report on the mkgmap mailing list.");
			log.error("Continue creating the map. This should be possible without a problem.");
			return house;
		}
		
		house.setSign(sign);
		Integer hn = parseHousenumber(sign);
		if (hn == null){
			if (log.isDebugEnabled())
				log.debug("No housenumber (", el.toBrowseURL(), "): ", sign);
			return house;
		}
		if (hn < 0 || hn > 1_000_000){
			log.warn("Number looks wrong, is ignored",house.getSign(),hn,"element",el.toBrowseURL());
			return house;
		}
		house.setHousenumber(hn);
		house.setStreet(getStreetname(el));
		house.setPlace(el.getTag(addrPlaceTagKey));
		house.setZipCode(el.getTag(postalCodeTagKey));
//		if (log.isDebugEnabled()){
//			if (city == null)
//				log.debug("mkgmap:city is not set in house number element",el.toBrowseURL(),el.toTagString());
//		}
		
		house.setValid(true);
		return house;
	}
	
	private CityInfo getCityInfos(String city, String region, String country) {
		CityInfo ci = new CityInfo(city, region, country);
		CityInfo ciOld = cityInfos.get(ci);
		if (ciOld != null)
			return ciOld;
//		log.debug(ci);
		cityInfos.put(ci, ci);
		return ci;
	}

	private HousenumberElem handleElement(Element el){
		String sign = getHousenumber(el);
		if (sign == null)
			return null;
		
		HousenumberElem he = parseElement(el, sign);
		if (he.isValid() == false)
			return null;
		houseElems.add(he);
		return he;
	}
	/**
	 * Adds a node for house number processing.
	 * @param n an OSM node
	 */
	public void addNode(Node n) {
		if (numbersEnabled == false) {
			return;
		}
		if ("true".equals(n.getTag(POIGeneratorHook.AREA2POI_TAG))){
			// ignore POI created for buildings
			return; 		
		}
		HousenumberElem houseElem = handleElement(n);
		if (houseElem == null)
			return;
		if (n.getTag(HousenumberHooks.partOfInterpolationTagKey) != null)
			interpolationNodes.put(n.getId(),houseElems.size()-1);
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
			// the way has the addr:interpolation=* tag, parse info
			// created by the HousenumberHook
			List<HousenumberElem> nodes = new ArrayList<>();
			String nodeIds = w.getTag(HousenumberHooks.mkgmapNodeIdsTagKey);
			if (nodeIds == null){
				// way was rejected by hook
			} else {
				String[] ids = nodeIds.split(",");
				for (String idString : ids){
					Long id = Long.decode(idString);
					Integer elemPos = interpolationNodes.get(id);
					if (elemPos != null){
						HousenumberElem node = houseElems.get(elemPos);
						if (node != null){
							assert node.getElement().getId() == id;
							nodes.add(node);
						}
					}
				}
				interpretInterpolationWay(w, nodes);
			}
			return;
		}
		
		if (w.hasIdenticalEndPoints()){
			// we are only interested in polygons now
			handleElement(w);
		}

	}
	
	/**
	 * Use the information provided by the addr:interpolation tag
	 * to generate additional house number elements. This increases
	 * the likelihood that a road segment is associated with the right 
	 * number ranges. 
	 * @param w the way
	 * @param nodes2 
	 * @param nodes list of nodes
	 */
	private void interpretInterpolationWay(Way w, List<HousenumberElem> nodes) {
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
			HousenumberElem he1 = nodes.get(i);
			HousenumberElem he2 = nodes.get(i+1);
			int pos1 = -1, pos2 = -1;
			for (int k = pos; k < w.getPoints().size(); k++){
				if (w.getPoints().get(k) == he1.getLocation()){
					pos1 = k;
					break;
				}
			}
			if (pos1 < 0){
				log.error("addr:interpolation node not found in way",w);
				return;
			}
			for (int k = pos1+1; k < w.getPoints().size(); k++){
				if (w.getPoints().get(k) == he2.getLocation()){
					pos2 = k;
					break;
				}
			}
			if (pos2 < 0){
				log.error("addr:interpolation node not found in way",w);
				return;
			}
			pos = pos2;
			String street = he1.getStreet();
			if (street != null && street.equals(he2.getStreet())){
				int start = he1.getHousenumber();
				int end = he2.getHousenumber();
				int steps;
				if (start < end){
					steps = (end - start) / step - 1;


				} else {
					steps = (start - end) / step - 1;

				}
				HousenumberIvl info = new HousenumberIvl(street, w, (Node)he1.element, (Node)he2.element);
				info.setStart(start);
				info.setEnd(end);
				info.setStep(step);
				info.setSteps(steps);
				info.setPoints(w.getPoints().subList(pos1, pos2+1));
				interpolationWays.add(street, info);
				if (start == end && he1.getSign().equals(he2.getSign())){
					// handle special case from CanVec imports  
					if (pos1 == 0 && pos2 +1 == w.getPoints().size()){
						info.setEqualEnds();
						log.warn(w.toBrowseURL(),"addr:interpolation way connects two points with equal numbers, numbers are ignored");
						return;
					}
				}
				if (steps <= 0){
					if (log.isDebugEnabled())
						log.debug(w.toBrowseURL(),"addr:interpolation way segment ignored, no number between",start,"and",end);
					info.setBad(true);
				}
				if ("even".equals(addrInterpolationMethod) && (start % 2 != 0 || end % 2 != 0)){
					if (log.isInfoEnabled())
						log.info(w.toBrowseURL(),"addr:interpolation=even is used with odd housenumber(s)",start,end);
					info.setBad(true);
				}
				if ("odd".equals(addrInterpolationMethod) && (start % 2 == 0 || end % 2 == 0)){
					if (log.isInfoEnabled())
						log.info(w.toBrowseURL(),"addr:interpolation=odd is used with even housenumber(s)",start,end);
					info.setBad(true);
				}
			}
		}
	}

	
	/**
	 * Adds a road to be processed by the house number generator.
	 * @param osmRoad the OSM way the defines the road 
	 * @param road a road
	 */
	public void addRoad(Way osmRoad, MapRoad road) {
		allRoads.add(road);
		if (numbersEnabled) {
			/*
			 * If the style adds the same OSM way as two or more routable ways, we use
			 * only the first. This ensures that we don't try to assign numbers from bad
			 * matches to these copies.
			 */
			if(road.getRoadDef().ferry())
				road.setSkipHousenumberProcessing(true);
			if(!road.isSkipHousenumberProcessing()){
				String name = road.getStreet(); 
				if (name != null) {
					if (log.isDebugEnabled())
						log.debug("Housenumber - Streetname:", name, "Way:",osmRoad.getId(),osmRoad.toTagString());
				}
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
						if ("associatedStreet".equals(relType)) 
							log.warn("Relation",r.toBrowseURL(),": don't know how to handle member with role",role);
						break;
					}
				}
			}
			String streetName = r.getTag("name");
			String streetNameFromRoads = null;
			List<Element> unnamedStreetElems = new ArrayList<>();
			boolean nameFromStreetsIsUnclear = false;
			if (streets.isEmpty() == false) {
				for (Element street : streets) {
					String roadName = street.getTag(streetTagKey);
					if (roadName == null) 
						roadName = street.getTag("name");
					if (roadName == null){
						unnamedStreetElems.add(street);
						continue;
					}
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
						if (unnamedStreetElems.isEmpty() == false){
							log.warn("Relation",r.toBrowseURL(),": ignored, street name is not clear.");
							return;
						}
						log.warn("Relation",r.toBrowseURL(),": street name is not clear, using the name from the way, not that of the relation.");
						streetName = streetNameFromRoads;
					} 
					else if (nameFromStreetsIsUnclear == true){
						log.warn("Relation",r.toBrowseURL(),": street name is not clear, using the name from the relation.");
					}
				} 
			}
			int countModHouses = 0;
			if (streetName != null && streetName.isEmpty() == false){
				for (Element house : houses) {
					if (addStreetTagFromRel(r, house, streetName) )
						countModHouses++;
				}
				for (Element street : unnamedStreetElems) {
					street.addTag(streetTagKey, streetName);
				}
			}
			if (log.isInfoEnabled()){
				if (countModHouses > 0 || !unnamedStreetElems.isEmpty()){
					if (countModHouses > 0)
						log.info("Relation",r.toBrowseURL(),": added tag mkgmap:street=",streetName,"to",countModHouses,"of",houses.size(),"house members" );
					if (!unnamedStreetElems.isEmpty())
						log.info("Relation",r.toBrowseURL(),": added tag mkgmap:street=",streetName,"to",unnamedStreetElems.size(),"of",streets.size(),"street members" );
				}
				else 
					log.info("Relation",r.toBrowseURL(),": ignored, no house or street member was changed");
			}

		}
	}
	
	/**
	 * Add the tag mkgmap:street=streetName to the element of the 
	 * relation if it does not already have a street name tag.
	 */
	private static boolean addStreetTagFromRel(Relation r, Element house, String streetName){
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
	
	
	/**
	 * 
	 * @param adder
	 * @param naxNodeId the highest nodeId used before
	 */
	public void generate(LineAdder adder, int naxNodeId) {
		if (numbersEnabled) {
			MultiHashMap<MapRoad,HousenumberMatch> initialHousesForRoads = findClosestRoadsToHouse();
			identifyServiceRoads();
			
			handleInterpolationWays(initialHousesForRoads);
			
			List<HousenumberRoad> hnrList = createHousenumberRoads(initialHousesForRoads);
			initialHousesForRoads = null;
			log.info("found",hnrList.size(),"road candidates for address search");
			
			useAddrPlaceTag(hnrList);
			Map<MapRoad, HousenumberRoad> road2HousenumberRoadMap = new HashMap<>();
			for (HousenumberRoad hnr : hnrList){
				road2HousenumberRoadMap.put(hnr.getRoad(), hnr);
			}
			Int2ObjectOpenHashMap<HashSet<MapRoad>> nodeId2RoadLists = new Int2ObjectOpenHashMap<>();
			for (MapRoad road : allRoads){
				for (Coord co : road.getPoints()){
					if (co.getId() == 0)
						continue;
					HashSet<MapRoad> connectedRoads = nodeId2RoadLists.get(co.getId());
					if (connectedRoads == null){
						connectedRoads = new HashSet<>();
						nodeId2RoadLists.put(co.getId(), connectedRoads);
					}
					connectedRoads.add(road);
				}
			}
			List<HousenumberRoad> addedRoads = new ArrayList<>();
			Iterator<HousenumberRoad> iter = hnrList.iterator();
			while (iter.hasNext()){
				HousenumberRoad hnr = iter.next();
				
				List<HousenumberMatch> lostHouses = hnr.checkStreetName(road2HousenumberRoadMap, nodeId2RoadLists);
				for (HousenumberMatch house : lostHouses){
					MapRoad r = house.getRoad();
					if (r != null){
						HousenumberRoad hnr2 = road2HousenumberRoadMap.get(r);
						if (hnr2 == null){
							CityInfo ci = getCityInfos(r.getCity(), r.getRegion(), r.getCountry());
							hnr2 = new HousenumberRoad(r, ci, Arrays.asList(house));
							road2HousenumberRoadMap.put(r,hnr2);
							addedRoads.add(hnr2);
						} else {
							hnr2.addHouse(house);
						}
					}
				}
				if (hnr.getName() == null){
					iter.remove();
					for (HousenumberMatch house : hnr.getHouses()){
						log.warn("found no plausible road name for address",house.getElement().toBrowseURL(),", closest road id:",house.getRoad());
					}
				}
			}
			hnrList.addAll(addedRoads);
			// TODO: interpolate addr:interpolation houses
			removeDupsGroupedByCityAndName(hnrList);
			
			TreeMap<CityInfo, TreeMap<String, List<HousenumberRoad>>> cityStreetnameRoadMap = new TreeMap<>();
			for (HousenumberRoad hnr : hnrList){
				TreeMap<String, List<HousenumberRoad>> cluster = cityStreetnameRoadMap.get(hnr.getRoadCityInfo());
				if (cluster == null){
					cluster = new TreeMap<>();
					cityStreetnameRoadMap.put(hnr.getRoadCityInfo(), cluster);
				}
				List<HousenumberRoad> roadsInCluster = cluster.get(hnr.getName());
				if (roadsInCluster == null){
					roadsInCluster = new ArrayList<>();
					cluster.put(hnr.getName(), roadsInCluster);
				}
				roadsInCluster.add(hnr);
			}
			for (Entry<CityInfo, TreeMap<String, List<HousenumberRoad>>> cityEntry : cityStreetnameRoadMap.entrySet()){
//				if (log.isDebugEnabled())
//					log.debug("processing roads in city",cityEntry.getKey());
				for (Entry<String, List<HousenumberRoad>> clusterEntry : cityEntry.getValue().entrySet()){
					List<HousenumberRoad> roadsInCluster = clusterEntry.getValue();
					String streetName = clusterEntry.getKey();
					
					useInterpolationInfo(streetName, roadsInCluster, road2HousenumberRoadMap);
					if (log.isDebugEnabled()){
						log.debug("processing road(s) with name",streetName,"in",cityEntry.getKey() );
					}
					for (HousenumberRoad hnr : roadsInCluster){
						hnr.buildIntervals();
					}
					boolean optimized = false;
					for (int loop = 0; loop < 10; loop++){
						for (HousenumberRoad hnr : roadsInCluster){
							hnr.checkIntervals();
						}
						checkWrongRoadAssignmments(roadsInCluster);
						boolean changed = hasChanges(roadsInCluster);
						if (!optimized && !changed){
							for (HousenumberRoad hnr : roadsInCluster){
								hnr.improveSearchResults();
							}
							changed = hasChanges(roadsInCluster);
							optimized = true;
						}
						if (!changed)
							break;
					}
					for (HousenumberRoad hnr : roadsInCluster){
						hnr.setNumbers();
					}
				}
			}
		}
			
		if (log.isInfoEnabled()){
			for (HousenumberElem house : houseElems){
				if (house.getRoad() == null){
					log.info("found no plausible road for house number element",house.getElement().toBrowseURL(),house);
				}
			}
		}
		for (MapRoad r : allRoads) {
			if (log.isDebugEnabled()){
				List<Numbers> finalNumbers = r.getRoadDef().getNumbersList();
				if (finalNumbers != null){
					log.info("id:"+r.getRoadDef().getId(),", final numbers");
					for (Numbers cn : finalNumbers){
						if (cn.isEmpty())
							continue;
						log.info("id:"+r.getRoadDef().getId(),", Left: ",cn.getLeftNumberStyle(),cn.getRnodNumber(),"Start:",cn.getLeftStart(),"End:",cn.getLeftEnd());
						log.info("id:"+r.getRoadDef().getId(),", Right:",cn.getRightNumberStyle(),cn.getRnodNumber(),"Start:",cn.getRightStart(),"End:",cn.getRightEnd());
					}
				}
			}
			adder.add(r);
		}
	}
	
	private List<HousenumberRoad> createHousenumberRoads(
			MultiHashMap<MapRoad, HousenumberMatch> initialHousesForRoads) {
		List<HousenumberRoad> hnrList = new ArrayList<>();
		for (MapRoad road : allRoads){
			if (road.isSkipHousenumberProcessing())
				continue;
			List<HousenumberMatch> houses = initialHousesForRoads.get(road);
			if (houses == null || houses.isEmpty())
				continue;
			CityInfo ci = getCityInfos(road.getCity(), road.getRegion(), road.getCountry());
			HousenumberRoad hnr = new HousenumberRoad(road, ci, houses);
			hnrList.add(hnr);
		}
		return hnrList;
	}

	private MultiHashMap<MapRoad, HousenumberMatch> findClosestRoadsToHouse() {
		// build road index
		long t1 = System.currentTimeMillis();
		RoadSegmentIndex roadSegmentIndex = new RoadSegmentIndex(allRoads, MAX_DISTANCE_TO_ROAD);
		long t2 = System.currentTimeMillis();
		log.debug("creation of road index took",t2-t1,"ms");
		
		long t3 = System.currentTimeMillis();
		MultiHashMap<MapRoad,HousenumberMatch> initialHousesForRoads = new MultiHashMap<>();
		for (int i = 0; i < houseElems.size(); i++){
			HousenumberElem house = houseElems.get(i);
			HousenumberMatch bestMatch = roadSegmentIndex.createHousenumberMatch(house);
			houseElems.set(i, bestMatch);
			if (bestMatch.getRoad() == null){
				bestMatch.setIgnored(true); // XXX maybe create a pseudo road with zero length?
//				log.warn("found no plausible road for house number element",house.getElement().toBrowseURL());
				continue;
			}
			house = bestMatch;
			initialHousesForRoads.add(house.getRoad(), bestMatch);
		}
		long t4 = System.currentTimeMillis();
		log.debug("identification of closest road for each house took",t4-t3,"ms");
		
		return initialHousesForRoads;
	}

	private void useAddrPlaceTag(List<HousenumberRoad> hnrList){
		HashMap<CityInfo,MultiHashMap<String,HousenumberMatch>> cityPlaceHouseMap = new LinkedHashMap<>();
		for (int i = 0; i < houseElems.size(); i++){
			HousenumberElem house = houseElems.get(i);
			if (house.getRoad() == null)
				continue;
			if (house.getPlace() == null)
				continue;
			MultiHashMap<String, HousenumberMatch> subMap = cityPlaceHouseMap.get(house.getCityInfo());
			if (subMap == null){
				subMap = new MultiHashMap<>();
				cityPlaceHouseMap.put(house.getCityInfo(), subMap);
			}
			subMap.add(house.getPlace(), (HousenumberMatch) house);
		}
		log.info("analysing",cityPlaceHouseMap.size(),"cities with addr:place=* houses" );
		for (Entry<CityInfo, MultiHashMap<String, HousenumberMatch>> topEntry : cityPlaceHouseMap.entrySet()){
			CityInfo cityInfo = topEntry.getKey();
			List<String> placeNames = new ArrayList<>(topEntry.getValue().keySet());
			Collections.sort(placeNames);
			for (String placeName : placeNames){
				List<HousenumberMatch> placeHouses = topEntry.getValue().get(placeName);
				HashSet<HousenumberRoad> roads = new LinkedHashSet<>();
				Int2IntOpenHashMap usedNumbers = new Int2IntOpenHashMap();
				HashMap<String,Integer> usedSigns = new HashMap<>();
				int dupSigns = 0;
				int dupNumbers = 0;
				int housesWithStreet = 0;
				int housesWithMatchingStreet = 0;
				int roadsWithNames = 0;
				int unnamedCloseRoads = 0;
				
				for (HousenumberMatch house : placeHouses){
					if (house.getStreet() != null ){
						++housesWithStreet;
						if (house.getStreet().equals(house.getRoad().getStreet())){
							++housesWithMatchingStreet;
						}
						
					} else {
						if (house.getRoad().getStreet() == null)
							++unnamedCloseRoads;
					}
					boolean added = roads.add(house.getHousenumberRoad());
					if (added && house.getRoad().getStreet() != null)
						++roadsWithNames;
					int oldCount = usedNumbers.put(house.getHousenumber(),1);
					if (oldCount != 0){
						usedNumbers.put(house.getHousenumber(), oldCount + 1);
						++dupNumbers;
					}
					Integer oldSignCount = usedSigns.put(house.getSign(), 1);
					if (oldSignCount != null){
						usedSigns.put(house.getSign(), oldSignCount + 1);
						++dupSigns;
					}
				}
				
				if (log.isDebugEnabled()){
					log.debug("place",placeName,"in city",cityInfo, ":", "houses:", placeHouses.size(),
							",duplicate numbers/signs:", dupNumbers+"/"+dupSigns,
							",roads (named/unnamed):", roads.size(),"("+roadsWithNames+"/"+(roads.size()- roadsWithNames)+")",
							",houses without addr:street:", placeHouses.size() - housesWithStreet,
							",street = name of closest road:", housesWithMatchingStreet,
							",houses without addr:street near named road:",	unnamedCloseRoads);
				}
				if ((float) dupSigns / placeHouses.size() < 0.25 ){
					if (log.isDebugEnabled())
						log.debug("will not use gaps in intervals for roads in",placeName );
					for (HousenumberRoad hnr : roads){
						hnr.setRemoveGaps(true);
					}
				}
				if (placeHouses.size() > housesWithStreet){ // XXX: threshold value?
					LongArrayList ids = new LongArrayList();
					for (HousenumberRoad hnr : roads){
						ids.add(hnr.getRoad().getRoadDef().getId());
						hnr.addPlaceName(placeName);
					}
					if (log.isDebugEnabled())
						log.debug("detected",placeName,"as potential address name for roads",ids);
				} else {
					if (log.isDebugEnabled())
						log.debug("will ignore addr:place for address search in",placeName,"in city",cityInfo);
				}
			}
		}
	}

	/**
	 * Update the house number information in the interpolation intervals and 
	 * use them to correct wrong road assignments.
	 * @param initialHousesForRoads map that is updated when wrong road assignments were found
	 */
	private void handleInterpolationWays(MultiHashMap<MapRoad, HousenumberMatch> initialHousesForRoads) {
		for (Entry<String, List<HousenumberIvl>> entry : interpolationWays.entrySet()){
			List<HousenumberIvl> infos = entry.getValue();
			for (HousenumberIvl info : infos){
				boolean isOK = info.setNodeRefs(interpolationNodes, houseElems);
				if (!isOK)
					continue;
				HousenumberMatch[] houses = info.getHouseNodes();
				MapRoad uncheckedRoads[] = new MapRoad[houses.length];
				for (int i = 0 ; i < houses.length; i++)
					uncheckedRoads[i] = houses[i].getRoad();
					
				isOK = info.checkRoads();
				if (!isOK)
					continue;
				// check if houses are assigned to different roads now
				houses = info.getHouseNodes();
				for (int i = 0 ; i < houses.length; i++){
					if (houses[i].getRoad() != uncheckedRoads[i]){
						initialHousesForRoads.removeMapping(uncheckedRoads[i], houses[i]);
						initialHousesForRoads.add(houses[i].getRoad(), houses[i]);
					}
				}
			}
		}
	}

	private void removeDupsGroupedByCityAndName(List<HousenumberRoad> hnrList){
		HashMap<CityInfo,MultiHashMap<String,HousenumberMatch>> cityNameHouseMap = new LinkedHashMap<>();
		for (int i = 0; i < houseElems.size(); i++){
			HousenumberElem house = houseElems.get(i);
			if (house.getRoad() == null)
				continue;
			if (house instanceof HousenumberMatch){
				HousenumberRoad hnr = ((HousenumberMatch) house).getHousenumberRoad();
				if (hnr.getName() == null)
					continue;
				MultiHashMap<String, HousenumberMatch> subMap = cityNameHouseMap.get(house.getCityInfo());
				if (subMap == null){
					subMap = new MultiHashMap<>();
					cityNameHouseMap.put(house.getCityInfo(), subMap);
				}
				subMap.add(hnr.getName(), (HousenumberMatch) house);
			}
		}
		
		for (Entry<CityInfo, MultiHashMap<String, HousenumberMatch>> topEntry : cityNameHouseMap.entrySet()){
			for (Entry<String, List<HousenumberMatch>> entry : topEntry.getValue().entrySet()){
				markSimpleDuplicates(entry.getKey(), entry.getValue());
			}
			
		}
	}	
	

	private static void checkSegment(HousenumberMatch house, MapRoad road, int seg){
		Coord cx = house.getLocation();
		Coord c0 = road.getPoints().get(seg);
		Coord c1 = road.getPoints().get(seg + 1);
		double frac = getFrac(c0, c1, cx);
		double dist = distanceToSegment(c0,c1,cx,frac);
		if (dist < house.getDistance()){
			house.setDistance(dist);
			house.setRoad(road);
			house.setSegment(seg);
			house.setSegmentFrac(frac);
		}
	}


	/**
	 * process option --x-name-service-roads=n
	 * The program identifies unnamed roads which are only connected to one
	 * road with a name or to multiple roads with the same name. The process is
	 * repeated n times. If n > 1 the program will also use unnamed roads which
	 * are connected to unnamed roads if those are connected to named roads.
	 * Higher values for n mean deeper search, but reasonable values are
	 * probably between 1 and 5.
	 * 
	 * These roads are then used for house number processing like the named
	 * ones. If house numbers are assigned to these roads, they are named so
	 * that address search will find them.
	 */
	private void identifyServiceRoads() {
		Int2ObjectOpenHashMap<String> roadNamesByNodeIds = new Int2ObjectOpenHashMap<>();
		MultiHashMap<MapRoad, Coord> coordNodesUnnamedRoads = new MultiHashMap<>();
		HashSet<Integer> unclearNodeIds = new HashSet<>();

		long t1 = System.currentTimeMillis();

		List<MapRoad> unnamedRoads = new ArrayList<>();
		for (MapRoad road : allRoads){
			if (road.isSkipHousenumberProcessing())
				continue;
			
			if (road.getStreet() == null){
				// if a road has a label but getStreet() returns null,
				// the road probably has a ref. We assume these are not service roads. 
				if (road.getName() == null){
					unnamedRoads.add(road);
					for (Coord co : road.getPoints()){
						if (co.getId() != 0)
							coordNodesUnnamedRoads.add(road, co);
					}
				}
			} else {
				identifyNodes(road.getPoints(), road.getStreet(), roadNamesByNodeIds, unclearNodeIds);
			}
		}
		int numUnnamedRoads = unnamedRoads.size();
		long t2 = System.currentTimeMillis();
		if (log.isDebugEnabled())
			log.debug("identifyServiceRoad step 1 took",(t2-t1),"ms, found",roadNamesByNodeIds.size(),"nodes to check and",numUnnamedRoads,"unnamed roads" );
		long t3 = System.currentTimeMillis();
		int named = 0;
		for (int pass = 1; pass <= nameSearchDepth; pass ++){
			int unnamed = 0;
			List<MapRoad> namedRoads = new ArrayList<>();
			for (int j = 0; j < unnamedRoads.size(); j++){
				MapRoad road = unnamedRoads.get(j);
				if (road == null)
					continue;
				unnamed++;
				List<Coord> coordNodes = coordNodesUnnamedRoads.get(road); 
				String name = null;
				for (Coord co : coordNodes){
					if (unclearNodeIds.contains(co.getId())){
						name = null;
						unnamedRoads.set(j, null); // don't process again
						break;
					}
					String possibleName = roadNamesByNodeIds.get(co.getId());
					if (possibleName == null)
						continue;
					if (name == null)
						name = possibleName;
					else if (name.equals(possibleName) == false){
						name = null;
						unnamedRoads.set(j, null); // don't process again
						break;
					}
				}
				if (name != null){
					named++;
					road.setStreet(name);
					namedRoads.add(road);
					unnamedRoads.set(j, null); // don't process again
				}
			}
			for (MapRoad road : namedRoads){
				String name = road.getStreet();
				if (log.isDebugEnabled())
					log.debug("pass",pass,"using unnamed road for housenumber processing,id=",road.getRoadDef().getId(),":",name);
				List<Coord> coordNodes = coordNodesUnnamedRoads.get(road); 
				identifyNodes(coordNodes, name, roadNamesByNodeIds, unclearNodeIds);
			}

			if (namedRoads.isEmpty())
				break;
			if (log.isDebugEnabled())
				log.debug("pass",pass,unnamed,named);
		}
		long t4 = System.currentTimeMillis();
		if (log.isDebugEnabled()){
			log.debug("indentifyServiceRoad step 2 took",(t4-t3),"ms, found a name for",named,"of",numUnnamedRoads,"roads" );
		}
		return;
	}

	private void identifyNodes(List<Coord> roadPoints,
			String streetName, Int2ObjectOpenHashMap<String> roadNamesByNodeIds, HashSet<Integer> unclearNodes) {
		for (Coord co : roadPoints){
			if (co.getId() != 0){
				String prevName = roadNamesByNodeIds.put(co.getId(), streetName);
				if (prevName != null){
					if (prevName.equals(streetName) == false)
						unclearNodes.add(co.getId());
				}
			}
		}			
	}


	/**
	 * Find house number nodes which are parts of addr:interpolation ways.
	 *  Check each addr:interpolation way for plausibility. 
	 *  If the check is OK, interpolate the numbers, if not, ignore 
	 *  also the numbers connected to the implausible way.
	 *  
	 *  XXX: Known problem: Doesn't work well when the road was
	 *  clipped at the tile boundary.
	 * @param streetName
	 * @param housesNearCluster
	 * @param roadsInCluster
	 * @param road2HousenumberRoadMap 
	 * @param interpolationInfos 
	 */
	private void useInterpolationInfo(String streetName,
			List<HousenumberRoad> roadsInCluster, Map<MapRoad, HousenumberRoad> road2HousenumberRoadMap) {
		List<HousenumberIvl> interpolationInfos = interpolationWays.get(streetName);
		if (interpolationInfos.isEmpty())
			return;
		
		List<HousenumberMatch> housesWithIvlInfo = new ArrayList<>();
		for (HousenumberRoad hnr : roadsInCluster){
			for (HousenumberMatch house : hnr.getHouses()){
				if (house.getIntervalInfoRefs() > 0)
					housesWithIvlInfo.add(house);
			}
		}
		if (housesWithIvlInfo.isEmpty())
			return;
		
		HashSet<String> simpleDupCheckSet = new HashSet<>();
		HashSet<HousenumberIvl> badIvls = new HashSet<>();
		Long2ObjectOpenHashMap<HousenumberIvl> id2IvlMap = new Long2ObjectOpenHashMap<>();
		Int2ObjectOpenHashMap<HousenumberMatch> interpolatedNumbers = new Int2ObjectOpenHashMap<>();
		Int2ObjectOpenHashMap<HousenumberMatch> existingNumbers = new Int2ObjectOpenHashMap<>();
		HashMap<HousenumberIvl, List<HousenumberMatch>> housesToAdd = new LinkedHashMap<>();
		
		for (HousenumberRoad hnr : roadsInCluster){
			for (HousenumberMatch house : hnr.getHouses())
				existingNumbers.put(house.getHousenumber(), house);
		}
		
		int inCluster = 0;
		boolean allOK = true;
		for (HousenumberIvl hivl : interpolationInfos){
			if (hivl.getId() == 35027547){
				long dd = 4;
			}

			if (hivl.inCluster(housesWithIvlInfo) == false)
				continue;
			++inCluster;
			if (hivl.checkRoads() == false){
				allOK = false;
			} else {
				String hivlDesc = hivl.getDesc();
				if (simpleDupCheckSet.contains(hivlDesc)){
					// happens often in Canada (CanVec imports): two or more addr:interpolation ways with similar meaning
					// sometimes at completely different road parts, sometimes at exactly the same
					log.warn("found additional addr:interpolation way with same meaning, is ignored:",streetName, hivl);
					badIvls.add(hivl);
					allOK = false;
					continue;
				}
				simpleDupCheckSet.add(hivlDesc);
				
				id2IvlMap.put(hivl.getId(), hivl);
				List<HousenumberMatch> interpolatedHouses = hivl.getInterpolatedHouses();
				if (interpolatedHouses.isEmpty() == false){
					if (interpolatedHouses.get(0).getRoad() == null){
						// the interpolated houses are not all along one road
						findRoadForInterpolatedHouses(streetName, interpolatedHouses, roadsInCluster);
					}
					
					boolean foundDup = false;
					for (HousenumberMatch house : interpolatedHouses){
						if (house.getRoad() == null || house.getDistance() > HousenumberIvl.MAX_INTERPOLATION_DISTANCE_TO_ROAD)
							continue;
						boolean ignoreGenOnly = false;
						HousenumberMatch old = interpolatedNumbers.put(house.getHousenumber(), house);
						if (old == null){
							ignoreGenOnly = true;
							old = existingNumbers.get(house.getHousenumber());
						}
						if (old != null){
							// forget both or only one ? Which one?
							house.setIgnored(true);
							if (old.getLocation().distance(house.getLocation()) > 5){
								foundDup = true;
								if (!ignoreGenOnly){
									old.setIgnored(true);
									long ivlId = old.getElement().getOriginalId();
									HousenumberIvl bad = id2IvlMap.get(ivlId);
									if (bad != null)
										badIvls.add(bad);
								}
							}
						}
					}
					if (foundDup)
						badIvls.add(hivl);
					else
						housesToAdd.put(hivl, interpolatedHouses);
				}
			}
		}
		if (inCluster == 0)
			return;
		for (HousenumberIvl badIvl: badIvls){
			allOK = false;
			badIvl.ignoreNodes();
			housesToAdd.remove(badIvl);
		}
		Iterator<Entry<HousenumberIvl, List<HousenumberMatch>>> iter = housesToAdd.entrySet().iterator();
		while (iter.hasNext()){
			Entry<HousenumberIvl, List<HousenumberMatch>> entry = iter.next();
			if (log.isInfoEnabled())
				log.info("using generated house numbers from addr:interpolation way",entry.getKey());
			for (HousenumberMatch house : entry.getValue()){
				if (house.getRoad() != null && house.isIgnored() == false){
					HousenumberRoad hnr = road2HousenumberRoadMap.get(house.getRoad());
					if (hnr == null){
						log.error("internal error: found no housenumber road for interpolated house",house.getElement().toBrowseURL());
						continue;
					}
					hnr.addHouse(house);
				}
			}
		}
		if (log.isDebugEnabled()){
			if (allOK)
				log.debug("found no problems with interpolated numbers from addr:interpolations ways for roads with name",streetName);
			else 
				log.debug("found problems with interpolated numbers from addr:interpolations ways for roads with name",streetName);
		}
	}
	
	private static void findRoadForInterpolatedHouses(String streetName,
			List<HousenumberMatch> houses,
			List<HousenumberRoad> roadsInCluster) {
		if (houses.isEmpty())
			return;
		Collections.sort(houses, new HousenumberMatchByNumComparator());
		
		HousenumberMatch prev = null;
		for (HousenumberMatch house : houses) {
			if (house.isIgnored())
				continue;
			house.setDistance(Double.POSITIVE_INFINITY); // make sure that we don't use an old match
			house.setRoad(null);
			List<HousenumberMatch> matches = new ArrayList<>();
			for (HousenumberRoad hnr : roadsInCluster){
				MapRoad r = hnr.getRoad();
				// make sure that we use the street info if available
				if (house.getPlace() != null){
					if (house.getStreet() != null && r.getStreet() != null && house.getStreet().equals(r.getStreet()) == false)
						continue;
				}
				HousenumberMatch test = new HousenumberMatch(house);
				findClosestRoadSegment(test, r);
				if (test.getRoad() != null && test.getGroup() != null || test.getDistance() < MAX_DISTANCE_TO_ROAD){
					matches.add(test);
				} else {
					long dd = 4;
				}
			}
			if (matches.isEmpty()){
				house.setIgnored(true);
				continue;
			}
			
			
			HousenumberMatch closest, best; 
			best = closest  = matches.get(0);
			
			if (matches.size() > 1){
				// multiple roads, we assume that the closest is the best
				// but we may have to check the alternatives as well
				
				Collections.sort(matches, new Comparator<HousenumberMatch>() {
					// sort by distance (smallest first)
					public int compare(HousenumberMatch o1, HousenumberMatch o2) {
						if (o1 == o2)
							return 0;
						int d = Double.compare(o1.getDistance(), o2.getDistance());
						if (d != 0)
							return d;
						return 0;
						
					}
				});
				closest  = matches.get(0);
				best = checkAngle(closest, matches);	
			}
			house.setDistance(best.getDistance());
			house.setSegmentFrac(best.getSegmentFrac());
			house.setRoad(best.getRoad());
			house.setSegment(best.getSegment());
			for (HousenumberMatch altHouse : matches){
				if (altHouse.getRoad() != best.getRoad() && altHouse.getDistance() < MAX_DISTANCE_TO_ROAD)
					house.addAlternativeRoad(altHouse.getRoad());
			}
				
			if (house.getRoad() == null) {
				house.setIgnored(true);
			} else {
				house.calcRoadSide();
			}
			// plausibility check for duplicate house numbers
			if (prev != null && prev.getHousenumber() == house.getHousenumber()){
				// duplicate number (e.g. 10 and 10 or 10 and 10A or 10A and 10B)
				if (prev.getSign().equals(house.getSign())){
					prev.setDuplicate(true);
					house.setDuplicate(true);
				}
			}
			
			if (house.getRoad() == null) {
				if (house.isIgnored() == false)
					log.warn("found no plausible road for house number element",house.getElement().toBrowseURL(),"(",streetName,house.getSign(),")");
			}
			if (!house.isIgnored())
				prev = house;
		}
	}

	
	private static void markSimpleDuplicates(String streetName, List<HousenumberMatch> housesNearCluster) {
		List<HousenumberMatch> sortedHouses = new ArrayList<>(housesNearCluster);
		Collections.sort(sortedHouses, new HousenumberMatchByNumComparator());
		int n = sortedHouses.size();
		for (int i = 1; i < n; i++){
			HousenumberMatch house1 = sortedHouses.get(i-1);
			if (house1.isIgnored())
				continue;
			HousenumberMatch house2 = sortedHouses.get(i);
			if (house2.isIgnored())
				continue;
			if (house1.getHousenumber() != house2.getHousenumber())
				continue;
			if (house1.getRoad() == house2.getRoad()){
				if (house1.isFarDuplicate())
					house2.setFarDuplicate(true);
				continue; // handled later
			}
			// we have two equal house numbers in different roads
			// check if they should be treated alike
			boolean markFarDup = false;
			double dist = house1.getLocation().distance(house2.getLocation());
			if (dist > 100)
				markFarDup = true;
			else {
				CityInfo city1 = house1.getCityInfo();
				CityInfo city2 = house2.getCityInfo();
				if (city1 != null && city1.equals(city2) == false){
					markFarDup = true;
				}
			}
			if (markFarDup){
				if (log.isDebugEnabled())
					log.debug("keeping duplicate numbers assigned to different roads in cluster ", streetName, house1,house2);
				house1.setFarDuplicate(true);
				house2.setFarDuplicate(true);
				continue;
			}
			boolean ignore2nd = false;
			if (dist < 30){
				ignore2nd = true;
			} else {
				Coord c1s = house1.getRoad().getPoints().get(house1.getSegment());
				Coord c1e = house1.getRoad().getPoints().get(house1.getSegment() + 1);
				Coord c2s = house2.getRoad().getPoints().get(house2.getSegment());
				Coord c2e = house2.getRoad().getPoints().get(house2.getSegment() + 1);
				if (c1s == c2s || c1s == c2e || c1e == c2s || c1e == c2e){
					// roads are directly connected
					ignore2nd = true;
				} 
			}
			if (ignore2nd){
				house2.setIgnored(true);
				if (log.isDebugEnabled()){
					if (house1.getSign().equals(house2.getSign()))
						log.debug("duplicate number is ignored",streetName,house2.getSign(),house2.getElement().toBrowseURL() );
					else 
						log.info("using",streetName,house1.getSign(), "in favor of",house2.getSign(),"as target for address search");
				}
			} else {
				if (log.isDebugEnabled())
					log.debug("keeping duplicate numbers assigned to different roads in cluster ", streetName, house1,house2);
				house1.setFarDuplicate(true);
				house2.setFarDuplicate(true);
			}
		}
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
			HousenumberMatch house1 = sortedHouses.get(pos1);
			if (house1.isIgnored() || house1.hasAlternativeRoad() == false)
				continue;
			int confirmed = 0;
			int falsified = 0;
			int pos2 = pos1;
			HousenumberMatch bestAlternative = null;
			double bestAlternativeDist = Double.POSITIVE_INFINITY;
			
			while (pos2 > 0){
				HousenumberMatch house2 = sortedHouses.get(pos2);
				if (house1.getHousenumber() - house2.getHousenumber() > 2)
					break;
					--pos2;
			}
			for (; pos2 < n; pos2++){
				if (confirmed > 0)
					break;
				if (pos2 == pos1)
					continue;
				HousenumberMatch house2 = sortedHouses.get(pos2);
				if (house2.isIgnored() || house2.getRoad() == null)
					continue;
				int deltaNum = house2.getHousenumber() - house1.getHousenumber();
				if (deltaNum > 2)
					break;
				if (deltaNum < -2)
					continue;
				double distHouses = house1.getLocation().distance(house2.getLocation());
				if (house2.getRoad() == house1.getRoad()){
					if (Math.abs(house1.getSegment() - house2.getSegment()) < 2){
						if (distHouses < 1.5 * bestAlternativeDist)
							confirmed++;
					} 
					continue;
				}
				
				Coord c1 = house2.getRoad().getPoints().get(house2.getSegment());
				Coord c2 = house2.getRoad().getPoints().get(house2.getSegment()+1);
				double frac2 = getFrac(c1,c2, house1.getLocation());
				double dist2 = distanceToSegment(c1,c2,house1.getLocation(),frac2);
				if (distHouses > dist2)
					continue;
				if (distHouses > house1.getDistance())
					continue;
				Coord c3 = house1.getRoad().getPoints().get(house1.getSegment());
				Coord c4 = house1.getRoad().getPoints().get(house1.getSegment()+1);
				if (c1 == c3 && Math.abs(Utils.getAngle(c2, c1, c4)) < 10 ||
						c1 == c4 && Math.abs(Utils.getAngle(c2, c1, c3)) < 10 ||
						c2 == c3 && Math.abs(Utils.getAngle(c1, c2, c4)) < 10 ||
						c2 == c4 && Math.abs(Utils.getAngle(c1, c2, c3)) < 10){
					confirmed++;
					continue;
				}
				++falsified;
				if (bestAlternative == null || dist2 < bestAlternativeDist){
					bestAlternative = house2;
					bestAlternativeDist = dist2;
				}
				if (log.isDebugEnabled())
					log.debug("road check house-1:",house1.getRoad(),house1,house1.getDistance(),",house-2:", house2.getRoad(),house2,house2.getDistance(),distHouses,dist2,frac2,"house-1 is falsified");
			}
			if (confirmed == 0 && falsified > 0){
				if (log.isInfoEnabled())
					log.info("house number element assigned to road",house1.getRoad(),house1,house1.getElement().toBrowseURL(),"is closer to more plausible houses at road",bestAlternative.getRoad());
				roadNumbers.removeMapping(house1.getRoad(), house1);
				Coord c1 = bestAlternative.getRoad().getPoints().get(bestAlternative.getSegment());
				Coord c2 = bestAlternative.getRoad().getPoints().get(bestAlternative.getSegment()+1);
				double frac2 = getFrac(c1,c2, house1.getLocation());
				double dist2 = distanceToSegment(c1,c2,house1.getLocation(),frac2);
				if (dist2 >= MAX_DISTANCE_TO_ROAD){
					log.info("house number element assigned to road",house1.getRoad(),house1,house1.getElement().toBrowseURL(),"is too far from more plausible road, is ignored");
					house1.setIgnored(true);
				} else {
					house1.setRoad(bestAlternative.getRoad());
					house1.setSegment(bestAlternative.getSegment());
					house1.setSegmentFrac(frac2);
					house1.setDistance(dist2);
					house1.setLeft(isLeft(c1, c2, house1.getLocation()));
					roadNumbers.add(house1.getRoad(), house1);
				} 
			} else if (confirmed == 0 && house1.isDuplicate()){
				// special ?
			}
		}
	}

	public static void findClosestRoadSegment(HousenumberMatch house, MapRoad r) {
		findClosestRoadSegment(house, r, 0, r.getPoints().size());
	}
	
	/**
	 * Fill/overwrite the fields in house which depend on the assigned road.  
	 */
	public static void findClosestRoadSegment(HousenumberMatch house, MapRoad r, int firstSeg, int stopSeg) {
		Coord cx = house.getLocation();
		double oldDist = house.getDistance();
		MapRoad oldRoad = house.getRoad();
		house.setRoad(null);
		house.setDistance(Double.POSITIVE_INFINITY);
		boolean foundGroupLink = false;
		int end = Math.min(r.getPoints().size(), stopSeg+1);
		for (int node = firstSeg; node + 1 < end; node++){
			Coord c1 = r.getPoints().get(node);
			Coord c2 = r.getPoints().get(node + 1);
			double frac = getFrac(c1, c2, cx);
			double dist = distanceToSegment(c1,c2,cx,frac);
			if (house.getGroup() != null && house.getGroup().linkNode == c1){
				if (c1.highPrecEquals(c2) == false){
					log.debug("block doesn't have zero length segment! Road:",r,house);
				}
				foundGroupLink = true;
				house.setDistance(dist);
				house.setSegmentFrac(frac);
				house.setRoad(r);
				house.setSegment(node);
				break;
			} else if (dist < house.getDistance()) {
				house.setDistance(dist);
				house.setSegmentFrac(frac);
				house.setRoad(r);
				house.setSegment(node);
			} 
		}
		
		if (house.getGroup() != null && house.getGroup().linkNode != null && foundGroupLink == false){
			log.debug(r,house,"has a group but the link was not found, should only happen after split of zero-length-segment");
		}
		if (oldRoad == r){
			if (house.getDistance() > MAX_DISTANCE_TO_ROAD + 2.5 && oldDist <= MAX_DISTANCE_TO_ROAD ){
				log.warn("line distorted? Road segment was moved by more than",
						String.format("%.2f m", 2.5), ", from address", r, house.getSign());
			}
		}
	}

	private static boolean hasChanges(
			List<HousenumberRoad> housenumberRoads) {
		for (HousenumberRoad hnr : housenumberRoads){
			if (hnr.isChanged())
				return true;
		}
		return false;
	}

	/**
	 * 
	 * @param streetName
	 * @param housenumberRoads
	 */
	private static void checkWrongRoadAssignmments(List<HousenumberRoad> housenumberRoads) {
		if (housenumberRoads.size() < 2)
			return;
		for (int loop = 0; loop < 10; loop++){
			boolean changed = false;
			for (int i = 0; i+1 < housenumberRoads.size(); i++){
				HousenumberRoad hnr1 = housenumberRoads.get(i);
				hnr1.setChanged(false);
				for (int j = i+1; j < housenumberRoads.size(); j++){
					HousenumberRoad hnr2 = housenumberRoads.get(j);
					hnr2.setChanged(false);
					hnr1.checkWrongRoadAssignmments(hnr2);
					if (hnr1.isChanged()){
						changed = true;
						hnr1.checkIntervals();
					}
					if (hnr2.isChanged()){
						changed = true;
						hnr2.checkIntervals();
					}
				}
			}
			if (!changed)
				return;
		}
	}

	/**
	 * Sorts house numbers by roads, road segments and position of the house number.
	 * @author WanMil
	 */
	public static class HousenumberMatchByPosComparator implements Comparator<HousenumberMatch> {

		public int compare(HousenumberMatch o1, HousenumberMatch o2) {
			if (o1 == o2) {
				return 0;
			}
			if (o1.getRoad() == null || o2.getRoad() == null){
				log.error("road is null in sort comparator",o1,o2);
				throw new MapFailedException("internal error in housenumber processing"); 
			}
			if (o1.getRoad() != o2.getRoad()) {
				// should not happen
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
			
			int d = o1.getHousenumber() - o2.getHousenumber();
			if (d != 0)
				return d;
			d = o1.getSign().compareTo(o2.getSign());
			if (d != 0)
				return d;
			return 0;
		}
		
	}
	
	/**
	 * Sorts house numbers by house number and segment 
	 * @author Gerd Petermann
	 */
	public static class HousenumberMatchByNumComparator implements Comparator<HousenumberMatch> {
		public int compare(HousenumberMatch o1, HousenumberMatch o2) {
			if (o1 == o2)
				return 0;
			int d = o1.getHousenumber() - o2.getHousenumber();
			if (d != 0)
				return d;
			d = o1.getSign().compareTo(o2.getSign());
			if (d != 0)
				return d;
			d = o1.getSegment() - o2.getSegment();
			if (d != 0)
				return d;
			double dDist = o1.getDistance() - o2.getDistance();
			if (dDist != 0d) {
				return (int)Math.signum(dDist);
			}
			if (d != 0)
				return d;
			d  = Long.compare(o1.getElement().getId(), o2.getElement().getId());
			return d;
		}
	}
	
	private static List<HousenumberMatch> checkPlausibility(String streetName, List<MapRoad> clusteredRoads,
			List<HousenumberMatch> housesNearCluster) {
		int countError = 0;
		int countTested = 0;
		List<HousenumberMatch> failed = new ArrayList<>();
		Int2IntOpenHashMap tested = new Int2IntOpenHashMap();
		tested.defaultReturnValue(-1);
		for (HousenumberMatch house : housesNearCluster){
			if (house.isIgnored())
				continue;
			++countTested;
			int num = house.getHousenumber();
			int countPlaces = 0;
			int countRoads = 0;
			int prevRes = tested.get(num);
			if (prevRes == 0)
				continue;
			boolean reported = false;
			for (MapRoad r : clusteredRoads){
				int countMatches = checkRoad(r, house.getHousenumber());
				if (countMatches == 0)
					continue;
				countRoads++;
				if (countMatches > 1){
					log.warn(streetName,house.getSign(),house.getElement().toBrowseURL(),"is coded in",countMatches,"different road segments");
					reported = true;
				}
				countPlaces += countMatches;
			}
			if (countPlaces == 1){
				tested.put(num,0);
				continue;
			}
			failed.add(house);
			++countError;
			
			if (countPlaces == 0 && house.getRoad() != null) {
				log.warn(streetName, house.getSign(), house.getElement().toBrowseURL(), "is not found in expected road", house.getRoad());
				reported = true;
			}
			if (countRoads > 1){
				log.warn(streetName, house.getSign(), house.getElement().toBrowseURL(), "is coded in", countRoads, "different roads");
				reported = true;
			}
			if (!reported)
				log.error(streetName, house.getSign(), house.getElement().toBrowseURL(), "unexpected result in plausibility check, counters:",countRoads, countPlaces);
		}
		if (countTested == 0)
			log.warn("plausibility check for road cluster found no valid numbers",clusteredRoads );
		else if (countError > 0)
			log.warn("plausibility check for road cluster failed with", countError, "detected problems:", clusteredRoads);
		else if (log.isInfoEnabled()) 
			log.info("plausibility check for road cluster found no problems", clusteredRoads);
		return failed; 
	}

	/**
	 * Count all segments that contain the house number
	 * @param r
	 * @param hn
	 * @return
	 */
	private static int checkRoad(MapRoad r, int hn) {
		if (r.getNumbers() == null)
			return 0;

		int matches = 0;
		Numbers last = null;
		Numbers firstMatch = null;
		for (Numbers numbers : r.getNumbers()){
			if (numbers.isEmpty())
				continue;
			int n = numbers.countMatches(hn);
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
		if (log.isInfoEnabled()){
			if (closestMatch.getRoad() != bestMatch.getRoad()){
				log.info("check angle: using road",bestMatch.getRoad().getRoadDef().getId(),"instead of",closestMatch.getRoad().getRoadDef().getId(),"for house number",bestMatch.getSign(),bestMatch.getElement().toBrowseURL());
			} else if (closestMatch != bestMatch){
				log.info("check angle: using road segment",bestMatch.getSegment(),"instead of",closestMatch.getSegment(),"for house number element",bestMatch.getElement().toBrowseURL());
			}
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
			log.warn("road segment length is 0 in left/right evaluation");
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

	/**
	 * @param length
	 * @return string with length, e.g. "0.23 m" or "116.12 m"
	 */
	public static String formatLen(double length){
		return String.format("%.2f m", length);
	}
	

	private static class RoadPoint implements Locatable{
		final Coord p;
		final MapRoad r;
		final int segment;
		final int partOfSeg;
		
		public RoadPoint(MapRoad road, Coord co, int s, int part) {
			this.p = co;
			this.r = road;
			this.segment = s;
			this.partOfSeg = part;
		}
		@Override
		public Coord getLocation() {
			return p;
		}
		@Override
		public String toString() {
			return r + " " + segment + " " + partOfSeg;
		}
	}

	/**
	 * A performance critical part:
	 * Index all road segments to be able to find all road segments within a given range
	 * around a point.  
	 * @author Gerd Petermann
	 *
	 */
	class RoadSegmentIndex {
		private final KdTree<RoadPoint> kdTree = new KdTree<>();
		private final Int2ObjectOpenHashMap<Set<RoadPoint>> nodeId2RoadPointMap = new Int2ObjectOpenHashMap<>(); 
		private final double range;
		private final double maxSegmentLength;
		private final double kdSearchRange;

		public RoadSegmentIndex(List<MapRoad> roads, double rangeInMeter) {
			this.range = rangeInMeter;
			this.maxSegmentLength = range * 2 / 3;
			this.kdSearchRange = Math.sqrt(Math.pow(rangeInMeter, 2) + Math.pow(maxSegmentLength/2, 2));
			build(roads);
			
		}

		public void build(List<MapRoad> roads){
			for (MapRoad road : roads){
				if (road.isSkipHousenumberProcessing())
					continue;
				List<Coord> points = road.getPoints();
				for (int i = 0; i + 1 < points.size(); i++){
					Coord c1 = points.get(i);
					Coord c2 = points.get(i + 1);
					int part = 0;
					addRealRoadPoint(new RoadPoint(road, c1, i, part++));
					while (true){
						double segLen = c1.distance(c2);
						double frac = maxSegmentLength / segLen;
						if (frac >= 1)
							break;
						// if points are not close enough, add extra point
						c1 = c1.makeBetweenPoint(c2, frac);
						kdTree.add(new RoadPoint(road, c1, i, part++));
						segLen -= maxSegmentLength;
					}
				}
				int last = points.size() - 1;
				addRealRoadPoint(new RoadPoint(road, points.get(last) , last, -1));
			}
		}
		
		private void addRealRoadPoint(RoadPoint rp){
			int id = rp.p.getId();
			if (id == 0){
				kdTree.add(rp);
				return;
			}
			// Coord node, add only once to KD-tree 
			Set<RoadPoint> rpOld = nodeId2RoadPointMap.get(id);
			if (rpOld == null){
				rpOld = new LinkedHashSet<>();
				nodeId2RoadPointMap.put(id, rpOld);
				kdTree.add(rp);
			}
			rpOld.add(rp);
		}
		
		public List<RoadPoint> getCLoseRoadPoints(HousenumberElem house){
			Set<RoadPoint> closeRoadPoints = kdTree.findNextPoint(house, kdSearchRange);
			List<RoadPoint> result = new ArrayList<>();
			for (RoadPoint rp : closeRoadPoints){
				int id = rp.p.getId();
				if (id != 0)
					result.addAll(nodeId2RoadPointMap.get(id));
				else 
					result.add(rp);
			}
			return result;
		}
		
		/**
		 * Find closest road segment and other plausible roads for a house
		 * @param house
		 * @return null if no road was found, else a {@link HousenumberMatch} instance 
		 */
		public HousenumberMatch createHousenumberMatch(HousenumberElem house){
			HousenumberMatch bestMatch = new HousenumberMatch(house);
			List<RoadPoint> closeRoadPoints = getCLoseRoadPoints(house);
			if (closeRoadPoints.isEmpty())
				return bestMatch;
			Map<MapRoad, BitSet> testedSegments = new HashMap<>();
			Map<MapRoad, HousenumberMatch> matches = new HashMap<>();
			double bestDist = Double.MAX_VALUE;
			double lastSameDist = Double.MAX_VALUE;
			for (RoadPoint rp : closeRoadPoints){
				if (house.getStreet() != null && house.getStreet().equals(rp.r.getStreet()) == false){
					if (rp.r.getStreet() != null){
						continue;
					}
				}
				
				BitSet bs = testedSegments.get(rp.r);
				HousenumberMatch hnm;
				if (bs == null){
					hnm = new HousenumberMatch(house);
					matches.put(rp.r, hnm);
					bs = new BitSet();
					testedSegments.put(rp.r, bs);
				} else {
					hnm = matches.get(rp.r);
				}
				double oldDist = hnm.getDistance();
				if (rp.partOfSeg >= 0){
					// rp.p is at start or before end of segment 
					if (bs.get(rp.segment) == false){
						bs.set(rp.segment);
						checkSegment(hnm, rp.r, rp.segment);
					}
				} 
				if (rp.partOfSeg == 0 && rp.segment > 0 || rp.partOfSeg < 0){
					// rp is at end of segment, check (also) the preceding segment 
					if (bs.get(rp.segment - 1) == false){
						bs.set(rp.segment-1);
						checkSegment(hnm, rp.r, rp.segment-1);
					}
				}
				if (oldDist == hnm.getDistance())
					continue;
				if (bestDist > hnm.getDistance()){
					bestDist = hnm.getDistance();
					bestMatch = hnm;
				} else if (bestDist == hnm.getDistance()){
					lastSameDist = bestDist;
				}
			}
			if (matches.isEmpty())
				return bestMatch;
			
			if (lastSameDist == bestDist){
				List<HousenumberMatch> sameDist = new ArrayList<>();
				for (HousenumberMatch hnm : matches.values()){
					if (bestDist == hnm.getDistance())
						sameDist.add(hnm);
				}
				checkAngle(bestMatch, sameDist);
			}
			if (bestMatch.getRoad() != null){
				for (HousenumberMatch altHouse : matches.values()){
					if (altHouse.getRoad() != bestMatch.getRoad() && altHouse.getDistance() < MAX_DISTANCE_TO_ROAD){
						if (house.getStreet() != null && altHouse.getDistance() > bestDist){
							if (house.getStreet().equals(altHouse.getRoad().getStreet()) == false)
								continue;
						}
						bestMatch.addAlternativeRoad(altHouse.getRoad());
					}
				}
				bestMatch.calcRoadSide();
			}
			return bestMatch;
		}
	}
}



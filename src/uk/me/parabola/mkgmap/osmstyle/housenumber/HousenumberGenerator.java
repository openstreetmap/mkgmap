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
import uk.me.parabola.mkgmap.general.ZipCodeInfo;
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
	/** Gives the maximum distance for different elements with the same address */
	public static final double MAX_DISTANCE_SAME_NUM = 100d;
	
	private boolean numbersEnabled;

	// options for handling of unnamed (service?) roads	
	private int nameSearchDepth = 3;

	private MultiHashMap<String, HousenumberIvl> interpolationWays;
	private List<MapRoad> allRoads;
	private Map<Long,Integer> interpolationNodes;
	private List<HousenumberElem> houseElems;
	private HashMap<CityInfo, CityInfo> cityInfos = new HashMap<>();
	private HashMap<ZipCodeInfo, ZipCodeInfo> zipInfos = new HashMap<>();

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
	private static final short numbersTagKey = TagDict.getInstance().xlate("mkgmap:numbers");
	
	public HousenumberGenerator(EnhancedProperties props) {
		this.interpolationWays = new MultiHashMap<>();
		this.allRoads = new ArrayList<>();
		this.interpolationNodes = new HashMap<>();
		this.houseElems = new ArrayList<>();
		
		numbersEnabled = props.containsKey("housenumbers");
		int n = props.getProperty("name-service-roads", 3);
		if (n != nameSearchDepth){
			nameSearchDepth = Math.min(25, Math.max(0, n));
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
			return null;
		}
		
		house.setSign(sign);
		Integer hn = parseHousenumber(sign);
		if (hn == null){
			if (log.isDebugEnabled())
				log.debug("No housenumber (", el.toBrowseURL(), "): ", sign);
			return null;
		}
		if (hn < 0 || hn > 1_000_000){
			log.warn("Number looks wrong, is ignored",house.getSign(),hn,"element",el.toBrowseURL());
			return null;
		}
		house.setHousenumber(hn);
		house.setStreet(getStreetname(el));
		house.setPlace(el.getTag(addrPlaceTagKey));
		String zipStr = el.getTag(postalCodeTagKey);
		ZipCodeInfo zip = getZipInfos(zipStr);
		house.setZipCode(zip);
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

	private ZipCodeInfo getZipInfos(String zipStr) {
		ZipCodeInfo zip = new ZipCodeInfo(zipStr);
		ZipCodeInfo zipOld = zipInfos.get(zip);
		if (zipOld != null)
			return zipOld;
		zipInfos.put(zip, zip);
		return zip;
	}

	private HousenumberElem handleElement(Element el){
		String sign = getHousenumber(el);
		if (sign == null)
			return null;
		
		HousenumberElem he = parseElement(el, sign);
		if (he == null)
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
		if("false".equals(n.getTag(numbersTagKey)))
			return;
		
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
		if("false".equals(w.getTag(numbersTagKey)))
			return;
		
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
		List<HousenumberIvl> hivls = new ArrayList<>();
		String streetName = null;
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
				if (streetName == null)
					streetName = street;
				else if (streetName.equals(street) == false){
					log.warn(w.toBrowseURL(),"addr:interpolation=even is used with different street names",streetName,street);
					return;
				}
				
				int start = he1.getHousenumber();
				int end = he2.getHousenumber();
				int steps;
				if (start < end){
					steps = (end - start) / step - 1;
				} else {
					steps = (start - end) / step - 1;
				}
				HousenumberIvl hivl = new HousenumberIvl(street, w, (Node)he1.element, (Node)he2.element);
				hivl.setStart(start);
				hivl.setEnd(end);
				hivl.setStep(step);
				hivl.setSteps(steps);
				hivl.setPoints(w.getPoints().subList(pos1, pos2+1));
//				if (pos1 > 0){
//					double angle = Utils.getAngle(w.getPoints().get(pos1-1), w.getPoints().get(pos1), w.getPoints().get(pos1+1));
//					if (Math.abs(angle) > 75){
//						log.warn(w.toBrowseURL(),"addr:interpolation way has sharp angle at number",start,"cannot use it");
//						return;
//					}
//						
//				}
				
				hivls.add(hivl);
				if ("even".equals(addrInterpolationMethod) && (start % 2 != 0 || end % 2 != 0)){
					log.warn(w.toBrowseURL(),"addr:interpolation=even is used with odd housenumber(s)",start,end);
					return;
				}
				if ("odd".equals(addrInterpolationMethod) && (start % 2 == 0 || end % 2 == 0)){
					log.warn(w.toBrowseURL(),"addr:interpolation=odd is used with even housenumber(s)",start,end);
					return;
				}
				
				if (start == end && he1.getSign().equals(he2.getSign())){
					// handle special case from CanVec imports  
					if (pos1 == 0 && pos2 +1 == w.getPoints().size()){
						hivl.setEqualEnds();
						log.warn(w.toBrowseURL(),"addr:interpolation way connects two points with equal numbers, numbers are ignored");
					}
				}
			}
		}
		for (HousenumberIvl  hivl : hivls)
			interpolationWays.add(streetName, hivl);
	}

	private MapRoad firstRoadSameOSMWay = null;
	/**
	 * Adds a road to be processed by the house number generator.
	 * @param osmRoad the OSM way the defines the road 
	 * @param road a road
	 */
	public void addRoad(Way osmRoad, MapRoad road) {
		allRoads.add(road);
		if (numbersEnabled) {
			if("false".equals(osmRoad.getTag(numbersTagKey))) 
				road.setSkipHousenumberProcessing(true);
			
			/*
			 * If the style adds the same OSM way as two or more routable ways, we use
			 * only the first. This ensures that we don't try to assign numbers from bad
			 * matches to these copies.
			 */
			if(!road.isSkipHousenumberProcessing()){
				if (firstRoadSameOSMWay != null){
					if (firstRoadSameOSMWay.getRoadDef().getId() == road.getRoadDef().getId()){
						if (firstRoadSameOSMWay.getPoints().equals(road.getPoints())){
							road.setSkipHousenumberProcessing(true);
							return;
						}
					}
				} 
				firstRoadSameOSMWay = road;
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
					street.addTag("name", streetName);
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
							if (r.getZip() != null)
								hnr2.setZipCodeInfo(getZipInfos(r.getZip()));
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
						log.warn("found no plausible road name for address",house.toBrowseURL(),", closest road id:",house.getRoad());
					}
				}
			}
			hnrList.addAll(addedRoads);
			// TODO: interpolate addr:interpolation houses
			removeDupsGroupedByCityAndName(hnrList);
			
			// group by street name and city
			TreeMap<String, TreeMap<CityInfo, List<HousenumberRoad>>> streetnameCityRoadMap = new TreeMap<>();
			for (HousenumberRoad hnr : hnrList){
				TreeMap<CityInfo, List<HousenumberRoad>> cluster = streetnameCityRoadMap.get(hnr.getName());
				if (cluster == null){
					cluster = new TreeMap<>();
					streetnameCityRoadMap.put(hnr.getName(), cluster);
				}
				List<HousenumberRoad> roadsInCluster = cluster.get(hnr.getRoadCityInfo());
				if (roadsInCluster == null){
					roadsInCluster = new ArrayList<>();
					cluster.put(hnr.getRoadCityInfo(), roadsInCluster);
				}
				roadsInCluster.add(hnr);
			}
			
			for (Entry<String, TreeMap<CityInfo, List<HousenumberRoad>>> streetNameEntry : streetnameCityRoadMap.entrySet()){
				String streetName = streetNameEntry.getKey();
				
				for (Entry<CityInfo, List<HousenumberRoad>> clusterEntry : streetNameEntry.getValue().entrySet()){
					useInterpolationInfo(streetName, clusterEntry.getValue(), road2HousenumberRoadMap);
				}
				
				for (Entry<CityInfo, List<HousenumberRoad>> clusterEntry : streetNameEntry.getValue().entrySet()){
					List<HousenumberRoad> roadsInCluster = clusterEntry.getValue();
					if (log.isDebugEnabled()){
						log.debug("processing road(s) with name",streetName,"in",clusterEntry.getKey() );
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
					if (house.getStreet() != null)
						log.info("found no plausible road for house number element",house.toBrowseURL(),house.getStreet(),house.getSign());
					else 
						log.info("found no plausible road for house number element",house.toBrowseURL());
				}
			}
		}
		for (MapRoad r : allRoads) {
			if (log.isDebugEnabled()){
				List<Numbers> finalNumbers = r.getRoadDef().getNumbersList();
				if (finalNumbers != null){
					log.info("id:"+r.getRoadDef().getId(),", final numbers,",r,"in",r.getCity());
					for (Numbers cn : finalNumbers){
						if (cn.isEmpty())
							continue;
						log.info("id:"+r.getRoadDef().getId(),", Left: ",cn.getLeftNumberStyle(),cn.getIndex(),"Start:",cn.getLeftStart(),"End:",cn.getLeftEnd());
						log.info("id:"+r.getRoadDef().getId(),", Right:",cn.getRightNumberStyle(),cn.getIndex(),"Start:",cn.getRightStart(),"End:",cn.getRightEnd());
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
			if (road.getZip() != null)
				hnr.setZipCodeInfo(getZipInfos(road.getZip()));
				
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
			initialHousesForRoads.add(bestMatch.getRoad(), bestMatch);
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
						if (house.getStreet().equalsIgnoreCase(house.getRoad().getStreet())){
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
				if (info.isBad()){
					continue;
				}
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
						if (houses[i].isIgnored() == false)
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
				HousenumberMatch hm = (HousenumberMatch) house;
				if (hm.isIgnored())
					continue;
				HousenumberRoad hnr = hm.getHousenumberRoad();
				if (hnr == null || hnr.getName() == null)
					continue;
				MultiHashMap<String, HousenumberMatch> subMap = cityNameHouseMap.get(hm.getCityInfo());
				if (subMap == null){
					subMap = new MultiHashMap<>();
					cityNameHouseMap.put(hm.getCityInfo(), subMap);
				}
				subMap.add(hnr.getName(), hm);
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
		HashMap<MapRoad, List<Coord>> coordNodesUnnamedRoads = new HashMap<>();
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
					List<Coord> nodes = new ArrayList<>();
					for (Coord co : road.getPoints()){
						if (co.getId() != 0)
							nodes.add(co);
					}
					coordNodesUnnamedRoads.put(road, nodes);
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
				road.setNamedByHousenumberProcessing(true);
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
		
		HashMap<String, HousenumberIvl> simpleDupCheckSet = new HashMap<>();
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
			if (hivl.inCluster(housesWithIvlInfo) == false || hivl.ignoreForInterpolation())
				continue;
			++inCluster;
			String hivlDesc = hivl.getDesc();
			HousenumberIvl hivlTest = simpleDupCheckSet.get(hivlDesc);
			if (hivlTest != null){
				// happens often in Canada (CanVec imports): two or more addr:interpolation ways with similar meaning
				// sometimes at completely different road parts, sometimes at exactly the same
				log.warn("found additional addr:interpolation way with same meaning, is ignored:",streetName, hivl, hivlTest);
				badIvls.add(hivl);
				allOK = false;
				continue;
			}
			simpleDupCheckSet.put(hivlDesc, hivl);

			id2IvlMap.put(hivl.getId(), hivl);
			List<HousenumberMatch> interpolatedHouses = hivl.getInterpolatedHouses();
			if (interpolatedHouses.isEmpty() == false){
				if (interpolatedHouses.get(0).getRoad() == null){
					// the interpolated houses are not all along one road
					findRoadForInterpolatedHouses(streetName, interpolatedHouses, roadsInCluster);
				}
				
				int dupCount = 0;
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
						double distToOld = old.getLocation().distance(house.getLocation()); 
						if (distToOld > MAX_DISTANCE_SAME_NUM){
							log.info("conflict caused by addr:interpolation way",streetName,hivl,"and address element",old,"at",old.getLocation().toDegreeString());
							dupCount++;
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
				if (dupCount > 0){
					log.warn("addr:interpolation way",streetName,hivl,"is ignored, it produces",dupCount,"duplicate number(s) too far from existing nodes");
					badIvls.add(hivl);
				}
				else
					housesToAdd.put(hivl, interpolatedHouses);
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
						log.error("internal error: found no housenumber road for interpolated house",house.toBrowseURL());
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
				
				Collections.sort(matches, new HousenumberGenerator.HousenumberMatchByDistComparator());
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
					log.warn("found no plausible road for house number element",house.toBrowseURL(),"(",streetName,house.getSign(),")");
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
			if (dist > MAX_DISTANCE_SAME_NUM)
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
						log.debug("duplicate number is ignored",streetName,house2.getSign(),house2.toBrowseURL() );
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
				return o1.getRoad().getRoadId() - o2.getRoad().getRoadId();
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
	/**
	 * Sorts house numbers by distance. If eqaul, compare segment and road to produce
	 * predictable results.  
	 * @author Gerd Petermann
	 */
	public static class HousenumberMatchByDistComparator implements Comparator<HousenumberMatch> {
		public int compare(HousenumberMatch o1, HousenumberMatch o2) {
			if (o1 == o2)
				return 0;
			int d = Double.compare(o1.getDistance(), o2.getDistance());
			if (d != 0)
				return d;
			d = Integer.compare(o1.getSegment(), o2.getSegment());
			if (d != 0)
				return d;
			d = Integer.compare(o1.getRoad().getRoadId(), o2.getRoad().getRoadId());
			if (d != 0)
				return d;
			return 0;
		}
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
		if (log.isDebugEnabled()){
			if (closestMatch.getRoad() != bestMatch.getRoad()){
				log.debug("check angle: using road",bestMatch.getRoad().getRoadDef().getId(),"instead of",closestMatch.getRoad().getRoadDef().getId(),"for house number",bestMatch.getSign(),bestMatch.toBrowseURL());
			} else if (closestMatch != bestMatch){
				log.debug("check angle: using road segment",bestMatch.getSegment(),"instead of",closestMatch.getSegment(),"for house number element",bestMatch.toBrowseURL());
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
				if (points.size() < 2)
					continue;

				List<RoadPoint> roadPoints = new ArrayList<>();
				RoadPoint rp;
				for (int i = 0; i + 1 < points.size(); i++){
					Coord c1 = points.get(i);
					Coord c2 = points.get(i + 1);
					int part = 0;
					rp = new RoadPoint(road, c1, i, part++);
					roadPoints.add(rp);
					while (true){
						double segLen = c1.distance(c2);
						double frac = maxSegmentLength / segLen;
						if (frac >= 1)
							break;
						// if points are not close enough, add extra point
						c1 = c1.makeBetweenPoint(c2, frac);
						rp = new RoadPoint(road, c1, i, part++);
						roadPoints.add(rp);
						segLen -= maxSegmentLength;
					}
				}
				int last = points.size() - 1;
				rp = new RoadPoint(road, points.get(last) , last, -1);
				roadPoints.add(rp);
				
				Collections.shuffle(roadPoints);
				for (RoadPoint toAdd : roadPoints){
					int id = toAdd.p.getId();
					if (id == 0)
						kdTree.add(toAdd);
					else {
						// Coord node, add only once to KD-tree with all roads
						Set<RoadPoint> set = nodeId2RoadPointMap.get(id);
						if (set == null){
							set = new LinkedHashSet<>();
							nodeId2RoadPointMap.put(id, set);
							kdTree.add(toAdd);
						}
						set.add(toAdd);
					}  		
				}
			}
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
			HousenumberMatch closest = new HousenumberMatch(house);
			List<RoadPoint> closeRoadPoints = getCLoseRoadPoints(house);
			if (closeRoadPoints.isEmpty())
				return closest;
			Collections.sort(closeRoadPoints, new Comparator<RoadPoint>() {
				// sort by distance (smallest first)
				public int compare(RoadPoint o1,  RoadPoint o2) {
					if (o1 == o2)
						return 0;
					int d = Integer.compare(o1.r.getRoadId(), o2.r.getRoadId());
					if (d != 0)
						return d;
					d = Integer.compare(o1.segment, o2.segment);
					if (d != 0)
						return d; 
					return Integer.compare(o1.partOfSeg, o2.partOfSeg);
				}
			});

			List<HousenumberMatch> matches = new ArrayList<>(40);
			BitSet testedSegments = new BitSet();
			MapRoad lastRoad = null;
			HousenumberMatch hnm = null;
			for (RoadPoint rp : closeRoadPoints){
				if (house.getStreet() != null){
					// we have a given street name, accept only roads with similar name or no name
					if (rp.r.getStreet() != null && house.getStreet().equalsIgnoreCase(rp.r.getStreet()) == false)
						continue;
				}
				if (rp.r != lastRoad){
					hnm = new HousenumberMatch(house);
					testedSegments.clear();
					matches.add(hnm);
					lastRoad = rp.r;
				}
				double oldDist = hnm.getDistance();
				if (rp.partOfSeg >= 0){
					// rp.p is at start or before end of segment 
					if (testedSegments.get(rp.segment) == false){
						testedSegments.set(rp.segment);
						checkSegment(hnm, rp.r, rp.segment);
					}
				} 
				if (rp.partOfSeg < 0){
					// rp is at end of road, check (also) the preceding segment 
					if (rp.segment < 1){
						log.error("internal error: trying to use invalid roadPoint",rp);
					} else if (testedSegments.get(rp.segment - 1) == false){
						testedSegments.set(rp.segment-1);
						checkSegment(hnm, rp.r, rp.segment-1);
					}
				}
				if (oldDist == hnm.getDistance())
					continue;
			}
			if (matches.isEmpty())
				return closest; // closest has not yet a road
			
			Collections.sort(matches, new HousenumberGenerator.HousenumberMatchByDistComparator());
			closest = matches.get(0);
			closest = checkAngle(closest, matches);
			closest.calcRoadSide();
			HousenumberMatch bestMatchingName = null; 
			if (closest.getStreet() != null && closest.getStreet().equalsIgnoreCase(closest.getRoad().getStreet()))
				bestMatchingName = closest;
			
			for (HousenumberMatch altHouse : matches){
				if (altHouse.getDistance() >= MAX_DISTANCE_TO_ROAD)
					break;
				if (altHouse.getRoad() != closest.getRoad()){
					if (house.getStreet() != null && altHouse.getDistance() > closest.getDistance()){
						if (house.getStreet().equalsIgnoreCase(altHouse.getRoad().getStreet())){
							if (bestMatchingName == null || bestMatchingName.getDistance() > altHouse.getDistance()){
								bestMatchingName = altHouse;
							}
						} else {
							if (bestMatchingName != null && altHouse.getDistance() > bestMatchingName.getDistance())
								continue;
						}
					}
					closest.addAlternativeRoad(altHouse.getRoad());
				}
			}
			if (bestMatchingName != null){
				if (house.getStreet().equals(bestMatchingName.getRoad().getStreet()) == false){
					log.warn("accepting match in spite of different capitalisation" , house.getStreet(),house.getSign(), bestMatchingName.getRoad().getRoadDef(), "house:",house.toBrowseURL());
					bestMatchingName.setStreet(bestMatchingName.getRoad().getStreet());
					closest.setStreet(bestMatchingName.getStreet());
				}
			}
			if (closest == bestMatchingName || bestMatchingName == null || bestMatchingName.getDistance() > MAX_DISTANCE_TO_ROAD)
				return closest;
			
			double ratio = closest.getDistance() / bestMatchingName.getDistance();
			if (ratio < 0.25)
				return closest;
			HousenumberMatch best = closest;
			if (ratio > 0.75){
				// prefer the road with the matching name
				for (MapRoad r : closest.getAlternativeRoads()){
					if (house.getStreet().equalsIgnoreCase(r.getStreet()))
						bestMatchingName.addAlternativeRoad(r);
				}
				best = bestMatchingName;
				best.calcRoadSide();
			} else {
				if (log.isDebugEnabled()){
					log.debug("further checks needed for address", closest.getStreet(), closest.getSign(), closest.toBrowseURL(), 
							formatLen(closest.getDistance()), formatLen(bestMatchingName.getDistance()));
				}

			}
			return best;
		}

	}
	
}



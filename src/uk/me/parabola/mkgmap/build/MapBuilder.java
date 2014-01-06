/*
 * Copyright (C) 2007 - 2012.
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

package uk.me.parabola.mkgmap.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Exit;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.Country;
import uk.me.parabola.imgfmt.app.lbl.ExitFacility;
import uk.me.parabola.imgfmt.app.lbl.Highway;
import uk.me.parabola.imgfmt.app.lbl.LBLFile;
import uk.me.parabola.imgfmt.app.lbl.POIRecord;
import uk.me.parabola.imgfmt.app.lbl.Region;
import uk.me.parabola.imgfmt.app.lbl.Zip;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.imgfmt.app.net.NETFile;
import uk.me.parabola.imgfmt.app.net.NODFile;
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.imgfmt.app.net.RouteCenter;
import uk.me.parabola.imgfmt.app.trergn.ExtTypeAttributes;
import uk.me.parabola.imgfmt.app.trergn.Overview;
import uk.me.parabola.imgfmt.app.trergn.Point;
import uk.me.parabola.imgfmt.app.trergn.PointOverview;
import uk.me.parabola.imgfmt.app.trergn.Polygon;
import uk.me.parabola.imgfmt.app.trergn.PolygonOverview;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.imgfmt.app.trergn.PolylineOverview;
import uk.me.parabola.imgfmt.app.trergn.RGNFile;
import uk.me.parabola.imgfmt.app.trergn.RGNHeader;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.imgfmt.app.trergn.TREFile;
import uk.me.parabola.imgfmt.app.trergn.Zoom;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.Version;
import uk.me.parabola.mkgmap.combiners.OverviewBuilder;
import uk.me.parabola.mkgmap.filters.BaseFilter;
import uk.me.parabola.mkgmap.filters.DouglasPeuckerFilter;
import uk.me.parabola.mkgmap.filters.FilterConfig;
import uk.me.parabola.mkgmap.filters.LineMergeFilter;
import uk.me.parabola.mkgmap.filters.LinePreparerFilter;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
import uk.me.parabola.mkgmap.filters.MapFilter;
import uk.me.parabola.mkgmap.filters.MapFilterChain;
import uk.me.parabola.mkgmap.filters.PolygonSplitterFilter;
import uk.me.parabola.mkgmap.filters.PreserveHorizontalAndVerticalLinesFilter;
import uk.me.parabola.mkgmap.filters.RemoveEmpty;
import uk.me.parabola.mkgmap.filters.RemoveObsoletePointsFilter;
import uk.me.parabola.mkgmap.filters.RoundCoordsFilter;
import uk.me.parabola.mkgmap.filters.ShapeMergeFilter;
import uk.me.parabola.mkgmap.filters.SizeFilter;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapExitPoint;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.RoadNetwork;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.overview.OverviewMapDataSource;
import uk.me.parabola.util.Configurable;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This is the core of the code to translate from the general representation
 * into the garmin representation.
 *
 * We need to go through the data several times, once for each level, filter
 * out features that are not required at the level and simplify paths for
 * lower resolutions if required.
 *
 * @author Steve Ratcliffe
 */
public class MapBuilder implements Configurable {
	private static final Logger log = Logger.getLogger(MapBuilder.class);
	private static final int CLEAR_TOP_BITS = (32 - 15);
	
	private static final int MIN_SIZE_LINE = 1;

	private final java.util.Map<MapPoint,POIRecord> poimap = new HashMap<MapPoint,POIRecord>();
	private final java.util.Map<MapPoint,City> cityMap = new HashMap<MapPoint,City>();
	private List<String> mapInfo = new ArrayList<String>();
	private List<String> copyrights = new ArrayList<String>();

	private boolean doRoads;
	private boolean routingErrorMsgPrinted;

	private Locator locator;

	private final java.util.Map<String, Highway> highways = new HashMap<String, Highway>();

	/** name that is used for cities which name are unknown */
	private final static String UNKNOWN_CITY_NAME = "";

	private Country defaultCountry;
	private String countryName = "COUNTRY";
	private String countryAbbr = "ABC";
	private String regionName;
	private String regionAbbr;
	
	private Set<String> locationAutofill;

	private int minSizePolygon;
	private String polygonSizeLimitsOpt;
	private HashMap<Integer,Integer> polygonSizeLimits = null;
	private double reducePointError;
	private double reducePointErrorPolygon;
	private boolean mergeLines;
	private boolean mergeShapes;

	private boolean	poiAddresses;
	private int		poiDisplayFlags;
	private boolean enableLineCleanFilters = true;
	private boolean makePOIIndex;
	private int routeCenterBoundaryType;
	
	private LBLFile lblFile;

	private String licenseFileName;

	public MapBuilder() {
		regionName = null;
		locationAutofill = Collections.emptySet();
		locator = new Locator();
	}

	public void config(EnhancedProperties props) {

		countryName = props.getProperty("country-name", countryName);
		countryAbbr = props.getProperty("country-abbr", countryAbbr);
		regionName = props.getProperty("region-name", null);
		regionAbbr = props.getProperty("region-abbr", null);
 		minSizePolygon = props.getProperty("min-size-polygon", 8);
 		polygonSizeLimitsOpt = props.getProperty("polygon-size-limits", null);
		reducePointError = props.getProperty("reduce-point-density", 2.6);
 		reducePointErrorPolygon = props.getProperty("reduce-point-density-polygon", -1);
		if (reducePointErrorPolygon == -1)
			reducePointErrorPolygon = reducePointError;
		mergeLines = props.containsKey("merge-lines");
		mergeShapes = props.getProperty("merge-shapes",false);

		makePOIIndex = props.getProperty("make-poi-index", false);

		if(props.getProperty("poi-address") != null)
			poiAddresses = true;

		routeCenterBoundaryType = props.getProperty("route-center-boundary", 0);

		licenseFileName = props.getProperty("license-file", null);
		
		locationAutofill = LocatorUtil.parseAutofillOption(props);
		
		locator = new Locator(props);
		locator.setDefaultCountry(countryName, countryAbbr);
	}

	/**
	 * Main method to create the map, just calls out to several routines
	 * that do the work.
	 *
	 * @param map The map.
	 * @param src The map data.
	 */
	public void makeMap(Map map, LoadableMapDataSource src) {

		RGNFile rgnFile = map.getRgnFile();
		TREFile treFile = map.getTreFile();
		lblFile = map.getLblFile();
		NETFile netFile = map.getNetFile();

		if(routeCenterBoundaryType != 0 &&
		   netFile != null &&
		   src instanceof MapperBasedMapDataSource) {
			for(RouteCenter rc : src.getRoadNetwork().getCenters()) {
				((MapperBasedMapDataSource)src).addBoundaryLine(rc.getArea(), routeCenterBoundaryType, rc.reportSizes());
			}
		}
		if (mapInfo.isEmpty())
			getMapInfo();

		normalizeCountries(src);
		
		processCities(map, src);
		processRoads(map,src);
		processPOIs(map, src);
		processOverviews(map, src);
		processInfo(map, src);
		makeMapAreas(map, src);

		treFile.setLastRgnPos(rgnFile.position() - RGNHeader.HEADER_LEN);

		rgnFile.write();
		treFile.write(rgnFile.haveExtendedTypes());
		treFile.writePost();
		lblFile.write();
		lblFile.writePost();

		if (netFile != null) {
			RoadNetwork network = src.getRoadNetwork();
			netFile.setNetwork(network.getRoadDefs());
			NODFile nodFile = map.getNodFile();
			if (nodFile != null) {
				nodFile.setNetwork(network.getCenters(), network.getRoadDefs(), network.getBoundary());
				nodFile.write();
			}
			netFile.write(lblFile.numCities(), lblFile.numZips());

			if (nodFile != null) {
				nodFile.writePost();
			}
			netFile.writePost(rgnFile.getWriter());
		}
	}
	
	private Country getDefaultCountry() {
		if (defaultCountry == null && lblFile != null) {
			defaultCountry = lblFile.createCountry(countryName, countryAbbr);
		}
		return defaultCountry;
	}
	
	/**
	 * Retrieves the region with the default name in the given country.
	 * @param country the country ({@code null} = use default country)
	 * @return the default region in the given country ({@code null} if not available)
	 */
	private Region getDefaultRegion(Country country) {
		if (lblFile==null || regionName == null) {
			return null;
		}
		if (country == null) {
			if (getDefaultCountry() == null) {
				return null;
			} else {
				return lblFile.createRegion(getDefaultCountry(), regionName, regionAbbr);
			}
		} else {
			return lblFile.createRegion(country, regionName, regionAbbr);
		}
	}

	/**
	 * Process the country names of all elements and normalize them
	 * so that one consistent country name is used for the same country 
	 * instead of different spellings.
	 * @param src the source of elements
	 */
	private void normalizeCountries(MapDataSource src) {
		for (MapPoint p : src.getPoints()) {
			String countryStr = p.getCountry();
			if (countryStr != null) {
				countryStr = locator.normalizeCountry(countryStr);
				p.setCountry(countryStr);
			}			
		}
		
		for (MapLine l : src.getLines()) {
			String countryStr = l.getCountry();
			if (countryStr != null) {
				countryStr = locator.normalizeCountry(countryStr);
				l.setCountry(countryStr);
			}			
		}		

		// shapes do not have address information
		// untag the following lines if this is wrong
//		for (MapShape s : src.getShapes()) {
//			String countryStr = s.getCountry();
//			if (countryStr != null) {
//				countryStr = locator.normalizeCountry(countryStr);
//				s.setCountry(countryStr);
//			}			
//		}		

	}
	
	/**
	 * Processing of Cities
	 *
	 * Fills the city list in lbl block that is required for find by name
	 * It also builds up information that is required to get address info
	 * for the POIs
	 *
	 * @param map The map.
	 * @param src The map data.
	 */
	private void processCities(Map map, MapDataSource src) {
		LBLFile lbl = map.getLblFile();
		
		if (locationAutofill.isEmpty() == false) {
			// collect the names of the cities
			for (MapPoint p : src.getPoints()) {
				if(p.isCity() && p.getName() != null)
					locator.addCityOrPlace(p); // Put the city info the map for missing info 
			}

			locator.autofillCities(); // Try to fill missing information that include search of next city
		}
		
		for (MapPoint p : src.getPoints()) 
		{
			if(p.isCity() && p.getName() != null)
			{
				String countryStr = p.getCountry();
				Country thisCountry;
				if(countryStr != null) {
					thisCountry = lbl.createCountry(countryStr, locator.getCountryISOCode(countryStr));
				} else
					thisCountry = getDefaultCountry();

				String regionStr  = p.getRegion();
				Region thisRegion;
				if(regionStr != null)
				{
					thisRegion = lbl.createRegion(thisCountry,regionStr, null);
				}
				else
					thisRegion = getDefaultRegion(thisCountry);

				City thisCity;
				if(thisRegion != null)
					thisCity = lbl.createCity(thisRegion, p.getName(), true);
				else
					thisCity = lbl.createCity(thisCountry, p.getName(), true);

				cityMap.put(p, thisCity);
			}
		}

	}
	
	private void processRoads(Map map, MapDataSource src) {
		LBLFile lbl = map.getLblFile();
		MapPoint searchPoint = new MapPoint();
		for (MapLine line : src.getLines()) {
			if(line.isRoad()) {
				String cityName = line.getCity();
				String cityCountryName = line.getCountry();
				String cityRegionName  = line.getRegion();
				String zipStr = line.getZip();

				if(cityName == null && locationAutofill.contains("nearest")) {
					// Get name of next city if untagged

					searchPoint.setLocation(line.getLocation());
					MapPoint nextCity = locator.findNextPoint(searchPoint);

					if(nextCity != null) {
						cityName = nextCity.getCity();
						// city/region/country fields should match to the found city
						cityCountryName = nextCity.getCountry();
						cityRegionName = nextCity.getRegion();
							
						// use the zip code only if no zip code is known
						if(zipStr == null)
							zipStr = nextCity.getZip();
					}
				}

				if (cityName == null && (cityCountryName != null || cityRegionName != null)) {
					// if city name is unknown and region and/or country is known 
					// use empty name for the city
					cityName = UNKNOWN_CITY_NAME;
				}
				
				if(cityName != null) {

					Country cc = (cityCountryName == null)? getDefaultCountry() : lbl.createCountry(cityCountryName, locator.getCountryISOCode(cityCountryName));

					Region cr = (cityRegionName == null)? getDefaultRegion(cc) : lbl.createRegion(cc, cityRegionName, null);

					if(cr != null) {
						((MapRoad)line).setRoadCity(lbl.createCity(cr, cityName, false));
					}
					else {
						((MapRoad)line).setRoadCity(lbl.createCity(cc, cityName, false));
					}
				}

				if(zipStr != null) {
					((MapRoad)line).setRoadZip(lbl.createZip(zipStr));
				}

			}
		}	
	}

	private void processPOIs(Map map, MapDataSource src) {

		LBLFile lbl = map.getLblFile();
		boolean checkedForPoiDispFlag = false;

		for (MapPoint p : src.getPoints()) {
			// special handling for highway exits
			if(p.isExit()) {
				processExit(map, (MapExitPoint)p);
			}
			// do not process:
			// * cities (already processed)
			// * extended types (address information not shown in MapSource and on GPS)
			// * all POIs except roads in case the no-poi-address option is set
			else if (!p.isCity() && !p.hasExtendedType() && (p.isRoadNamePOI() || poiAddresses))
			{
				
				String countryStr = p.getCountry();
				String regionStr  = p.getRegion();
				String zipStr     = p.getZip();
				String cityStr    = p.getCity();

				if(locationAutofill.contains("nearest") && (countryStr == null || regionStr == null || (zipStr == null && cityStr == null)))
				{
					MapPoint nextCity = locator.findNearbyCityByName(p);
						
					if(nextCity == null)
						nextCity = locator.findNextPoint(p);

					if(nextCity != null)
					{
						if (countryStr == null)	countryStr = nextCity.getCountry();
						if (regionStr == null)  regionStr  = nextCity.getRegion();

						if(zipStr == null)
						{
							String cityZipStr = nextCity.getZip();
							
							// Ignore list of Zips separated by ;
							
							if(cityZipStr != null && cityZipStr.indexOf(',') < 0)
								zipStr = cityZipStr;
						}
							
						if(cityStr == null) cityStr = nextCity.getCity();
					
					}
				}
				
	
				if(countryStr != null && !checkedForPoiDispFlag)
				{
					// Different countries require different address notation

					poiDisplayFlags = locator.getPOIDispFlag(countryStr);
					checkedForPoiDispFlag = true;
				}


				if(p.isRoadNamePOI() && cityStr != null)
				{
					// If it is road POI add city name and street name into address info
					p.setStreet(p.getName());
					p.setName(p.getName() + "/" + cityStr);
				}

				POIRecord r = lbl.createPOI(p.getName());	

				if (cityStr == null && (countryStr != null || regionStr != null)) {
					// if city name is unknown and region and/or country is known 
					// use empty name for the city
					cityStr = UNKNOWN_CITY_NAME;
				}
				
				if(cityStr != null)
				{
					Country thisCountry;

					if(countryStr != null)
						thisCountry = lbl.createCountry(countryStr, locator.getCountryISOCode(countryStr));
					else
						thisCountry = getDefaultCountry();

					Region thisRegion;
					if(regionStr != null)
						thisRegion = lbl.createRegion(thisCountry,regionStr, null);
					else
						thisRegion = getDefaultRegion(thisCountry);

					City city;
					if(thisRegion != null)
						city = lbl.createCity(thisRegion, cityStr, false);
					else
						city = lbl.createCity(thisCountry, cityStr, false);

					r.setCity(city);

				}

				if (zipStr != null)
				{
					Zip zip = lbl.createZip(zipStr);
					r.setZip(zip);
				}

				if(p.getStreet() != null)
				{
					Label streetName = lbl.newLabel(p.getStreet());
					r.setStreetName(streetName);			  
				}

				if(p.getHouseNumber() != null)
				{
					if(!r.setSimpleStreetNumber(p.getHouseNumber()))
					{
						Label streetNumber = lbl.newLabel(p.getHouseNumber());
						r.setComplexStreetNumber(streetNumber);
					}
				}

				if(p.getPhone() != null)
				{
					if(!r.setSimplePhoneNumber(p.getPhone()))
					{
						Label phoneNumber = lbl.newLabel(p.getPhone());
						r.setComplexPhoneNumber(phoneNumber);
					}
				}	
		  	
				poimap.put(p, r);
			}
		}

		lbl.allPOIsDone();
	}

	private void processExit(Map map, MapExitPoint mep) {
		LBLFile lbl = map.getLblFile();
		String ref = mep.getMotorwayRef();
		String OSMId = mep.getOSMId();
		if(ref != null) {
			Highway hw = highways.get(ref);
			if(hw == null)
				hw = makeHighway(map, ref);
			if(hw == null) {
			    log.warn("Can't create exit", mep.getName(), "(OSM id", OSMId, ") on unknown highway", ref);
			    return;
			}
			String exitName = mep.getName();
			String exitTo = mep.getTo();
			Exit exit = new Exit(hw);
			String facilityDescription = mep.getFacilityDescription();
			log.info("Creating", ref, "exit", exitName, "(OSM id", OSMId +") to", exitTo, "with facility", ((facilityDescription == null)? "(none)" : facilityDescription));
			if(facilityDescription != null) {
				// description is TYPE,DIR,FACILITIES,LABEL
				// (same as Polish Format)
				String[] atts = facilityDescription.split(",");
				int type = 0;
				if(atts.length > 0)
					type = Integer.decode(atts[0]);
				char direction = ' ';
				if(atts.length > 1) {
					direction = atts[1].charAt(0);
					if(direction == '\'' && atts[1].length() > 1)
						direction = atts[1].charAt(1);
				}
				int facilities = 0x0;
				if(atts.length > 2)
					facilities = Integer.decode(atts[2]);
				String description = "";
				if(atts.length > 3)
					description = atts[3];
				boolean last = true; // FIXME - handle multiple facilities?
				ExitFacility ef = lbl.createExitFacility(type, direction, facilities, description, last);

				exit.addFacility(ef);
			}
			mep.setExit(exit);
			POIRecord r = lbl.createExitPOI(exitName, exit);
			if(exitTo != null) {
				Label ed = lbl.newLabel(exitTo);
				exit.setDescription(ed);
			}
			poimap.put(mep, r);
			// FIXME - set bottom bits of
			// type to reflect facilities available?
		}
	}

	/**
	 * Drive the map generation by stepping through the levels, generating the
	 * subdivisions for the level and filling in the map elements that should
	 * go into the area.
	 *
	 * This is fairly complex: you need to divide into subdivisions depending on
	 * their size and the number of elements that will be contained.
	 *
	 * @param map The map.
	 * @param src The data for the map.
	 */
	private void makeMapAreas(Map map, LoadableMapDataSource src) {
		// The top level has to cover the whole map without subdividing, so
		// do a special check to make sure.
		LevelInfo[] levels = null; 
		if (src instanceof OverviewMapDataSource)
			levels = src.mapLevels();
		else {
			if (OverviewBuilder.isOverviewImg(map.getFilename())) {
				levels = src.overviewMapLevels();
			} else {
				levels = src.mapLevels();
			}
		}
		if (levels == null){
			throw new ExitException("no info about levels available.");
		}
		LevelInfo levelInfo = levels[0];

		// If there is already a top level zoom, then we shouldn't add our own
		Subdivision topdiv;
		if (levelInfo.isTop()) {
			// There is already a top level definition.  So use the values from it and
			// then remove it from the levels definition.

			levels = Arrays.copyOfRange(levels, 1, levels.length);

			Zoom zoom = map.createZoom(levelInfo.getLevel(), levelInfo.getBits());
			topdiv = makeTopArea(src, map, zoom);
		} else {
			// We have to automatically create the definition for the top zoom level.
			int maxBits = getMaxBits(src);
			// If the max is larger than the top-most data level then we
			// decrease it so that it is less.
			if (levelInfo.getBits() <= maxBits)
				maxBits = levelInfo.getBits() - 1;

			// Create the empty top level
			Zoom zoom = map.createZoom(levelInfo.getLevel() + 1, maxBits);
			topdiv = makeTopArea(src, map, zoom);
		}

		// We start with one map data source.
		List<SourceSubdiv> srcList = Collections.singletonList(new SourceSubdiv(src, topdiv));

		// Now the levels filled with features.
		for (LevelInfo linfo : levels) {
			List<SourceSubdiv> nextList = new ArrayList<SourceSubdiv>();

			Zoom zoom = map.createZoom(linfo.getLevel(), linfo.getBits());

			for (SourceSubdiv srcDivPair : srcList) {

				MapSplitter splitter = new MapSplitter(srcDivPair.getSource(), zoom);
				MapArea[] areas = splitter.split();
				log.info("Map region", srcDivPair.getSource().getBounds(), "split into", areas.length, "areas at resolution", zoom.getResolution());

				for (MapArea area : areas) {
					Subdivision parent = srcDivPair.getSubdiv();
					Subdivision div = makeSubdivision(map, parent, area, zoom);
					if (log.isDebugEnabled())
						log.debug("ADD parent-subdiv", parent, srcDivPair.getSource(), ", z=", zoom, " new=", div);
					nextList.add(new SourceSubdiv(area, div));
				}

				Subdivision lastdiv = nextList.get(nextList.size() - 1).getSubdiv();
				lastdiv.setLast(true);
			}

			srcList = nextList;
		}
	}

	/**
	 * Create the top level subdivision.
	 *
	 * There must be an empty zoom level at the least detailed level. As it
	 * covers the whole area in one it must be zoomed out enough so that
	 * this can be done.
	 *
	 * Note that the width is a 16 bit quantity, but the top bit is a
	 * flag and so that leaves only 15 bits into which the actual width
	 * can fit.
	 *
	 * @param src  The source of map data.
	 * @param map  The map being created.
	 * @param zoom The zoom level.
	 * @return The new top level subdivision.
	 */
	private Subdivision makeTopArea(MapDataSource src, Map map, Zoom zoom) {
		Subdivision topdiv = map.topLevelSubdivision(src.getBounds(), zoom);
		topdiv.setLast(true);
		return topdiv;
	}

	/**
	 * Make an individual subdivision for the map.  To do this we need a link
	 * to its parent and the zoom level that we are working at.
	 *
	 * @param map	The map to add this subdivision into.
	 * @param parent The parent division.
	 * @param ma	 The area of the map that we are fitting into this division.
	 * @param z	  The zoom level.
	 * @return The new subdivsion.
	 */
	private Subdivision makeSubdivision(Map map, Subdivision parent, MapArea ma, Zoom z) {
		List<MapPoint> points = ma.getPoints();
		List<MapLine> lines = ma.getLines();
		List<MapShape> shapes = ma.getShapes();

		Subdivision div = map.createSubdivision(parent, ma.getFullBounds(), z);

		if (ma.hasPoints())
			div.setHasPoints(true);
		if (ma.hasIndPoints())
			div.setHasIndPoints(true);
		if (ma.hasLines())
			div.setHasPolylines(true);
		if (ma.hasShapes())
			div.setHasPolygons(true);

		div.startDivision();

		processPoints(map, div, points);
		processLines(map, div, lines);
		processShapes(map, div, shapes);

		div.endDivision();

		return div;
	}

	/**
	 * Create the overview sections.
	 *
	 * @param map The map details.
	 * @param src The map data source.
	 */
	protected void processOverviews(Map map, MapDataSource src) {
		List<Overview> features = src.getOverviews();
		for (Overview ov : features) {
			switch (ov.getKind()) {
			case Overview.POINT_KIND:
				map.addPointOverview((PointOverview) ov);
				break;
			case Overview.LINE_KIND:
				map.addPolylineOverview((PolylineOverview) ov);
				break;
			case Overview.SHAPE_KIND:
				map.addPolygonOverview((PolygonOverview) ov);
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Set all the information that appears in the header.
	 *
	 * @param map The map to write to.
	 * @param src The source of map information.
	 */
	protected void getMapInfo() {
		if (licenseFileName != null) {
			File file = new File(licenseFileName);

			try {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String text;

				// repeat until all lines is read
				while ((text = reader.readLine()) != null) {
					if (!text.isEmpty()) {
						mapInfo.add(text);
					}
				}

				reader.close();
			} catch (FileNotFoundException e) {
				throw new ExitException("Could not open license file " + licenseFileName);
			} catch (IOException e) {
				throw new ExitException("Error reading license file " + licenseFileName);
			}
		} else {
			mapInfo.add("Map data (c) OpenStreetMap and its contributors");
			mapInfo.add("http://www.openstreetmap.org/copyright");
			mapInfo.add("");
			mapInfo.add("This map data is made available under the Open Database License:");
			mapInfo.add("http://opendatacommons.org/licenses/odbl/1.0/");
			mapInfo.add("Any rights in individual contents of the database are licensed under the");
			mapInfo.add("Database Contents License: http://opendatacommons.org/licenses/dbcl/1.0/");
			mapInfo.add("");

			// Pad the version number with spaces so that version
			// strings that are different lengths do not change the size and
			// offsets of the following sections.
			mapInfo.add("Map created with mkgmap-r"
					+ String.format("%-10s", Version.VERSION));

			mapInfo.add("Program released under the GPL");
		}
	}
	
	public void setMapInfo(List<String> msgs){
		mapInfo = msgs;
	}
	
	public void setCopyrights(List<String> msgs){
		copyrights = msgs;
	}
	
	
	/**
	 * Set all the information that appears in the header.
	 *
	 * @param map The map to write to.
	 * @param src The source of map information.
	 */
	protected void processInfo(Map map, LoadableMapDataSource src) {
		// The bounds of the map.
		map.setBounds(src.getBounds());

		if(poiDisplayFlags != 0)					// POI requested alternate address notation
			map.addPoiDisplayFlags(poiDisplayFlags);

		// You can add anything here.
		// But there has to be something, otherwise the map does not show up.
		//
		// We use it to add copyright information that there is no room for
		// elsewhere
		String info = "";
		for (String s: mapInfo){
			info += s.trim() + "\n";
		}
		if (!info.isEmpty())
			map.addInfo(info);
		if (copyrights.isEmpty()){
			// There has to be (at least) two copyright messages or else the map
			// does not show up.  The second one will be displayed at startup,
			// although the conditions where that happens are not known.
			map.addCopyright("program licenced under GPL v2");

			// This one gets shown when you switch on, so put the actual
			// map copyright here.
			for (String cm : src.copyrightMessages())
				map.addCopyright(cm);
		} else {
			for (String cm : copyrights)
				map.addCopyright(cm);
		}
	}

	/**
	 * Step through the points, filter and create a map point which is then added
	 * to the map.
	 *
	 * Note that the location and resolution of map elements is relative to the
	 * subdivision that they occur in.
	 *
	 * @param map	The map to add points to.
	 * @param div	The subdivision that the points belong to.
	 * @param points The points to be added.
	 */
	private void processPoints(Map map, Subdivision div, List<MapPoint> points) {
		LBLFile lbl = map.getLblFile();
		div.startPoints();
		int res = div.getResolution();

		boolean haveIndPoints = false;
		int pointIndex = 1;

		// although the non-indexed points are output first,
		// pointIndex must be initialized to the number of indexed
		// points (not 1)
		for (MapPoint point : points) {
			if (point.isCity() &&
			    point.getMinResolution() <= res &&
			    point.getMaxResolution() >= res) {
				++pointIndex;
				haveIndPoints = true;
			}
		}

		for (MapPoint point : points) {

			if (point.isCity() ||
			    point.getMinResolution() > res ||
			    point.getMaxResolution() < res)
				continue;

			String name = point.getName();

			Point p = div.createPoint(name);
			p.setType(point.getType());

			if(point.hasExtendedType()) {
				ExtTypeAttributes eta = point.getExtTypeAttributes();
				if(eta != null) {
					eta.processLabels(lbl);
					p.setExtTypeAttributes(eta);
				}
			}

			Coord coord = point.getLocation();
			try {
				p.setLatitude(coord.getLatitude());
				p.setLongitude(coord.getLongitude());
			}
			catch (AssertionError ae) {
				log.error("Problem with point of type 0x" + Integer.toHexString(point.getType()) + " at " + coord.toOSMURL());
				log.error("  Subdivision shift is " + div.getShift() +
						  " and its centre is at " + div.getCenter().toOSMURL());
				log.error("  " + ae.getMessage());
				continue;
			}

			POIRecord r = poimap.get(point);
			if (r != null)
				p.setPOIRecord(r);

			map.addMapObject(p);
			if(!point.hasExtendedType()) {
				if(name != null && div.getZoom().getLevel() == 0) {
					if(pointIndex > 255)
						log.error("FIXME - too many POIs in group");
					else if(point.isExit()) {
						Exit e = ((MapExitPoint)point).getExit();
						if(e != null)
							e.getHighway().addExitPoint(name, pointIndex, div);
					}
					else if(makePOIIndex)
						lbl.createPOIIndex(name, pointIndex, div, point.getType());
				}

				++pointIndex;
			}
		}

		if (haveIndPoints) {
			div.startIndPoints();

			pointIndex = 1; // reset to 1
			for (MapPoint point : points) {

				if (!point.isCity() ||
				    point.getMinResolution() > res ||
				    point.getMaxResolution() < res)
					continue;

				String name = point.getName();

				Point p = div.createPoint(name);
				p.setType(point.getType());

				Coord coord = point.getLocation();
				try {
					p.setLatitude(coord.getLatitude());
					p.setLongitude(coord.getLongitude());
				}
				catch (AssertionError ae) {
					log.error("Problem with point of type 0x" + Integer.toHexString(point.getType()) + " at " + coord.toOSMURL());
					log.error("  Subdivision shift is " + div.getShift() +
							  " and its centre is at " + div.getCenter().toOSMURL());
					log.error("  " + ae.getMessage());
					continue;
				}

				map.addMapObject(p);
				if(name != null && div.getZoom().getLevel() == 0) {
					// retrieve the City created earlier for this
					// point and store the point info in it
					City c = cityMap.get(point);

					if(pointIndex > 255) {
						System.err.println("Can't set city point index for " + name + " (too many indexed points in division)\n");
					} else {
						c.setPointIndex((byte)pointIndex);
						c.setSubdivision(div);
					}
				}

				++pointIndex;
			}
		}
	}

	/**
	 * Step through the lines, filter, simplify if necessary, and create a map
	 * line which is then added to the map.
	 *
	 * Note that the location and resolution of map elements is relative to the
	 * subdivision that they occur in.
	 *
	 * @param map	The map to add points to.
	 * @param div	The subdivision that the lines belong to.
	 * @param lines The lines to be added.
	 */
	private void processLines(Map map, Subdivision div, List<MapLine> lines)
	{
		div.startLines();  // Signal that we are beginning to draw the lines.

		int res = div.getResolution();

		FilterConfig config = new FilterConfig();
		config.setResolution(res);
		config.setLevel(div.getZoom().getLevel());
		config.setRoutable(doRoads);

		//TODO: Maybe this is the wrong place to do merging.
		// Maybe more efficient if merging before creating subdivisions.
		if (mergeLines) {
			LineMergeFilter merger = new LineMergeFilter();
			lines = merger.merge(lines);
		}
		LayerFilterChain filters = new LayerFilterChain(config);
		if (enableLineCleanFilters && (res < 24)) {
			filters.addFilter(new RoundCoordsFilter());
			filters.addFilter(new SizeFilter(MIN_SIZE_LINE));
			if(reducePointError > 0)
				filters.addFilter(new DouglasPeuckerFilter(reducePointError));
		}
		filters.addFilter(new LineSplitterFilter());
		filters.addFilter(new RemoveEmpty());
		filters.addFilter(new RemoveObsoletePointsFilter());
		filters.addFilter(new LinePreparerFilter(div));
		filters.addFilter(new LineAddFilter(div, map, doRoads));
		
		for (MapLine line : lines) {
			if (line.getMinResolution() > res || line.getMaxResolution() < res)
				continue;

			filters.startFilter(line);
		}
	}

	/**
	 * Step through the polygons, filter, simplify if necessary, and create a map
	 * shape which is then added to the map.
	 *
	 * Note that the location and resolution of map elements is relative to the
	 * subdivision that they occur in.
	 *
	 * @param map	The map to add polygons to.
	 * @param div	The subdivision that the polygons belong to.
	 * @param shapes The polygons to be added.
	 */
	private void processShapes(Map map, Subdivision div, List<MapShape> shapes)
	{
		div.startShapes();  // Signal that we are beginning to draw the shapes.

		int res = div.getResolution();

		FilterConfig config = new FilterConfig();
		config.setResolution(res);
		config.setLevel(div.getZoom().getLevel());
		config.setRoutable(doRoads);
		
		if (res == 24 && mergeShapes){
			ShapeMergeFilter shapeMergeFilter = new ShapeMergeFilter(div.getShift());
			List<MapShape> mergedShapes = shapeMergeFilter.merge(shapes, div.getStartRgnPointer());
			log.info("merged shapes " + shapes.size() + "->" + mergedShapes.size());
			shapes = mergedShapes; 
		}
		
		LayerFilterChain filters = new LayerFilterChain(config);
		if (enableLineCleanFilters && (res < 24)) {
			filters.addFilter(new PreserveHorizontalAndVerticalLinesFilter());
			filters.addFilter(new RoundCoordsFilter());
			int sizefilterVal =  getMinSizePolygonForResolution(res);
			if (sizefilterVal > 0)
				filters.addFilter(new SizeFilter(sizefilterVal));
			//DouglasPeucker behaves at the moment not really optimal at low zooms, but acceptable.
			//Is there an similar algorithm for polygons?
			if(reducePointErrorPolygon > 0)
				filters.addFilter(new DouglasPeuckerFilter(reducePointErrorPolygon));
		}
		filters.addFilter(new PolygonSplitterFilter());
		filters.addFilter(new RemoveEmpty());
		filters.addFilter(new RemoveObsoletePointsFilter());
		filters.addFilter(new LinePreparerFilter(div));
		filters.addFilter(new ShapeAddFilter(div, map));

		for (MapShape shape : shapes) {
			if (shape.getMinResolution() > res || shape.getMaxResolution() < res)
				continue;

			filters.startFilter(shape);
		}
	}

	Highway makeHighway(Map map, String ref) {
		if(getDefaultRegion(null) == null) {
			log.warn("Highway " + ref + " has no region (define a default region to zap this warning)");
		}
		Highway hw = highways.get(ref);
		if(hw == null) {
			LBLFile lblFile = map.getLblFile();
			log.info("creating highway " + ref);
			hw = lblFile.createHighway(getDefaultRegion(null), ref);
			highways.put(ref, hw);
		}

		return hw;
	}

	/**
	 * It is not possible to represent large maps at the 24 bit resolution.  This
	 * gets the largest resolution that can still cover the whole area of the
	 * map.  It is used for the top most layer.
	 *
	 * @param src The map data.
	 * @return The largest number of bits where we can still represent the
	 *         whole map.
	 */
	private int getMaxBits(MapDataSource src) {
		int topshift = Integer.numberOfLeadingZeros(src.getBounds().getMaxDimension());
		int minShift = Math.max(CLEAR_TOP_BITS - topshift, 0);
		return 24 - minShift;
	}

	/**
	 * Enable/disable the creation of a routable map 
	 * @param doRoads 
	 */
	public void setDoRoads(boolean doRoads) {
		this.doRoads = doRoads;
	}

	public void setEnableLineCleanFilters(boolean enable) {
		this.enableLineCleanFilters = enable;
	}

	/**
	 * Determine the minimum size for a polygon for the given level.
	 * @param res the resolution
	 * @return the size filter value
	 */
	private int getMinSizePolygonForResolution(int res) {
	
		if (polygonSizeLimitsOpt == null)
			return minSizePolygon;
	
		if (polygonSizeLimits == null){
			polygonSizeLimits = new HashMap<Integer, Integer>();
			String[] desc = polygonSizeLimitsOpt.split("[, \\t\\n]+");
	
			int count = 0;
			for (String s : desc) {
				String[] keyVal = s.split("[=:]");
				if (keyVal == null || keyVal.length < 2) {
					System.err.println("incorrect polygon-size-limits specification " + polygonSizeLimitsOpt);
					continue;
				}
	
				try {
					int key = Integer.parseInt(keyVal[0]);
					int value = Integer.parseInt(keyVal[1]);
					Integer testDup = polygonSizeLimits.put(key, value);
					if (testDup != null){
						System.err.println("duplicate resolution value in polygon-size-limits specification " + polygonSizeLimitsOpt);
						continue;
					}
				} catch (NumberFormatException e) {
					System.err.println("polygon-size-limits specification not all numbers " + keyVal[count]);
				}
				count++;
			}
		}
		if (polygonSizeLimits != null){
			// return the value for the desired resolution or the next higher one
			for (int r = res; r <= 24; r++){
				Integer limit = polygonSizeLimits.get(r);
				if (limit != null){
					if (r != res)
						polygonSizeLimits.put(res, limit);
					return limit;
				}
			}
			return 0;
		}
		return minSizePolygon;
	}

	private static class SourceSubdiv {
		private final MapDataSource source;
		private final Subdivision subdiv;

		SourceSubdiv(MapDataSource ds, Subdivision subdiv) {
			this.source = ds;
			this.subdiv = subdiv;
		}

		public MapDataSource getSource() {
			return source;
		}

		public Subdivision getSubdiv() {
			return subdiv;
		}
	}

	private class LineAddFilter extends BaseFilter implements MapFilter {
		private final Subdivision div;
		private final Map map;
		private final boolean doRoads;

		LineAddFilter(Subdivision div, Map map, boolean doRoads) {
			this.div = div;
			this.map = map;
			this.doRoads = doRoads;
		}

		public void doFilter(MapElement element, MapFilterChain next) {
			MapLine line = (MapLine) element;
			assert line.getPoints().size() < 255 : "too many points";

			Polyline pl = div.createLine(line.getLabels());
			if (element.hasExtendedType()) {
				ExtTypeAttributes eta = element.getExtTypeAttributes();
				if (eta != null) {
					eta.processLabels(map.getLblFile());
					pl.setExtTypeAttributes(eta);
				}
			} else
				div.setPolylineNumber(pl);

			pl.setDirection(line.isDirection());

			pl.addCoords(line.getPoints());

			pl.setType(line.getType());
			if (doRoads){
				if (line.isRoad()) {
					if (log.isDebugEnabled())
						log.debug("adding road def: " + line.getName());
					RoadDef roaddef = ((MapRoad) line).getRoadDef();

					pl.setRoadDef(roaddef);
					roaddef.addPolylineRef(pl);
				} else if (routingErrorMsgPrinted == false){
					if (div.getZoom().getLevel() == 0 && GType.isRoutableLineType(line.getType())){
						Coord start = line.getPoints().get(0);
						log.error("Non-routable way with routable type " + GType.formatType(line.getType()) + " starting at " +
								start.toOSMURL() + 
								" is used for a routable map. This leads to routing errors. Try --check-styles to check the style.");
						
						routingErrorMsgPrinted = true;
					}
				}
			}
			map.addMapObject(pl);
		}
	}
	
	private static class ShapeAddFilter extends BaseFilter implements MapFilter {
		private final Subdivision div;
		private final Map map;

		ShapeAddFilter(Subdivision div, Map map) {
			this.div = div;
			this.map = map;
		}

		public void doFilter(MapElement element, MapFilterChain next) {
			MapShape shape = (MapShape) element;
			assert shape.getPoints().size() < 255 : "too many points";

			Polygon pg = div.createPolygon(shape.getName());

			pg.addCoords(shape.getPoints());

			pg.setType(shape.getType());
			if(element.hasExtendedType()) {
				ExtTypeAttributes eta = element.getExtTypeAttributes();
				if(eta != null) {
					eta.processLabels(map.getLblFile());
					pg.setExtTypeAttributes(eta);
				}
			}
			map.addMapObject(pg);
		}
	}
}

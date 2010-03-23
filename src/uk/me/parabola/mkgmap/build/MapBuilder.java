/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: 30-Sep-2007
 */
package uk.me.parabola.mkgmap.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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
import uk.me.parabola.mkgmap.filters.BaseFilter;
import uk.me.parabola.mkgmap.filters.DouglasPeuckerFilter;
import uk.me.parabola.mkgmap.filters.FilterConfig;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
import uk.me.parabola.mkgmap.filters.LineMergeFilter;
import uk.me.parabola.mkgmap.filters.MapFilter;
import uk.me.parabola.mkgmap.filters.MapFilterChain;
import uk.me.parabola.mkgmap.filters.PreserveHorizontalAndVerticalLinesFilter;
import uk.me.parabola.mkgmap.filters.PolygonSplitterFilter;
import uk.me.parabola.mkgmap.filters.RemoveEmpty;
import uk.me.parabola.mkgmap.filters.RoundCoordsFilter;
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
	
	private final java.util.Map<MapPoint,POIRecord> poimap = new HashMap<MapPoint,POIRecord>();
	private final java.util.Map<MapPoint,City> cityMap = new HashMap<MapPoint,City>();

	private boolean doRoads;

	private final Locator locator = new Locator();

	private final java.util.Map<String, Highway> highways = new HashMap<String, Highway>();

	private Country country;
	private Region  region;

	private String countryName = "COUNTRY";
	private String countryAbbr = "ABC";
	private String regionName;
	private String regionAbbr;

	private double reducePointError;
	private boolean mergeLines;

	private int     minLineSize;
	private int     minPolygonSize;

	private int		locationAutofillLevel;
	private boolean	poiAddresses = true;
	private int		poiDisplayFlags;
	private boolean sortRoads = true;
	private boolean enableLineCleanFilters = true;
	private boolean makePOIIndex = false;
	private int routeCenterBoundaryType = 0;

	public MapBuilder() {
		regionName = null;
	}

	public void config(EnhancedProperties props) {

		countryName = props.getProperty("country-name", countryName);
		countryAbbr = props.getProperty("country-abbr", countryAbbr);
		regionName = props.getProperty("region-name", null);
		regionAbbr = props.getProperty("region-abbr", null);
		reducePointError = props.getProperty("reduce-point-density", 2.6);
		mergeLines = props.containsKey("merge-lines");

		minLineSize = props.getProperty("min-line-size", 1);
		minPolygonSize = props.getProperty("min-polygon-size", 8);

		makePOIIndex = props.getProperty("make-poi-index", false);

		if(props.getProperty("no-poi-address", null) != null)
			poiAddresses = false;

		String autoFillPar = props.getProperty("location-autofill", null);

		if(autoFillPar != null)
		{
			try
			{
				locationAutofillLevel = Integer.parseInt(autoFillPar);
			}
			catch (Exception e)
			{
				locationAutofillLevel = 1;
			}
		}

		locator.setAutoFillLevel(locationAutofillLevel);

		if(props.getProperty("no-sorted-roads", null) != null)
			sortRoads = false;

		routeCenterBoundaryType = props.getProperty("route-center-boundary", 0);
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
		LBLFile lblFile = map.getLblFile();
		NETFile netFile = map.getNetFile();

		country = lblFile.createCountry(countryName, countryAbbr);
		if(regionName != null)
			region = lblFile.createRegion(country, regionName, regionAbbr);

		if(routeCenterBoundaryType != 0 &&
		   netFile != null &&
		   src instanceof MapperBasedMapDataSource) {
			for(RouteCenter rc : src.getRoadNetwork().getCenters()) {
				((MapperBasedMapDataSource)src).addBoundaryLine(rc.getArea(), routeCenterBoundaryType, rc.reportSizes());
			}
		}

		processCities(map, src);
		processPOIs(map, src);
		//preProcessRoads(map, src);
		processOverviews(map, src);
		processInfo(map, src);
		makeMapAreas(map, src);
		//processRoads(map, src);
		//postProcessRoads(map, src);

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
			netFile.writePost(rgnFile.getWriter(), sortRoads);
		}
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

		locator.setDefaultCountry(countryName, countryAbbr);
		
		// collect the names of the cities
		for (MapPoint p : src.getPoints()) {
			if(p.isCity() && p.getName() != null)
				locator.addLocation(p); // Put the city info the map for missing info 
		}

		if(locationAutofillLevel > 0)
			locator.resolve(); // Try to fill missing information that include search of next city

		for (MapPoint p : src.getPoints()) 
		{
			if(p.isCity() && p.getName() != null)
			{
				Country thisCountry;

				String CountryStr = p.getCountry();
				String RegionStr  = p.getRegion();

				if(CountryStr != null)
					thisCountry = lbl.createCountry(CountryStr, locator.getCountryCode(CountryStr));
				else
					thisCountry = country;

				Region thisRegion;
				if(RegionStr != null)
				{
					thisRegion = lbl.createRegion(thisCountry,RegionStr, null);
				}
				else
					thisRegion = region;

				City thisCity;
				if(thisRegion != null)
					thisCity = lbl.createCity(thisRegion, p.getName(), true);
				else
					thisCity = lbl.createCity(thisCountry, p.getName(), true);

				cityMap.put(p, thisCity);
			}
		}

		MapPoint tempPoint = new MapPoint();

		for (MapLine line : src.getLines()) {
			if(line.isRoad()) {
				String cityName = line.getCity();
				String cityCountryName = line.getCountry();
				String cityRegionName  = line.getRegion();
				String zipStr = line.getZip();

				if(cityName == null) {
					// Get name of next city if untagged

					tempPoint.setLocation(line.getLocation());
					MapPoint nextCity = locator.findNextPoint(tempPoint);

					if(nextCity != null) {
						cityName = nextCity.getCity();
						if(cityCountryName == null)
							cityCountryName = nextCity.getCountry();
						if(cityRegionName == null)
							cityRegionName = nextCity.getRegion();
						if(zipStr == null)
							zipStr = nextCity.getZip();
					}
				}

				if(cityName != null) {

					Country cc = (cityCountryName == null)? country : lbl.createCountry(cityCountryName, locator.getCountryCode(cityCountryName));

					Region cr = (cityRegionName == null)? region : lbl.createRegion(cc, cityRegionName, null);

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
			if(p.isExit()) {
				processExit(map, (MapExitPoint)p);
			}
			else if(!p.isCity() && !p.hasExtendedType() && (p.isRoadNamePOI() || poiAddresses)) {
				boolean doAutofill;
				if(locationAutofillLevel > 0 || p.isRoadNamePOI())
					doAutofill = true;
				else
					doAutofill = false;
				
				
				String CountryStr = p.getCountry();
				String RegionStr  = p.getRegion();		
				String ZipStr     = p.getZip();
				String CityStr    = p.getCity();

				if(CityStr != null || ZipStr != null ||RegionStr != null || CountryStr != null)

				if(CountryStr != null)
					CountryStr = locator.fixCountryString(CountryStr);

				boolean guessed = false;
				if(CountryStr == null || RegionStr == null || (ZipStr == null && CityStr == null))
				{
						MapPoint nextCity = locator.findByCityName(p);
						
						if(doAutofill && nextCity == null)
							nextCity = locator.findNextPoint(p);

						if(nextCity != null)
						{
							guessed = true;

							if (CountryStr == null)	CountryStr = nextCity.getCountry();
							if (RegionStr == null)  RegionStr  = nextCity.getRegion();

							if(doAutofill)
							{
								if(ZipStr == null)
								{
									String CityZipStr = nextCity.getZip();
									
									// Ignore list of Zips seperated by ;
									
									if(CityZipStr != null && CityZipStr.indexOf(',') < 0)
										ZipStr = CityZipStr; 
								}
								
								if(CityStr == null) CityStr = nextCity.getCity();								
							}
						
						}
				}
				
	
				if(CountryStr != null && !checkedForPoiDispFlag)
				{
					// Different countries require different address notation

					poiDisplayFlags = locator.getPOIDispFlag(CountryStr);
					checkedForPoiDispFlag = true;
				}


				if(p.isRoadNamePOI() && CityStr != null)					
				{
						// If it is road POI add city name and street name into address info
						p.setStreet(p.getName());
						p.setName(p.getName() + "/" + CityStr);
				}

				POIRecord r = lbl.createPOI(p.getName());	

				if(CityStr != null)
				{
					Country thisCountry;

					if(CountryStr != null)
						thisCountry = lbl.createCountry(CountryStr, locator.getCountryCode(CountryStr));
					else
						thisCountry = country;

					Region thisRegion;
					if(RegionStr != null)
						thisRegion = lbl.createRegion(thisCountry,RegionStr, null);
					else
						thisRegion = region;

					City city;
					if(thisRegion != null)
						city = lbl.createCity(thisRegion, CityStr, false);
					else
						city = lbl.createCity(thisCountry, CityStr, false);

	  			r.setCity(city);

				}

				if (ZipStr != null)
				{
					Zip zip = lbl.createZip(ZipStr);
					r.setZipIndex(zip.getIndex());
				}

				if(p.getStreet() != null)
				{
					Label streetName = lbl.newLabel(p.getStreet());
					r.setStreetName(streetName);			  
				}
				else if (guessed && locationAutofillLevel > 0)
				{
					Label streetName = lbl.newLabel("FIX MY ADDRESS");
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

		//System.out.println(poiAddrCountr + " POIs have address info");

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
			    log.warn("Can't create exit " + mep.getName() + " (OSM id " + OSMId + ") on unknown highway " + ref);
			    return;
			}
			String exitName = mep.getName();
			String exitTo = mep.getTo();
			Exit exit = new Exit(hw);
			String facilityDescription = mep.getFacilityDescription();
			log.info("Creating " + ref + " exit " + exitName + " (OSM id " + OSMId +") to " + exitTo + " with facility " + ((facilityDescription == null)? "(none)" : facilityDescription));
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
	 * Drive the map generation by steping through the levels, generating the
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
		LevelInfo[] levels = src.mapLevels();
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
				log.info("Map region " + srcDivPair.getSource().getBounds() + " split into " + areas.length + " areas at resolution " + zoom.getResolution());

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
	protected void processInfo(Map map, LoadableMapDataSource src) {
		// The bounds of the map.
		map.setBounds(src.getBounds());

		if(poiDisplayFlags != 0)							// POI requested alterate address notation
			map.setPoiDisplayFlags(poiDisplayFlags);

		// You can add anything here.
		// But there has to be something, otherwise the map does not show up.
		//
		// We use it to add copyright information that there is no room for
		// elsewhere.
		map.addInfo("OSM Street map");
		map.addInfo("http://www.openstreetmap.org/");
		map.addInfo("Map data licenced under Creative Commons Attribution ShareAlike 2.0");
		map.addInfo("http://creativecommons.org/licenses/by-sa/2.0/");
		map.addInfo("Map created with mkgmap-r" + Version.VERSION);

		map.addInfo("Program released under the GPL");

		// There has to be (at least) two copyright messages or else the map
		// does not show up.  The second one will be displayed at startup,
		// although the conditions where that happens are not known.
		map.addCopyright("program licenced under GPL v2");

		// This one gets shown when you switch on, so put the actual
		// map copyright here.
		for (String cm : src.copyrightMessages())
			map.addCopyright(cm);
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
		// pointIndex must be initialised to the number of indexed
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
						  " and its centre is at " + new Coord(div.getLatitude(), div.getLongitude()).toOSMURL());
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
							  " and its centre is at " + new Coord(div.getLatitude(), div.getLongitude()).toOSMURL());
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


		//TODO: Maybe this is the wrong place to do merging.
		// Maybe more efficient if merging before creating subdivisions.
		if (mergeLines && res < 24) {
			LineMergeFilter merger = new LineMergeFilter();
			lines = merger.merge(lines);
		}

		LayerFilterChain filters = new LayerFilterChain(config);
		if (enableLineCleanFilters && (res < 24)) {
			filters.addFilter(new PreserveHorizontalAndVerticalLinesFilter());
			filters.addFilter(new RoundCoordsFilter());
			filters.addFilter(new SizeFilter(minLineSize));
			if(reducePointError > 0)
				filters.addFilter(new DouglasPeuckerFilter(reducePointError));
		}
		filters.addFilter(new LineSplitterFilter());
		filters.addFilter(new RemoveEmpty());
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
		LayerFilterChain filters = new LayerFilterChain(config);
		if (enableLineCleanFilters && (res < 24)) {
			filters.addFilter(new PreserveHorizontalAndVerticalLinesFilter());
			filters.addFilter(new RoundCoordsFilter());
			filters.addFilter(new SizeFilter(minPolygonSize));
			//DouglasPeucker behaves at the moment not really optimal at low zooms, but acceptable.
			//Is there an similar algorithm for polygons?
			if(reducePointError > 0)
				filters.addFilter(new DouglasPeuckerFilter(reducePointError));
		}
		filters.addFilter(new PolygonSplitterFilter());
		filters.addFilter(new RemoveEmpty());
		filters.addFilter(new ShapeAddFilter(div, map));

		for (MapShape shape : shapes) {
			if (shape.getMinResolution() > res || shape.getMaxResolution() < res)
				continue;

			filters.startFilter(shape);
		}
	}

	Highway makeHighway(Map map, String ref) {
		if(region == null) {
			log.warn("Highway " + ref + " has no region (define a default region to zap this warning)");
		}
		Highway hw = highways.get(ref);
		if(hw == null) {
			LBLFile lblFile = map.getLblFile();
			log.info("creating highway " + ref);
			hw = lblFile.createHighway(region, ref);
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
		int topshift = Integer.numberOfLeadingZeros(src.getBounds().getMaxDimention());
		int minShift = Math.max(CLEAR_TOP_BITS - topshift, 0);
		return 24 - minShift;
	}

	public void setDoRoads(boolean doRoads) {
		this.doRoads = doRoads;
	}

	public void setEnableLineCleanFilters(boolean enable) {
		this.enableLineCleanFilters = enable;
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

			Polyline pl = div.createLine(line.getName(), line.getRef());
			if(!element.hasExtendedType())
				div.setPolylineNumber(pl);
			else {
				ExtTypeAttributes eta = element.getExtTypeAttributes();
				if(eta != null) {
					eta.processLabels(map.getLblFile());
					pl.setExtTypeAttributes(eta);
				}
			}

			pl.setDirection(line.isDirection());

			for (Coord co : line.getPoints())
				pl.addCoord(co);

			pl.setType(line.getType());

			if (doRoads && line.isRoad()) {
				if (log.isDebugEnabled())
					log.debug("adding road def: " + line.getName());
				RoadDef roaddef = ((MapRoad) line).getRoadDef();

				pl.setRoadDef(roaddef);
				roaddef.addPolylineRef(pl);
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

			for (Coord co : shape.getPoints())
				pg.addCoord(co);

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

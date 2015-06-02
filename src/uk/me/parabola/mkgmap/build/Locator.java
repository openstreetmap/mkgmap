/*
 * Copyright (C) 2006, 2011.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.KdTree;
import uk.me.parabola.util.MultiHashMap;

public class Locator {
	private static final Logger log = Logger.getLogger(Locator.class);

    /** hash map to collect equally named MapPoints*/ 
	private final MultiHashMap<String, MapPoint> cityMap = new MultiHashMap<String, MapPoint>();
	
	private final KdTree<MapPoint> cityFinder = new KdTree<>();
	private final List<MapPoint> placesMap  =  new ArrayList<MapPoint>();

	/** Contains the tags defined by the option name-tag-list */
	private final List<String> nameTags;

	private final LocatorConfig locConfig = LocatorConfig.get();

	private final Set<String> locationAutofill;
	
	private static final double MAX_CITY_DIST = 30000;

	public Locator() {
		this(new EnhancedProperties());
	}
	
	public Locator(EnhancedProperties props) {
		this.nameTags = LocatorUtil.getNameTags(props);
		this.locationAutofill = new HashSet<String>(LocatorUtil.parseAutofillOption(props));
	}
	
	public void addCityOrPlace(MapPoint p) 
	{
		if (p.isCity() == false) 
		{
			log.warn("MapPoint has no city type id: 0x"+Integer.toHexString(p.getType()));
			return;
		}
		
		if (log.isDebugEnabled())
			log.debug("S City 0x"+Integer.toHexString(p.getType()), p.getName(), "|", p.getCity(), "|", p.getRegion(), "|", p.getCountry());

		// correct the country name
		// usually this is the translation from 3letter ISO code to country name
		if(p.getCountry() != null)
			p.setCountry(normalizeCountry(p.getCountry()));

		resolveIsInInfo(p); // Pre-process the is_in field

		if(p.getCity() != null)
		{
			if (log.isDebugEnabled())
				log.debug(p.getCity(),p.getRegion(),p.getCountry());
			// Must use p.getName() here because p.getCity() contains the city name of the preprocessed cities
			addCity(p.getName(), p);
		}
		else
		{
			// All other places which do not seam to be a real city has to resolved later
			placesMap.add(p);		
		}
		
		if (log.isDebugEnabled())
			log.debug("E City 0x"+Integer.toHexString(p.getType()), p.getName(), "|", p.getCity(), "|", p.getRegion(), "|", p.getCountry());
	}

	public void setDefaultCountry(String country, String abbr)
	{
		locConfig.setDefaultCountry(country, abbr);
	}
	
	public String normalizeCountry(String country)
	{
		if (country == null) {
			return null;
		}
		
		String iso = locConfig.getCountryISOCode(country);
		if (iso != null) {
			String normedCountryName = locConfig.getCountryName(iso, nameTags);
			if (normedCountryName != null) {
				log.debug("Country:",country,"ISO:",iso,"Norm:",normedCountryName);
				return normedCountryName;
			}
		}
		
		// cannot find the country in our config => return the country itself
		log.debug("Country:",country,"ISO:",iso,"Norm:",country);
		return country;
	}

	/**
	 * Checks if the country given by attached tags is already known, adds or completes
	 * the Locator information about this country and return the three letter ISO code
	 * (in case the country is known in the LocatorConfig.xml) or the country name.
	 * 
	 * @param tags the countries tags
	 * @return the three letter ISO code or <code>null</code> if ISO code is unknown
	 */
	public String addCountry(Tags tags) {
		synchronized (locConfig) {
			String iso = getCountryISOCode(tags);
			if (iso == null) {
				log.warn("Cannot find iso code for country with tags", tags);
			} else {
				locConfig.addCountryWithTags(iso, tags);
			}
			return iso;
		}
	}
	
	private final static String[] PREFERRED_NAME_TAGS = {"name","name:en","int_name"};
	
	public String getCountryISOCode(Tags countryTags) {
		for (String nameTag : PREFERRED_NAME_TAGS) {
			String nameValue = countryTags.get(nameTag);
			String isoCode = getCountryISOCode(nameValue);
			if (isoCode != null) {
				return isoCode;
			}
		}

		for (String countryStr : countryTags.getTagsWithPrefix("name:", false)
				.values()) {
			String isoCode = getCountryISOCode(countryStr);
			if (isoCode != null) {
				return isoCode;
			}
		}
		return null;
	}
	
	public String getCountryISOCode(String country)
	{
		return locConfig.getCountryISOCode(country);
	}

	public int getPOIDispFlag(String country)
	{
		return locConfig.getPoiDispFlag(getCountryISOCode(country));
	}

	private boolean isContinent(String continent)
	{
		return locConfig.isContinent(continent);
	}


	/**
	 * resolveIsInInfo tries to get country and region info out of the is_in field 
	 * @param p 	Point to process
	 */
	private void resolveIsInInfo(MapPoint p)
	{
		if (locationAutofill.contains("is_in") == false) {
			return;
		}
		
		if(p.getCountry() != null && p.getRegion() != null && p.getCity() == null)
		{		
			p.setCity(p.getName());
			return;
		}

		if(p.getIsIn() != null)
		{	
			String[] cityList = p.getIsIn().split(",");
			
			//System.out.println(p.getIsIn());

			// is_in content is not well defined so we try our best to get some info out of it
			// Format 1 popular in Germany: "County,State,Country,Continent"

			if(cityList.length > 1 &&
				isContinent(cityList[cityList.length-1]))	// Is last a continent ?
			{
				if (p.getCountry() == null) {
					// The one before continent should be the country
					p.setCountry(normalizeCountry(cityList[cityList.length-2].trim()));
				}
				
				// aks the config which info to use for region info				
				int offset = locConfig.getRegionOffset(getCountryISOCode(p.getCountry())) + 1;

				if(cityList.length > offset && p.getRegion() == null)
					p.setRegion(cityList[cityList.length-(offset+1)].trim());

			} else

			// Format 2 other way round: "Continent,Country,State,County"	

			if(cityList.length > 1 && isContinent(cityList[0]))	// Is first a continent ?
			{
				if (p.getCountry() == null) {
					// The one before continent should be the country
					p.setCountry(normalizeCountry(cityList[1].trim()));
				}
				
				int offset = locConfig.getRegionOffset(getCountryISOCode(p.getCountry())) + 1;

				if(cityList.length > offset && p.getRegion() == null)
					p.setRegion(cityList[offset].trim());
			} else

			// Format like this "County,State,Country"

			if(p.getCountry() == null && cityList.length > 0)
			{
				// I don't like to check for a list of countries but I don't want other stuff in country field
				String isoCode = locConfig.getCountryISOCode(cityList[cityList.length-1]);
				if (isoCode != null)
				{	
					p.setCountry(normalizeCountry(isoCode));

					int offset = locConfig.getRegionOffset(isoCode) + 1;

					if(cityList.length > offset && p.getRegion() == null)
						p.setRegion(cityList[cityList.length-(offset+1)].trim());	
				}
			}
		}

		if(p.getCountry() != null && p.getRegion() != null && p.getCity() == null)
		{	
			p.setCity(p.getName());
		}
	}
	
	public MapPoint findNextPoint(MapPoint p)
	{
		return cityFinder.findNextPoint(p);
	}
	
	public MapPoint findNearbyCityByName(MapPoint p) {

		if (p.getCity() == null)
			return null;

		Collection<MapPoint> nextCityList = cityMap.get(p.getCity());
		if (nextCityList.isEmpty()) {
			return null;
		}

		MapPoint near = null;
		double minDist = Double.MAX_VALUE;
		for (MapPoint nextCity : nextCityList) {
			double dist = p.getLocation().distance(nextCity.getLocation());

			if (dist < minDist) {
				minDist = dist;
				near = nextCity;
			}
		}

		if (minDist <= MAX_CITY_DIST) // Wrong hit more the 30 km away ?
			return near;
		else
			return null;
	}
	
	private MapPoint findCityByIsIn(MapPoint place) {
		
		if (locationAutofill.contains("is_in") == false) {
			return null;
		}
		
		String isIn = place.getIsIn();

		if (isIn == null) {
			return null;
		}

		String[] cityList = isIn.split(",");

		// Go through the isIn string and check if we find a city with this name
		// Lets hope we find the next bigger city

		double minDist = Double.MAX_VALUE;
		Collection<MapPoint> nextCityList = null;
		for (String cityCandidate : cityList) {
			cityCandidate = cityCandidate.trim();

			Collection<MapPoint> candidateCityList = cityMap.get(cityCandidate);
			if (candidateCityList.isEmpty() == false) {
				if (nextCityList == null) {
					nextCityList = new ArrayList<MapPoint>(candidateCityList.size());
				}
				nextCityList.addAll(candidateCityList);
			}
		}

		if (nextCityList == null) {
			// no city name found in the is_in tag
			return null;
		}

		MapPoint nearbyCity = null;
		for (MapPoint nextCity : nextCityList) {
			double dist = place.getLocation().distance(nextCity.getLocation());

			if (dist < minDist) {
				minDist = dist;
				nearbyCity = nextCity;
			}
		}

		// Check if the city is closer than MAX_CITY_DIST
		// otherwise don't use it but issue a warning
		if (minDist > MAX_CITY_DIST) {
			log.warn("is_in of", place.getName(), "is far away from",
					nearbyCity.getName(), (minDist / 1000.0), "km is_in",
					place.getIsIn());
			log.warn("Number of cities with this name:", nextCityList.size());
		}

		return nearbyCity;
	}

	public void autofillCities() {
		if (locationAutofill.contains("nearest") == false && locationAutofill.contains("is_in") == false) {
			return;
		}
		
		log.info("Locator City   Map contains", cityMap.size(), "entries");
		log.info("Locator Places Map contains", placesMap.size(), "entries");
		log.info("Locator Finder KdTree contains", cityFinder.size(), "entries");

		int runCount = 0;
		int maxRuns = 2;
		int unresCount;

		do {
			unresCount = 0;

			for (MapPoint place : placesMap) {
				if (place != null) {

					// first lets try exact name

					MapPoint near = findCityByIsIn(place);

					// if this didn't worked try to workaround german umlaut

					if (near == null) {
						// TODO perform a soundslike search
					}

					if (near != null) {
						if (place.getCity() == null)
							place.setCity(near.getCity());
						if (place.getZip() == null)
							place.setZip(near.getZip());
					} else if (locationAutofill.contains("nearest") && (runCount + 1) == maxRuns) {
						// In the last resolve run just take info from the next
						// known city
						near = cityFinder.findNextPoint(place);
						if (near != null && near.getCountry() != null) {
							if (place.getCity() == null)
								place.setCity(place.getName());
						}
					}

					if (near != null) {
						if (place.getRegion() == null)
							place.setRegion(near.getRegion());

						if (place.getCountry() == null)
							place.setCountry(near.getCountry());

					}

					if (near == null)
						unresCount++;
				}
			}

			for (int i = 0; i < placesMap.size(); i++) {
				MapPoint place = placesMap.get(i);

				if (place != null) {
					if (place.getCity() != null) {
						addCity(place.getName(), place);
						placesMap.set(i, null);
					} else if ((runCount + 1) == maxRuns) {
						place.setCity(place.getName());
						addCity(place.getName(), place);
					}
				}
			}

			runCount++;

			log.info("Locator City   Map contains", cityMap.size(),
					 "entries after resolver run", runCount,
					 "Still unresolved", unresCount);

		} while (unresCount > 0 && runCount < maxRuns);

	}
	
	/**
	 * Add MapPoint to cityMap and cityFinder
	 *  
	 * @param name Name that is used to find the city
	 * @param p the MapPoint
	 */
	private void addCity(String name, MapPoint p){
		if(name != null)
		{
			cityMap.add(name, p);
			
			// add point to the kd-tree
			cityFinder.add(p);
		}
		
	}
}


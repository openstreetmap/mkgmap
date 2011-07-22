/*
 * Copyright (C) 2009 Bernhard Heibler
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
 *  The Locator tries to fill missing country, region, postal code information
 *
 *	The algorithm works like this:
 *
 *	1. Step: Go through all cities an check if they have useful country region info
 *	The best case is if the tags is_in:country and is_in:county are present that's easy.
 *	Some cities have is_in information that can be used. We check for three different 
 *	formats:
 *
 *	County, State, Country, Continent
 *	County, State, Country
 *	Continent, Country, State, County, ...
 *
 *	In "openGeoDb countries" this info is pretty reliable since it was imported from a 
 *	external db into osm. Other countries have very sparse is_in info.
 *
 *	All this cities that have such info will end up in "city" list. All which lack 
 *	such information in "location" list.
 *
 * 	2. Step: Go through the "location" list and check if the is_in info has some relations 
 *	to the cities we have info about.
 *
 *	Especially hamlets often have no full is_in information. They only have one entry in 
 *	is_in that points to the city they belong to. I will check if I can find the name 
 *	of this city in the "City" list. If there are more with the same name I use the 
 *	closest one. If we can't find the exact name I use fuzzy name search. That's a
 *	workaround for german umlaut since sometimes there are used in the tags and
 *	sometimes there are written as ue ae oe. 
 *
 *	3. Step: Do the same like in step 2 once again. This is used to support at least
 *	one level of recursion in is_in relations.
 *
 *  If there is still no info found I use brute force and use the information from the
 *	next city. Has to be used for countries with poor is_in tagging.
 *
 * Author: Bernhard Heibler
 * Create date: 02-Jan-2009
 */

package uk.me.parabola.mkgmap.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapPointFastFindMap;

public class Locator {
	private static final Logger log = Logger.getLogger(Locator.class);

	private final MapPointFastFindMap cityMap  					= new MapPointFastFindMap();
	private final List<MapPoint> placesMap  =  new ArrayList<MapPoint>();

	private final LocatorConfig locConfig = LocatorConfig.get();

	private final Set<String> locationAutofill = new HashSet<String>();
	
	private static final double MAX_CITY_DIST = 30000;

	private static final Pattern COMMA_OR_SPACE_PATTERN = Pattern.compile("[,\\s]+");
	
	/**
	 * Parses the parameters of the location-autofill option. Establishes also downwards
	 * compatibility with the old integer values of location-autofill. 
	 * @param optionStr the value of location-autofill
	 * @return the options
	 */
	public static Set<String> parseAutofillOption(String optionStr) {
		if (optionStr == null) {
			return Collections.emptySet();
		}

		Set<String> autofillOptions = new HashSet<String>(Arrays.asList(COMMA_OR_SPACE_PATTERN
				.split(optionStr)));

		// convert the old autofill options to the new parameters
		if (autofillOptions.contains("0")) {
			autofillOptions.add("is_in");
			autofillOptions.remove("0");
		}
		if (autofillOptions.contains("1")) {
			autofillOptions.add("is_in");
			// PENDING: fuzzy search
			autofillOptions.remove("1");
		}
		if (autofillOptions.contains("2")) {
			autofillOptions.add("is_in");
			// PENDING: fuzzy search
			autofillOptions.add("nearest");
			autofillOptions.remove("2");
		}		
		if (autofillOptions.contains("3")) {
			autofillOptions.add("is_in");
			// PENDING: fuzzy search
			autofillOptions.add("nearest");
			autofillOptions.remove("3");
		}	
		return autofillOptions;
	}
	
	
	public void addCityOrPlace(MapPoint p) 
	{
		if (p.isCity() == false) 
		{
			log.warn("MapPoint has no city type id: 0x"+Integer.toHexString(p.getType()));
			return;
		}
		
		log.debug("S City 0x"+Integer.toHexString(p.getType()), p.getName(), "|", p.getCity(), "|", p.getRegion(), "|", p.getCountry());

		// correct the country name
		// usually this is the translation from 3letter ISO code to country name
		if(p.getCountry() != null)
			p.setCountry(fixCountryString(p.getCountry()));

		resolveIsInInfo(p); // Pre-process the is_in field

		if(p.getCity() != null)
		{
			if (log.isDebugEnabled())
				log.debug(p.getCity(),p.getRegion(),p.getCountry());
			// Must use p.getName() here because p.getCity() contains the city name of the preprocessed cities
			cityMap.put(p.getName(), p);
		}
		else
		{
			// All other places which do not seam to be a real city has to resolved later
			placesMap.add(p);		
		}
		log.debug("E City 0x"+Integer.toHexString(p.getType()), p.getName(), "|", p.getCity(), "|", p.getRegion(), "|", p.getCountry());
	}

			
	public void setLocationAutofill(Collection<String> autofillOptions) {
		this.locationAutofill.addAll(autofillOptions);
	}

	public void setDefaultCountry(String country, String abbr)
	{
		locConfig.setDefaultCountry(country, abbr);
	}

	public String fixCountryString(String country)
	{
		return locConfig.fixCountryString(country);
	}

	private String isCountry(String country)
	{
		return locConfig.isCountry(country);
	}

	public String getCountryCode(String country)
	{
		return locConfig.getCountryCode(country);
	}

	public int getPOIDispFlag(String country)
	{
		return locConfig.getPoiDispFlag(country);
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
					p.setCountry(fixCountryString(cityList[cityList.length-2].trim()));
				}
				
				// aks the config which info to use for region info				
				int offset = locConfig.getRegionOffset(p.getCountry()) + 1;

				if(cityList.length > offset && p.getRegion() == null)
					p.setRegion(cityList[cityList.length-(offset+1)].trim());

			} else

			// Format 2 other way round: "Continent,Country,State,County"	

			if(cityList.length > 1 && isContinent(cityList[0]))	// Is first a continent ?
			{
				if (p.getCountry() == null) {
					// The one before continent should be the country
					p.setCountry(fixCountryString(cityList[1].trim()));
				}
				
				int offset = locConfig.getRegionOffset(p.getCountry()) + 1;

				if(cityList.length > offset && p.getRegion() == null)
					p.setRegion(cityList[offset].trim());
			} else

			// Format like this "County,State,Country"

			if(p.getCountry() == null && cityList.length > 0)
			{
				// I don't like to check for a list of countries but I don't want other stuff in country field

				String countryStr = isCountry(cityList[cityList.length-1]);

				if(countryStr != null)
				{	
					p.setCountry(countryStr);

					int offset = locConfig.getRegionOffset(countryStr) + 1;

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
		return cityMap.findNextPoint(p);
	}
	
	public MapPoint findNearbyCityByName(MapPoint p) {

		if (p.getCity() == null)
			return null;

		Collection<MapPoint> nextCityList = cityMap.getList(p.getCity());
		if (nextCityList == null) {
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

			Collection<MapPoint> candidateCityList = cityMap
					.getList(cityCandidate);
			if (candidateCityList != null) {
				if (nextCityList == null) {
					nextCityList = new ArrayList<MapPoint>();
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
						near = cityMap.findNextPoint(place);
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
						cityMap.put(place.getName(), place);
						placesMap.set(i, null);
					} else if ((runCount + 1) == maxRuns) {
						place.setCity(place.getName());
						cityMap.put(place.getName(), place);
					}
				}
			}

			runCount++;

			log.info("Locator City   Map contains", cityMap.size(),
					 "entries after resolver run", runCount,
					 "Still unresolved", unresCount);

		} while (unresCount > 0 && runCount < maxRuns);

	}
}


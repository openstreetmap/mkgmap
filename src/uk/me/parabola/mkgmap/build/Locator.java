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
 *  The Locator tries to fill missing country, region, postal coude information
 *
 *	The algorithm works like this:
 *
 *	1. Step: Go through all cities an check if they have useful country region info
 *	The best case is if the tags is_in:country and is_in:county are present thats easy.
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
 *	closest one. If we can't find the exact name I use fuzzy name search. Thats a
 *	workaround for german umlaute since sometimes there are used in the tags and 
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

import java.util.Collection;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapPointFastFindMap;
import uk.me.parabola.mkgmap.general.MapPointMultiMap;

import java.util.Vector;

public class Locator {

	private final MapPointFastFindMap cityMap  					= new MapPointFastFindMap();
	private final MapPointMultiMap  	fuzzyCityMap			= new MapPointMultiMap();
	private final java.util.Vector<MapPoint> placesMap  =  new Vector<MapPoint>();

	private LocatorConfig locConfig = new LocatorConfig();

	static double totalTime = 0;
	static long totalFinds = 0;
	private int autoFillLevel = 0;

	public void addLocation(MapPoint p) 
	{
		resolveIsInInfo(p); // Preprocess the is_in field
		
		if(autoFillLevel < 1 &&  p.getCity() == null)
		{			
			// Without autofill city name is the name of the tag
			p.setCity(p.getName());
		}
		
		if(p.getCity() != null)
		{
			cityMap.put(p.getCity(), p);
			
			fuzzyCityMap.put(fuzzyDecode(p.getCity()),p);

			if(p.getName() != null && p.getCity().equals(p.getName()) == false) // Name variants ? 
				fuzzyCityMap.put(fuzzyDecode(p.getName()),p);
		}
		else
		{
			// All other places which do not seam to be a real city has to resolved later
			placesMap.add(p);		
		}
			
	}

			

	public void setAutoFillLevel(int level)
	{
		autoFillLevel = level;
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

	private boolean isOpenGeoDBCountry(String country)
	{
		// Countries that have open geo db data in osm
		// Right now this are only germany, austria and swizerland
		return locConfig.isOpenGeoDBCountry(country);
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
		if(p.getCountry() != null)
			p.setCountry(fixCountryString(p.getCountry()));

		if(p.getCountry() != null && p.getRegion() != null && p.getCity() == null)
		{		
			p.setCity(p.getName());
			return;
		}

		if(p.getIsIn() != null)
		{	
			String cityList[] = p.getIsIn().split(",");			
			
			//System.out.println(p.getIsIn());

			// is_in content is not well defined so we try our best to get some info out of it
			// Format 1 popular in Germany: "County,State,Country,Continent"

			if(cityList.length > 1 &&
				isContinent(cityList[cityList.length-1]))	// Is last a continent ?
			{
				// The one before contient should be the country
	  		p.setCountry(fixCountryString(cityList[cityList.length-2].trim()));				

				// aks the config which info to use for region info				
				int offset = locConfig.getRegionOffset(p.getCountry()) + 1;

				if(cityList.length > offset)
					p.setRegion(cityList[cityList.length-(offset+1)].trim());

			}

			// Format 2 other way round: "Continent,Country,State,County"	

			if(cityList.length > 1 && isContinent(cityList[0]))	// Is first a continent ?
			{
				// The one before contient should be the country
	  		p.setCountry(fixCountryString(cityList[1].trim()));				
				
				int offset = locConfig.getRegionOffset(p.getCountry()) + 1;

				if(cityList.length > offset)
					p.setRegion(cityList[offset].trim());
			}	

			// Format like this "County,State,Country"

			if(p.getCountry() == null && cityList.length > 0)
			{
				// I don't like to check for a list of countries but I don't want other stuff in country field

				String countryStr = isCountry(cityList[cityList.length-1]);

				if(countryStr != null)
				{	
					p.setCountry(countryStr);

					int offset = locConfig.getRegionOffset(countryStr) + 1;

					if(cityList.length > offset)
		     		p.setRegion(cityList[cityList.length-(offset+1)].trim());	
				}
			}
		}

		if(p.getCountry() != null && p.getRegion() != null && p.getCity() == null)
		{	
			// In OpenGeoDB Countries I don't want to mess up the info which city is a real independent
			// Community in all other countries I just have to do it

			if(isOpenGeoDBCountry(p.getCountry()) == false)	
				p.setCity(p.getName());
		}
	}
	
	public MapPoint findNextPoint(MapPoint p)
	{
		long   startTime = System.nanoTime();
	
		MapPoint nextPoint = null;
	
		nextPoint = cityMap.findNextPoint(p);
		
		totalFinds++;
		totalTime = totalTime + ((System.nanoTime() - startTime)/1e9);
		return nextPoint;
	}
	
	public  MapPoint findByCityName(MapPoint p)
	{
		MapPoint near   = null;
		Double minDist  = Double.MAX_VALUE;
		Collection <MapPoint> nextCityList = null;			
		
		if(p.getCity() == null)
			return null;
		
		nextCityList = cityMap.getList(p.getCity());
				
		if(nextCityList != null)
		{
			for (MapPoint nextCity: nextCityList)		
			{
				Double dist = p.getLocation().distance(nextCity.getLocation());

				if(dist < minDist)
				{
					minDist = dist;
					near = nextCity;
				}
			}
	 	}
		
		nextCityList = fuzzyCityMap.getList(fuzzyDecode(p.getCity()));
		
		if(nextCityList != null)
		{
			for (MapPoint nextCity: nextCityList)		
			{
				Double dist = p.getLocation().distance(nextCity.getLocation());

				if(dist < minDist)
				{
					minDist = dist;
					near = nextCity;
				}
			}
	 	}
		
		if(near != null && minDist < 30000) // Wrong hit more the 30 km away ?
			return near;
		else
			return null;
	}
	
	private MapPoint findCity(MapPoint place, boolean fuzzy)
	{
		MapPoint near   = null;
		Double minDist  = Double.MAX_VALUE;
		Collection <MapPoint> nextCityList = null;	

		String isIn = place.getIsIn();
			
		if(isIn != null)
		{
			String cityList[] = isIn.split(",");
 
			// Go through the isIn string and check if we find a city with this name
			// Lets hope we find the next bigger city 

			for(int i = 0; i < cityList.length; i++)
		  {
		  	String biggerCityName=cityList[i].trim();
				

				if(fuzzy == false)
					nextCityList = cityMap.getList(biggerCityName);
				else
				  nextCityList = fuzzyCityMap.getList(fuzzyDecode(biggerCityName));

				if(nextCityList != null)
				{
					for (MapPoint nextCity: nextCityList)		
					{
						Double dist = place.getLocation().distance(nextCity.getLocation());

						if(dist < minDist)
						{
							minDist = dist;
							near = nextCity;
				  	}
					}
			 	}
			}

			if (autoFillLevel > 3)  // Some debug output to find suspicios relations
			{
			   
				if(near != null && minDist > 30000)
			  {
			  		System.out.println("Locator: " + place.getName() + " is far away from " +
								near.getName() + 	" " + (minDist/1000.0) + " km is_in" +	place.getIsIn());
						if(nextCityList != null)
				   		System.out.println("Number of cities with this name: " + nextCityList.size());					
			  }
			   
 				//if(near != null && fuzzy)
			  //{
				// System.out.println("Locator: " + place.getName() + " may belong to " +
				//			 near.getName() + " is_in" + place.getIsIn());
				//}
			}
		}

		return near;
	}

	public void resolve() {

		if(autoFillLevel < 0)
			return;			// Nothing to do if autofill is fulli disabled

		if(autoFillLevel > 2)
		{
			System.out.println("\nLocator City   Map contains " + cityMap.size() + " entries");
			System.out.println("Locator Places Map contains " + placesMap.size() + " entries");
		}
		
		int runCount = 0;
		int maxRuns = 2;
		int unresCount;
		
		do
		{
			unresCount=0;

			for (int i = 0; i < placesMap.size(); i++)
			{
				MapPoint place = placesMap.get(i);

				if(place != null)
				{

					// first lets try exact name

					MapPoint near = findCity(place, false);

					
					// if this didn't worked try to workaround german umlaute 

		  		if(near == null)
			  		near = findCity(place, true);
				
			  	if(autoFillLevel > 3 && near == null && (runCount + 1) == maxRuns)
			  	{
					  // TODO re-enable on merge
						//if(place.getIsIn() != null)
						//System.out.println("Locator: CAN't locate " + place.getName() + " is_in " +	place.getIsIn()
						//		+ " " + place.getLocation().toOSMURL());
			  	}

	
					if(near != null)
					{
						place.setCity(near.getCity());
						place.setZip(near.getZip());
		     	}
					else if (autoFillLevel > 1 && (runCount + 1) == maxRuns)
					{
							// In the last resolve run just take info from the next known city
							near = cityMap.findNextPoint(place);
					}


					if(near != null) 
					{
						if(place.getRegion() == null)
							place.setRegion(near.getRegion());

						if(place.getCountry() == null)
							place.setCountry(near.getCountry());	

					}

					if(near == null)
						unresCount++;

				}
			}

			for (int i = 0; i < placesMap.size(); i++)
			{
				MapPoint place = placesMap.get(i);

				if (place != null)
				{
					if( place.getCity() != null)
					{
		 	  		cityMap.put(place.getName(),place);
						fuzzyCityMap.put(fuzzyDecode(place.getName()),place);
						placesMap.set(i, null);
					}
					else if(autoFillLevel < 2 && (runCount + 1) == maxRuns)
					{
						place.setCity(place.getName());
						cityMap.put(place.getName(),place);
					}
				}				
			}
			
			runCount++;

			if(autoFillLevel > 2)
				System.out.println("Locator City   Map contains " + cityMap.size() + 
				" entries after resolver run " + runCount + " Still unresolved " + unresCount);
			
		} 
		while(unresCount > 0 && runCount < maxRuns);
		
	}
	
	public void printStat()
	{	
	   System.out.println("Locator Find called: " + totalFinds + " time");
	   System.out.println("Locator Find time:   " + totalTime + " s");
	   
	   cityMap.printStat();
	}	
	
	private String fuzzyDecode(String stringToDecode)
  {

		if(stringToDecode == null)
			return stringToDecode;

		String decodeString = stringToDecode.toUpperCase().trim();

		// German umlaut resolution
		decodeString = decodeString.replaceAll("Ä","AE").replaceAll("Ü","UE").replaceAll("Ö","OE");
		
		//if(decodeString.equals(stringToDecode) == false)
		//	System.out.println(stringToDecode + " -> " + decodeString);

		return (decodeString);
	}
		
}


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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;

/**
 * This is really part of the LBLFile.  We split out all the parts of the file
 * that are to do with location to here.
 *
 * @author Steve Ratcliffe
 */
public class PlacesFile {
	private final Map<String, Country> countries = new LinkedHashMap<String, Country>();
	private final Map<String, Region> regions = new LinkedHashMap<String, Region>();
	private final Map<String, City> cities = new LinkedHashMap<String, City>();
	private final SortedMap<String, City> cityList = new TreeMap<String, City>();
	private final Map<String, Zip> postalCodes = new LinkedHashMap<String, Zip>();
	private final List<POIRecord> pois = new ArrayList<POIRecord>();

	private LBLFile lblFile;
	private PlacesHeader placeHeader;
	private boolean poisClosed;

	/**
	 * We need to have links back to the main LBL file and need to be passed
	 * the part of the header that we manage here.
	 *
	 * @param file The main LBL file, used so that we can create lables.
	 * @param pheader The place header.
	 */
	void init(LBLFile file, PlacesHeader pheader) {
		lblFile = file;
		placeHeader = pheader;
	}

	void write(ImgFileWriter writer) {
		for (Country c : countries.values())
			c.write(writer);
		placeHeader.endCountries(writer.position());

		for (Region r : regions.values())
			r.write(writer);
		placeHeader.endRegions(writer.position());

		for (String s : cityList.keySet())
		{
			City c = cityList.get(s);
			c.write(writer);
		}

		placeHeader.endCity(writer.position());

		int poistart = writer.position();
		byte poiglobalflags = placeHeader.getPOIGlobalFlags();
		for (POIRecord p : pois)
			p.write(writer, poiglobalflags,
				writer.position() - poistart, cities.size(), postalCodes.size());
		placeHeader.endPOI(writer.position());

		for (Zip z : postalCodes.values())
			z.write(writer);
		placeHeader.endZip(writer.position());
	}

	Country createCountry(String name, String abbr) {
	
		String s = abbr != null ? name + (char)0x1d + abbr : name;
			
		Country c = countries.get(s);
	
		if(c == null)
		{
		   c = new Country(countries.size()+1);

		   Label l = lblFile.newLabel(s);
		   c.setLabel(l);
   		 countries.put(s, c);
		}
		return c;
	}

	Region createRegion(Country country, String name, String abbr) {
	
		String s = abbr != null ? name + (char)0x1d + abbr : name;

		String uniqueRegionName = s.toUpperCase().concat(Long.toString(country.getIndex()));	
	
		Region r = regions.get(uniqueRegionName);
		
		if(r == null)
		{
		  r = new Region(country, regions.size()+1);
		  Label l = lblFile.newLabel(s);
		  r.setLabel(l);
		  regions.put(uniqueRegionName, r);
		}
		return r;
	}

	City createCity(Country country, String name, boolean unique) {
		
		String uniqueCityName = name.toUpperCase().concat("_C").concat(Long.toString(country.getIndex()));
		
		City c = null;

		if(!unique)
			c = cities.get(uniqueCityName);
		
		if(c == null)
		{
			c = new City(country);

			Label l = lblFile.newLabel(name);
			c.setLabel(l);

		  cityList.put(name + " 0" + c, c);
			cities.put(uniqueCityName, c);
		}

		return c;
  }

	City createCity(Region region, String name, boolean unique) {
		
		String uniqueCityName = name.toUpperCase().concat("_R").concat(Long.toString(region.getIndex()));
		
		City c = null;

		if(!unique)
			c = cities.get(uniqueCityName);
		
		if(c == null)
		{
		   c = new City(region);

			Label l = lblFile.newLabel(name);
			c.setLabel(l);

			cityList.put(name + " 0" + c, c);
			cities.put(uniqueCityName, c);
		}

		return c;
	}

	private void sortCities()
	{
		int index = 1;

		for (String s : cityList.keySet())
		{
			City c = cityList.get(s);
			c.setIndex(index++);
		}
	}

	Zip createZip(String code) {
	
		Zip z = postalCodes.get(code);
		
		if(z == null)
		{
	  	   z = new Zip(postalCodes.size()+1);

		   Label l = lblFile.newLabel(code);
		   z.setLabel(l);

		   postalCodes.put(code, z);
 	        }
		return z;
	}

	POIRecord createPOI(String name) {
		assert !poisClosed;
		// TODO...
		POIRecord p = new POIRecord();

		Label l = lblFile.newLabel(name);
		p.setLabel(l);

		pois.add(p);
		
		return p;
	}

	void allPOIsDone() {

		sortCities();

		poisClosed = true;

		byte poiFlags = 0;
		for (POIRecord p : pois) {
			poiFlags |= p.getPOIFlags();
		}
		placeHeader.setPOIGlobalFlags(poiFlags);

		int ofs = 0;
		for (POIRecord p : pois)
			ofs += p.calcOffset(ofs, poiFlags, cities.size(), postalCodes.size());
	}
}

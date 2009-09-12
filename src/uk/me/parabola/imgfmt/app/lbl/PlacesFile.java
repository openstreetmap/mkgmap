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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;

import uk.me.parabola.util.Sortable;

import uk.me.parabola.imgfmt.app.Exit;

/**
 * This is really part of the LBLFile.  We split out all the parts of the file
 * that are to do with location to here.
 */
public class PlacesFile {
	private final Map<String, Country> countries = new LinkedHashMap<String, Country>();

	private final Map<String, Region> regions = new LinkedHashMap<String, Region>();
	private final List<Sortable<String, Region>> regionList = new ArrayList<Sortable<String, Region>>();

	private final Map<String, City> cities = new LinkedHashMap<String, City>();
	private final List<Sortable<String, City>> cityList = new ArrayList<Sortable<String, City>>();

	private final Map<String, Zip> postalCodes = new LinkedHashMap<String, Zip>();
	private final List<Highway> highways = new ArrayList<Highway>();
	private final List<ExitFacility> exitFacilities = new ArrayList<ExitFacility>();
	private final List<POIRecord> pois = new ArrayList<POIRecord>();
	private final List[] poiIndex = new ArrayList[256];

	private LBLFile lblFile;
	private PlacesHeader placeHeader;
	private boolean poisClosed;

	//private Collator collator;

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
		//collator = Collator.getInstance(Locale.US); // TODO work out how this should work
	}

	void write(ImgFileWriter writer) {
		for (Country c : countries.values())
			c.write(writer);
		placeHeader.endCountries(writer.position());

		for (Sortable<String, Region> sr : regionList)
			sr.getValue().write(writer);
		placeHeader.endRegions(writer.position());

		for (Sortable<String, City> sc : cityList)
			sc.getValue().write(writer);

		placeHeader.endCity(writer.position());

		for (List<POIIndex> pil : poiIndex) {
			if(pil != null) {
				// sort entries by POI name
				Collections.sort(pil);
				for(POIIndex pi : pil)
					pi.write(writer);
			}
		}
		placeHeader.endPOIIndex(writer.position());

		int poistart = writer.position();
		byte poiglobalflags = placeHeader.getPOIGlobalFlags();
		for (POIRecord p : pois)
			p.write(writer, poiglobalflags,
				writer.position() - poistart, cities.size(), postalCodes.size(), highways.size(), exitFacilities.size());
		placeHeader.endPOI(writer.position());

		int numPoiIndexEntries = 0;
		for (int i = 0; i < 256; ++i) {
			if(poiIndex[i] != null) {
				writer.put((byte)i);
				writer.put3(numPoiIndexEntries + 1);
				numPoiIndexEntries += poiIndex[i].size();
			}
		}
		placeHeader.endPOITypeIndex(writer.position());

		for (Zip z : postalCodes.values())
			z.write(writer);
		placeHeader.endZip(writer.position());

		int extraHighwayDataOffset = 0;
		for (Highway h : highways) {
		    h.setExtraDataOffset(extraHighwayDataOffset);
		    extraHighwayDataOffset += h.getExtraDataSize();
		    h.write(writer, false);
		}
		placeHeader.endHighway(writer.position());

		for (ExitFacility ef : exitFacilities)
			ef.write(writer);
		placeHeader.endExitFacility(writer.position());

		for (Highway h : highways)
			h.write(writer, true);
		placeHeader.endHighwayData(writer.position());
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

		String uniqueRegionName = s.toUpperCase() + "_C" + country.getLabel().getOffset();
	
		Region r = regions.get(uniqueRegionName);
		
		if(r == null)
		{
			r = new Region(country);
			Label l = lblFile.newLabel(s);
			r.setLabel(l);
			regionList.add(new Sortable<String, Region>(name, r));
			regions.put(uniqueRegionName, r);
		}
		return r;
	}

	City createCity(Country country, String name, boolean unique) {
		
		String uniqueCityName = name.toUpperCase() + "_C" + country.getLabel().getOffset();
		
		City c = null;

		if(!unique)
			c = cities.get(uniqueCityName);
		
		if(c == null)
		{
			c = new City(country);

			Label l = lblFile.newLabel(name);
			c.setLabel(l);

			cityList.add(new Sortable<String, City>(name, c));
			cities.put(uniqueCityName, c);
		}

		return c;
	}

	City createCity(Region region, String name, boolean unique) {
		
		String uniqueCityName = name.toUpperCase() + "_R" + region.getLabel().getOffset();
		
		City c = null;

		if(!unique)
			c = cities.get(uniqueCityName);
		
		if(c == null)
		{
			c = new City(region);

			Label l = lblFile.newLabel(name);
			c.setLabel(l);

			cityList.add(new Sortable<String, City>(name, c));
			cities.put(uniqueCityName, c);
		}

		return c;
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

	Highway createHighway(Region region, String name) {
		Highway h = new Highway(region, highways.size()+1);

		Label l = lblFile.newLabel(name);
		h.setLabel(l);

		highways.add(h);
		return h;
	}

	public ExitFacility createExitFacility(int type, char direction, int facilities, String description, boolean last) {
		Label d = lblFile.newLabel(description);
		ExitFacility ef = new ExitFacility(type, direction, facilities, d, last, exitFacilities.size()+1);
		exitFacilities.add(ef);
		return ef;
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

	POIRecord createExitPOI(String name, Exit exit) {
		assert !poisClosed;
		// TODO...
		POIRecord p = new POIRecord();

		Label l = lblFile.newLabel(name);
		p.setLabel(l);

		p.setExit(exit);

		pois.add(p);

		return p;
	}

	POIIndex createPOIIndex(String name, int index, Subdivision group, int type) {
		assert index < 0x100 : "Too many POIS in division";
		POIIndex pi = new POIIndex(name, (byte)index, group, (byte)type);
		int t = type >> 8;
		if(poiIndex[t] == null)
			poiIndex[t] = new ArrayList<POIIndex>();
		poiIndex[t].add(pi);
		return pi;
	}

	void allPOIsDone() {

		int index = 1;
		Collections.sort(regionList);
		for (Sortable<String, Region> sr: regionList)
			sr.getValue().setIndex(index++);

		index = 1;
		Collections.sort(cityList);
		for (Sortable<String, City> sc: cityList)
			sc.getValue().setIndex(index++);

		poisClosed = true;

		byte poiFlags = 0;
		for (POIRecord p : pois) {
			poiFlags |= p.getPOIFlags();
		}
		placeHeader.setPOIGlobalFlags(poiFlags);

		int ofs = 0;
		for (POIRecord p : pois)
			ofs += p.calcOffset(ofs, poiFlags, cities.size(), postalCodes.size(), highways.size(), exitFacilities.size());
	}

	public int numCities() {
		return cities.size();
	}

	public int numZips() {
		return postalCodes.size();
	}

	public int numHighways() {
		return highways.size();
	}

	public int numExitFacilities() {
		return exitFacilities.size();
	}
}

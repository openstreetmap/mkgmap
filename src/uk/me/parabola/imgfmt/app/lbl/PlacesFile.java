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

import uk.me.parabola.imgfmt.app.WriteStrategy;
import uk.me.parabola.imgfmt.app.Label;

import java.util.ArrayList;
import java.util.List;

/**
 * This is really part of the LBLFile.  We split out all the parts of the file
 * that are to do with location to here.
 *
 * @author Steve Ratcliffe
 */
public class PlacesFile {
	private List<Country> countries = new ArrayList<Country>();
	private List<Region> regions = new ArrayList<Region>();
	private List<City> cities = new ArrayList<City>();
	private List<Zip> postalCodes = new ArrayList<Zip>();
	private List<POIRecord> pois = new ArrayList<POIRecord>();

	private PlacesHeader header;

	private LBLFile lbl;

	public PlacesFile(LBLFile lblFile) {
		this.lbl = lblFile;
	}

	void write(WriteStrategy writer) {
		for (Country c : countries)
			c.write(writer);

		header.startRegions(writer.position());
		for (Region r : regions)
			r.write(writer);

		header.startCities(writer.position());
		for (City c : cities)
			c.write(writer);

		header.startZip(writer.position());
		for (Zip z : postalCodes)
			z.write(writer);
	}

	Country createCountry(String name, String abbr) {
		Country c = new Country(countries.size());

		Label l = lbl.newLabel(name);
		c.setLabel(l);

		countries.add(c);
		return c;
	}

	Region createRegion(Country country, String name) {
		Region r = new Region(country, regions.size());

		Label l = lbl.newLabel(name);
		r.setLabel(l);

		regions.add(r);
		return r;
	}

	City createCity(Region region, String name) {
		City c = new City(region, cities.size());

		Label l = lbl.newLabel(name);
		c.setLabel(l);

		cities.add(c);
		return c;
	}

	Zip createZip(String name) {
		Zip z = new Zip(postalCodes.size());

		Label l = lbl.newLabel(name);
		z.setLabel(l);

		postalCodes.add(z);
		return z;
	}
}

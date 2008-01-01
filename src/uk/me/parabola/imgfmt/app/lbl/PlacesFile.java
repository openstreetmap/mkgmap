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

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve Ratcliffe
 */
public class PlacesFile {
	private List<Country> countries = new ArrayList<Country>();
	private List<Region> regions = new ArrayList<Region>();
	private List<City> cities = new ArrayList<City>();
	private List<Zip> postalCodes = new ArrayList<Zip>();
	private List<POIRecord> pois = new ArrayList<POIRecord>();

}

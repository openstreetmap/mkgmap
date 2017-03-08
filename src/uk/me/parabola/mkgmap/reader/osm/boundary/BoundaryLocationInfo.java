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
package uk.me.parabola.mkgmap.reader.osm.boundary;

/**
 * Stores location relevant information that was extracted 
 * from the tags of a boundary.
 * @author GerdP
 *
 */
public class BoundaryLocationInfo  {

	private final String zip;
	private final String name;
	private final int admLevel;

	BoundaryLocationInfo (int admLevel, String name, String zip){
		this.admLevel = admLevel;
		if (admLevel > 0 && name == null)
			this.name = "not_set"; // TODO: review
		else 
			this.name = name;
		this.zip = zip;
	}
	public String getZip() {
		return zip;
	}

	public String getName() {
		return name;
	}

	public int getAdmLevel() {
		return admLevel;
	}
}


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
 * Create date: Feb 17, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;

import uk.me.parabola.log.Logger;

/**
 * A style is a collection of files that describe the mapping between the OSM
 * features and the garmin features.
 *
 * The files are either contained in a directory, in a package or in a zip'ed
 * file.
 *
 * @author Steve Ratcliffe
 */
public class Style {
	private static final Logger log = Logger.getLogger(Style.class);
	
	private File file;

	protected void loadStyleByName(String name) throws FileNotFoundException {
		StyleFileLoader fl = StyleFileLoader.createStyleLoaderByName(name);
		Reader reader = fl.open("version");
		System.out.println("got reader " + reader);
	}

	public static void main(String[] args) throws FileNotFoundException {
		Style s = new Style();

		s.loadStyleByName("testnn");
		s.loadStyle("http://localhost/test.jar");
	}

	private void loadStyle(String loc) throws FileNotFoundException {
		StyleFileLoader fl = StyleFileLoader.createStyleLoader(loc, null);
		Reader reader = fl.open("version");
		System.out.println("got reader " + reader);
	}
}

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

import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.IOException;
import java.util.Scanner;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;

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

	private static final int VERSION = 0;

	private static final String FILE_VERSION = "version";
	private static final String FILE_INFO = "info";
	private static final String FILE_FEATURES = "map-features.csv";
	private static final String FILE_CONTROL = "control";

	private final StyleFileLoader fileLoader;

	/**
	 * Create a style from the given location and name.
	 * @param loc The location of the style. Can be null to mean just check
	 * the classpath.
	 * @param name The name.  Can be null if the location isn't.  If it is
	 * null then we just check for the first version file that can be found.
	 * @throws FileNotFoundException If the file doesn't exist.  This can
	 * include the version file being missing.
	 */
	public Style(String loc, String name) throws FileNotFoundException {
		fileLoader = StyleFileLoader.createStyleLoader(loc, name);

		// There must be a version file, if not then we don't create the style.
		checkVersion();

		//readControl();
	}

	/**
	 * Make an old style converter from the style.  The plan is that to begin
	 * with we will just delegate all style requests to the old
	 * {@link FeatureListConverter}.
	 * @param collector The map collector
	 * @return An old Feature list converter, using the map-features.csv in
	 * the style.
	 * @throws IOException If the map-features.csv file does not exist or can't
	 * be read.
	 */
	public OsmConverter makeConverter(MapCollector collector) throws IOException {
		Reader r = fileLoader.open(FILE_FEATURES);
		OsmConverter converter = new FeatureListConverter(collector, r);
		return converter;
	}

	private void checkVersion() throws FileNotFoundException {
		Reader r = fileLoader.open(FILE_VERSION);
		Scanner scan = new Scanner(r);
		int version = scan.nextInt();
		log.debug("Got version " + version);

		if (version > VERSION) {
			System.err.println("Warning: unrecognised style version " + version +
			", but only understand version " + VERSION);
		}
	}
}
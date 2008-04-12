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

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

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
	//private static final String FILE_INFO = "info";
	private static final String FILE_FEATURES = "map-features.csv";
	private static final String FILE_OPTIONS = "options";

	private final StyleFileLoader fileLoader;
	private String[] nameTagList;

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

		readOptions();
	}

	private void readOptions() {
		try {
			Reader r = fileLoader.open(FILE_OPTIONS);
			BufferedReader br = new BufferedReader(r);

			String line;
			while ((line = br.readLine()) != null) {
				String s = line.trim();

				if (s.length() == 0 || s.charAt(0) == '#')
					continue;

				String[] optval = s.split("[=:]", 2);

				if (optval.length > 1) {
					String opt = optval[0].trim();
					String val = optval[1].trim();

					processOption(opt, val);
				}
			}
		} catch (FileNotFoundException e) {
			// the file is optional, so ignore if not present, or causes error
			log.debug("no options file");
		} catch (IOException e) {
			log.warn("error reading options file");
		}
	}

	private void processOption(String opt, String val) {
		if (opt.equals("name-tag-list")) {
			// The name-tag-list allows you to redifine what you want to use
			// as the name of a feature.  By default this is just 'name', but
			// you can supply a list of tags to use
			// instead eg. "name:en,int_name,name"
			nameTagList = val.split("[,\\s]+");
		}
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
		WordScanner scan = new WordScanner(r);
		int version = scan.nextInt();
		log.debug("Got version " + version);

		if (version > VERSION) {
			System.err.println("Warning: unrecognised style version " + version +
			", but only understand version " + VERSION);
		}
	}

	public String[] getNameTagList() {
		return nameTagList;
	}
}
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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Locale;

/**
 * A style is a collection of files that describe the mapping between the OSM
 * features and the garmin features.  This file reads in those files and
 * provides methods for using the information.
 *
 * The files are either contained in a directory, in a package or in a zip'ed
 * file.
 *
 * @author Steve Ratcliffe
 */
public class Style {
	private static final Logger log = Logger.getLogger(Style.class);

	// This is max the version that we understand
	private static final int VERSION = 0;

	// General options just have a value and don't need any special processing.
	private static final List<String> OPTION_LIST = new ArrayList<String>(
			Arrays.asList("levels"));

	// Options that should not be overriden from the command line if the
	// value is empty.
	private static final List<String> DONT_OVERRIDE = new ArrayList<String>(
			Arrays.asList("levels"));

	// File names
	private static final String FILE_VERSION = "version";
	private static final String FILE_INFO = "info";

	private static final String FILE_FEATURES = "map-features.csv";

	private static final String FILE_OPTIONS = "options";

	// A handle on the style directory or file.
	private final StyleFileLoader fileLoader;

	// The general information in the 'info' file.
	private final StyleInfo info = new StyleInfo();

	// A list of tag names to be used as the element name
	private String[] nameTagList;

	// Options from the option file that are used outside this file.
	private final Map<String, String> generalOptions = new HashMap<String, String>();

	private Map<String, TypeRule> ways = new HashMap<String, TypeRule>();
	//private Map<String, TypeRule> wayKeys = new HashMap<String, TypeRule>();
	private Map<String, TypeRule> nodes = new HashMap<String, TypeRule>();
	//private Map<String, TypeRule> nodeKeys = new HashMap<String, TypeRule>();
	private DefaultFeatureNames defaultNames;

	private int nextIndex = 100000000;

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

		readInfo();

		readDefaultNames();
		
		readMapFeatures();
	}

	public String[] getNameTagList() {
		return nameTagList;
	}


	public String getOption(String name) {
		return generalOptions.get(name);
	}

	public StyleInfo getInfo() {
		return info;
	}

	public Map<String, TypeRule> getWays() {
		return ways;
	}

	public Map<String, TypeRule> getNodes() {
		return nodes;
	}

	private void readDefaultNames() {
		defaultNames = new DefaultFeatureNames(fileLoader, Locale.getDefault());
	}

	/**
	 * Read the map-features file.  This is the old format of the mapping
	 * between osm and garmin types.
	 */
	private void readMapFeatures() {
		try {
			Reader r = fileLoader.open(FILE_FEATURES);
			MapFeatureReader mfr = new MapFeatureReader();
			mfr.readFeatures(new BufferedReader(r));
			initFromMapFeatures(mfr);
		} catch (FileNotFoundException e) {
			// optional file
			log.debug("no map-features file");
		} catch (IOException e) {
			log.error("could not read map features file");
		}
	}

	/**
	 * Take the output of the map-features file and create rules for
	 * each line and add to this style.  All rules in map-features are
	 * unconditional, in other words the osm 'amenity=cinema' always
	 * maps to the same garmin type.
	 *
	 * @param mfr The map feature file reader.
	 */
	private void initFromMapFeatures(MapFeatureReader mfr) {
		nextIndex -= 10000;
		for (Map.Entry<String, GType> me : mfr.getLineFeatures().entrySet()) {
			TypeRule value = createRule(me.getKey(), me.getValue());
			ways.put(me.getKey(), value);
		}

		for (Map.Entry<String, GType> me : mfr.getShapeFeatures().entrySet()) {
			TypeRule value = createRule(me.getKey(), me.getValue());
			ways.put(me.getKey(), value);
		}

		for (Map.Entry<String, GType> me : mfr.getPointFeatures().entrySet()) {
			TypeRule value = createRule(me.getKey(), me.getValue());
			nodes.put(me.getKey(), value);
		}
	}

	/**
	 * Create a rule from a raw gtype. You get raw gtypes when you
	 * have read the types from a map-features file.
	 *
	 * @return A rule that always resolves to the given type.  It will
	 * also have its priority set so that rules earlier in a file
	 * will override those later.
	 */
	private TypeRule createRule(String key, GType gt) {
		gt.setDefaultName(defaultNames.get(key));
		if (gt.getDefaultName() != null)
			log.debug("set default name of " + gt.getDefaultName() + " for  " + key);
		TypeRule value = new NoRule(gt);
		value.setPriority(nextIndex++);
		return value;
	}

	/**
	 * After the style is loaded we override any options that might
	 * have been set in the style itself with the command line options.
	 *
	 * We may have to filter some options that we don't ever want to
	 * set on the command line.
	 *
	 * @param config The command line options.
	 */
	void applyOptionOverride(Properties config) {
		Set<Map.Entry<Object,Object>> entries = config.entrySet();
		for (Map.Entry<Object,Object> ent : entries) {
			String key = (String) ent.getKey();
			String val = (String) ent.getValue();

			if (!DONT_OVERRIDE.contains(key))
				processOption(key, val);
		}
	}

	/**
	 * If there is an options file, then read it and keep options that
	 * we are interested in.
	 */
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
			// instead eg. "name:en,int_name,name" or you could use some
			// completely different tag...
			nameTagList = val.split("[,\\s]+");
		} else if (OPTION_LIST.contains(opt)) {
			// Simple options that have string value.  Perhaps we should alow
			// anything here?
			generalOptions.put(opt, val);
		}
	}

	private void readInfo() {
		try {
			BufferedReader r = new BufferedReader(fileLoader.open(FILE_INFO));
			WordScanner ws = new WordScanner(r);

			info.readInfo(ws);
		} catch (FileNotFoundException e) {
			// optional file..
			log.debug("no info file");
		}
	}

	/**
	 * Merge another style into this one.  The style will have a lower
	 * priority, in other words if rules in the current style match the
	 * other one, then the current rule wins.
	 *
	 * This is used to base styles on other ones, without having to repeat
	 * everything.
	 */
	private void mergeStyle(Style other) {
		//for (Map.Entry<String, TypeRule> ent : other.ways.entrySet())
		//	addWayKeyVal(ent.getKey(), createRule(ent));
		//
		//
		//for (Map.Entry<String, TypeRule> ent : other.wayKeys.entrySet())
		//	addWayKey(ent.getKey(), createRule(ent));
		//
		//
		//for (Map.Entry<String, TypeRule> ent : other.ways.entrySet())
		//	addNodeKeyVal(ent.getKey(), createRule(ent));
		//
		//
		//for (Map.Entry<String, TypeRule> ent : other.ways.entrySet())
		//	addNodeKey(ent.getKey(), createRule(ent));
		//
		//nextIndex -= 10000;
	}

	private void checkVersion() throws FileNotFoundException {
		Reader r = fileLoader.open(FILE_VERSION);
		WordScanner scan = new WordScanner(r);
		int version = scan.nextInt();
		log.debug("Got version " + version);

		if (version > VERSION) {
			System.err.println("Warning: unrecognised style version " + version +
			", but only versions up to " + VERSION + " are understood");
		}
	}
}
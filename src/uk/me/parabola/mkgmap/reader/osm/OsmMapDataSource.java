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
 * Create date: 22-Sep-2007
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Base class for OSM map sources.  It exists so that more than
 * one version of the api can be supported at a time.
 *
 * @author Steve Ratcliffe
 */
public abstract class OsmMapDataSource extends MapperBasedMapDataSource
		implements LoadableMapDataSource
{
	private static final Logger log = Logger.getLogger(OsmMapDataSource.class);

	private Style style;
	private final OsmReadingHooks[] POSSIBLE_HOOKS = {
			new SeaGenerator(),
			new HighwayHooks(),
	};
	private OsmConverter converter;
	private final Set<String> usedTags = new HashSet<String>();
	protected ElementSaver elementSaver;
	protected OsmReadingHooks osmReadingHooks;

	/**
	 * Get the maps levels to be used for the current map.  This can be
	 * specified in a number of ways in order:
	 * <ol>
	 * <li>On the command line with the --levels flag.
	 * The format is a comma (or space) separated list of level/resolution
	 * pairs.  Eg --levels=0:24,1:22,2:20
	 * If the flag is given without an argument then the command line override
	 * is turned off for maps following that option.
	 *
	 * <li>In the style options file.  This works just like the command line
	 * option, but it applies whenever the given style is used and not overridden
	 * on the command line.
	 *
	 * <li>A default setting.
	 * </ol>
	 *
	 * <p>I'd advise that new styles specify their own set of levels.
	 *
	 * @return An array of level information, basically a [level,resolution]
	 * pair.
	 */
	public LevelInfo[] mapLevels() {

		// First try command line, then style, then our default.
		String levelSpec = getConfig().getProperty("levels");
		log.debug("levels", levelSpec, ", ", ((levelSpec!=null)?levelSpec.length():""));
		if (levelSpec == null || levelSpec.length() < 2) {
			if (style != null) {
				levelSpec = style.getOption("levels");
				log.debug("getting levels from style:", levelSpec);
			}
		}

		if (levelSpec == null)
			levelSpec = LevelInfo.DEFAULT_LEVELS;

		return LevelInfo.createFromString(levelSpec);
	}

	/**
	 * There are no copyright messages in the OSM files themselves.  So we
	 * include a fixed set of strings on the assumption that .osm files
	 * are probably going to have the OSM copyright statements.
	 *
	 * @return A list of copyright messages as a String array.
	 */
	public String[] copyrightMessages() {
		return new String[] {
				"OpenStreetMap.org contributors",
				"See: http://wiki.openstreetmap.org/index.php/Attribution"
		};
	}

	protected void setStyle(Style style) {
		this.style = style;
	}

	/**
	 * Common code to setup the file handler.
	 * @param handler The file handler.
	 */
	protected void setupHandler(OsmHandler handler) {
		createElementSaver();
		osmReadingHooks = pluginChain(elementSaver, getConfig());

		handler.setElementSaver(elementSaver);
		handler.setHooks(osmReadingHooks);

		createConverter();

		handler.setUsedTags(getUsedTags());

		String deleteTagsFileName = getConfig().getProperty("delete-tags-file");
		if(deleteTagsFileName != null) {
			Map<String, Set<String>> deltags = readDeleteTagsFile(deleteTagsFileName);
			handler.setTagsToDelete(deltags);
		}
	}
	
	protected void createElementSaver() {
		elementSaver = new ElementSaver(getConfig());
	}
	
	public ElementSaver getElementSaver() {
		return elementSaver;
	}

	protected OsmReadingHooks[] getPossibleHooks() {
		return this.POSSIBLE_HOOKS;
	}
	
	protected OsmReadingHooks pluginChain(ElementSaver saver, EnhancedProperties props) {
		List<OsmReadingHooks> plugins = new ArrayList<OsmReadingHooks>();

		for (OsmReadingHooks p : getPossibleHooks()) {
			if (p.init(saver, props))
				plugins.add(p);
		}

		OsmReadingHooks hooks;
		switch (plugins.size()) {
		case 0:
			hooks = new OsmReadingHooksAdaptor();
			break;
		case 1:
			hooks = plugins.get(0);
			break;
		default:
			OsmReadingHooksChain chain = new OsmReadingHooksChain();
			for (OsmReadingHooks p : plugins) {
				chain.add(p);
			}
			hooks = chain;
		}
		getUsedTags().addAll(hooks.getUsedTags());
		return hooks;
	}

	private Map<String, Set<String>> readDeleteTagsFile(String fileName) {
		Map<String, Set<String>> deletedTags = new HashMap<String,Set<String>>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String line;
			while((line = br.readLine()) != null) {
				line = line.trim();
				if(line.length() > 0 && !line.startsWith("#") && !line.startsWith(";")) {
					String[] parts = line.split("=");
					if (parts.length == 2) {
						parts[0] = parts[0].trim();
						parts[1] = parts[1].trim();
						if ("*".equals(parts[1])) {
							deletedTags.put(parts[0], new HashSet<String>());
						} else {
							Set<String> vals = deletedTags.get(parts[0]);
							if (vals == null)
								vals = new HashSet<String>();
							vals.add(parts[1]);
							deletedTags.put(parts[0], vals);
						}
					} else {
						log.error("Ignoring bad line in deleted tags file: " + line);
					}
				}
			}
			br.close();
		}
		catch(FileNotFoundException e) {
			log.error("Could not open delete tags file " + fileName);
		}
		catch(IOException e) {
			log.error("Error reading delete tags file " + fileName);
		}

		if(deletedTags.isEmpty())
			deletedTags = null;

		return deletedTags;
	}

	/**
	 * Create the appropriate converter from osm to garmin styles.
	 *
	 * The option --style-file give the location of an alternate file or
	 * directory containing styles rather than the default built in ones.
	 *
	 * The option --style gives the name of a style, either one of the
	 * built in ones or selects one from the given style-file.
	 *
	 * If there is no name given, but there is a file then the file should
	 * just contain one style.
	 *
	 */
	private void createConverter() {

		Properties props = getConfig();
		String loc = props.getProperty("style-file");
		if (loc == null)
			loc = props.getProperty("map-features");
		String name = props.getProperty("style");

		if (loc == null && name == null)
			name = "default";

		try {
			Style style = new StyleImpl(loc, name);
			style.applyOptionOverride(props);
			setStyle(style);

			getUsedTags().addAll(style.getUsedTags());
			converter = new StyledConverter(style, mapper, props);
		} catch (SyntaxException e) {
			System.err.println("Error in style: " + e.getMessage());
			throw new ExitException("Could not open style " + name);
		} catch (FileNotFoundException e) {
			String name1 = (name != null)? name: loc;
			throw new ExitException("Could not open style " + name1);
		}
	}

	public OsmConverter getConverter() {
		return converter;
	}

	public Set<String> getUsedTags() {
		return usedTags;
	}
}

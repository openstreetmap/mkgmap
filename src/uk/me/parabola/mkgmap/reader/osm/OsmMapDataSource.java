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

import java.util.Arrays;
import java.util.Properties;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ConfiguredByProperties;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;

/**
 * Base class for OSM map sources.  It exists so that more than
 * one version of the api can be supported at a time.
 *
 * @author Steve Ratcliffe
 */
public abstract class OsmMapDataSource extends MapperBasedMapDataSource
		implements LoadableMapDataSource, ConfiguredByProperties
{
	private static final Logger log = Logger.getLogger(OsmMapDataSource.class);

	private static final String DEFAULT_LEVELS = "0:24, 1:22, 2:20, 3:18, 4:16";

	private Properties configProps;
	private Style style;

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
	 * option, but it applies whenever the given style is used and not overriden
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
		LevelInfo[] levels;

		// First try command line, then style, then our default.
		String levelSpec = configProps.getProperty("levels");
		log.debug("levels", levelSpec, ", ", ((levelSpec!=null)?levelSpec.length():""));
		if (levelSpec == null || levelSpec.length() < 2) {
			if (style != null) {
				levelSpec = style.getOption("levels");
				log.debug("getting levels from style:", levelSpec);
			}
		}

		if (levelSpec == null)
			levelSpec = DEFAULT_LEVELS;

		levels = createLevels(levelSpec);

		return levels;
	}

	/**
	 * Convert a string into an array of LevelInfo structures.
	 */
	private LevelInfo[] createLevels(String levelSpec) {
		String[] desc = levelSpec.split("[, \\t\\n]+");
		LevelInfo[] levels = new LevelInfo[desc.length];

		int count = 0;
		for (String s : desc) {
			String[] keyVal = s.split("[=:]");
			if (keyVal == null || keyVal.length < 2) {
				System.err.println("incorrect level specification " + levelSpec);
				continue;
			}
			
			try {
				int key = Integer.parseInt(keyVal[0]);
				int value = Integer.parseInt(keyVal[1]);
				levels[count] = new LevelInfo(key, value);
			} catch (NumberFormatException e) {
				System.err.println("Levels specification not all numbers " + keyVal[count]);
			}
			count++;
		}

		Arrays.sort(levels);

		if (log.isDebugEnabled()) {
			for (LevelInfo li : levels) {
				log.debug("Level:", li);
			}
		}

		// If there are more than 8 levels the map can cause the
		// garmin to crash.
		if (levels.length > 8) {
			throw new ExitException("Too many levels");
		}
		return levels;
	}

	public String[] copyrightMessages() {
		return new String[] {"OpenStreetMap.org contributors."};
	}

	public void config(Properties props) {
		this.configProps = props;
	}

	Properties getConfig() {
		return configProps;
	}

	public void setStyle(Style style) {
		this.style = style;
	}
}

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

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ConfiguredByProperties;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.plugin.MapperBasedMapDataSource;

import java.util.Arrays;
import java.util.Properties;

/**
 * @author Steve Ratcliffe
 */
public abstract class OsmMapDataSource extends MapperBasedMapDataSource
		implements LoadableMapDataSource, ConfiguredByProperties
{
	private static final Logger log = Logger.getLogger(OsmMapDataSource.class);

	private Properties props;

	public LevelInfo[] mapLevels() {
		String configuredLevels = props.getProperty("levels");
		LevelInfo[] levels;

		if (configuredLevels != null) {
			levels = createLevels(configuredLevels);
		} else {
			// We return a fixed mapping at present.
			levels = new LevelInfo[]{
					new LevelInfo(5, 16),
					new LevelInfo(4, 18),
					new LevelInfo(3, 19),
					new LevelInfo(2, 21),
					new LevelInfo(1, 22),
					new LevelInfo(0, 24),
			};
		}

		return levels;
	}

	private LevelInfo[] createLevels(String configuredLevels) {
		String[] desc = configuredLevels.split("[, \\t\\n]");
		LevelInfo[] levels = new LevelInfo[desc.length];

		int count = 0;
		for (String s : desc) {
			String[] keyVal = s.split("[=:]");
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
				log.debug("Level: " + li);
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
		this.props = props;
	}

	Properties getConfig() {
		return props;
	}
}

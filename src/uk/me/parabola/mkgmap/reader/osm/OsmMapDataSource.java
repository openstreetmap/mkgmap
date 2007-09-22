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

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ConfiguredByProperties;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * @author Steve Ratcliffe
 */
public abstract class OsmMapDataSource
		implements LoadableMapDataSource, ConfiguredByProperties
{
	private static final Logger log = Logger.getLogger(OsmMapDataSource.class);

	protected final MapDetails mapper = new MapDetails();
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
				System.err.println("The levels specification failed for " + keyVal[count]);
			}
			count++;
		}

		Arrays.sort(levels);

		if (log.isDebugEnabled()) {
			for (LevelInfo li : levels) {
				log.debug("Level: " + li);
			}
		}
		return levels;
	}

	public String copyrightMessage() {
		return "OpenStreetMap.org contributors.";
	}

	/**
	 * Get the area that this map covers. Delegates to the map collector.
	 *
	 * @return The area the map covers.
	 */
	public Area getBounds() {
		return mapper.getBounds();
	}

	/**
	 * Get the list of point for the map.
	 * @return A list of points.
	 */
	public List<MapPoint> getPoints() {
		return mapper.getPoints();
	}

	/**
	 * Get the list of lines that need to be rendered to the map. Delegates to
	 * the map collector.
	 *
	 * @return A list of {@link MapLine} objects.
	 */
	public List<MapLine> getLines() {
		return mapper.getLines();
	}

	/**
	 * Get the polygons for the map.
	 * @return A list of polygons.
	 */
	public List<MapShape> getShapes() {
		return mapper.getShapes();
	}

	/**
	 * Get a list of every feature that is used in the map.  As features are
	 * created a list is kept of each separate feature that is used.  This
	 * goes into the .img file and is important for points and polygons although
	 * it doesn't seem to matter if lines are represented or not on my Legend Cx
	 * anyway.
	 *
	 * @return A list of all the types of point, polygon and polyline that are
	 * used in the map.
	 */
	public List<Overview> getOverviews() {
		return mapper.getOverviews();
	}

	public void config(Properties props) {
		this.props = props;
	}

	Properties getConfig() {
		return props;
	}
}

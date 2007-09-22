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
 * Create date: 02-Sep-2007
 */
package uk.me.parabola.mkgmap.reader.test;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.ConfiguredByProperties;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Properties;

/**
 * This is a map data source that just generates maps without reference to
 * any external data.
 * 
 * @author Steve Ratcliffe
 */
public class ElementTestDataSource implements LoadableMapDataSource, ConfiguredByProperties {
	private final MapDetails mapper = new MapDetails();
	private Properties props;

	/**
	 * 'Filenames' that are supported begin with test-map:
	 * @param name The name to check.
	 * @return True If a recognised test name beginning with test-map:
	 */
	public boolean isFileSupported(String name) {
		if (name == null)
			return false;
		
		if (name.startsWith("test-map:"))
			return true;
		return false;
	}

	/**
	 * Load a map by generating it in code.
	 * @param name The name of the map to generate.
	 * @throws FileNotFoundException If the name is not recognised.
	 */
	public void load(String name) throws FileNotFoundException {
		if ("test-map:all-elements".equals(name)) {
			AllElements all = new AllElements();
			all.load(mapper);
		} else if ("test-map:test-points".equals(name)) {
			TestPoints test = new TestPoints();
			test.load(mapper, props);
		} else {
			throw new FileNotFoundException("Invalid test file name");
		}
	}

	public LevelInfo[] mapLevels() {
		return new LevelInfo[] {
				new LevelInfo(3, 16),
				new LevelInfo(2, 18),
				new LevelInfo(1, 20),
				new LevelInfo(0, 24),
		};
	}

	public String copyrightMessage() {
		return "test data";
	}

	public List<Overview> getOverviews() {
		return mapper.getOverviews();
	}

	public Area getBounds() {
		return mapper.getBounds();
	}

	public List<MapPoint> getPoints() {
		return mapper.getPoints();
	}

	public List<MapLine> getLines() {
		return mapper.getLines();
	}

	public List<MapShape> getShapes() {
		return mapper.getShapes();
	}

	public void config(Properties props) {
		this.props = props;
	}
}

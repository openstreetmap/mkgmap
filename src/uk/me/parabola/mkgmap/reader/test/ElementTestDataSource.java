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

import java.io.FileNotFoundException;
import java.util.Properties;

import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This is a map data source that just generates maps without reference to
 * any external data.
 * 
 * @author Steve Ratcliffe
 */
public class ElementTestDataSource extends MapperBasedMapDataSource implements LoadableMapDataSource {
	private Properties configProps;

	/**
	 * 'Filenames' that are supported begin with test-map:
	 * @param name The name to check.
	 * @return True If a recognised test name beginning with test-map:
	 */
	public boolean isFileSupported(String name) {
		return name != null && name.startsWith("test-map:");
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
			test.load(mapper, configProps);
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

	public LevelInfo[] overviewMapLevels() {
		return null; // TODO: probably this should return something 
	}

	public String[] copyrightMessages() {
		return new String[] {"test data"};
	}

	public void config(EnhancedProperties props) {
		this.configProps = props;
	}
}

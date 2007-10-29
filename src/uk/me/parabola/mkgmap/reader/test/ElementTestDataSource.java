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

import uk.me.parabola.mkgmap.ConfiguredByProperties;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.plugin.MapperBasedMapDataSource;

import java.io.FileNotFoundException;
import java.util.Properties;

/**
 * This is a map data source that just generates maps without reference to
 * any external data.
 * 
 * @author Steve Ratcliffe
 */
public class ElementTestDataSource extends MapperBasedMapDataSource implements LoadableMapDataSource, ConfiguredByProperties {
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

	public String[] copyrightMessages() {
		return new String[] {"test data"};
	}

	public void config(Properties configProps) {
		this.props = configProps;
	}
}

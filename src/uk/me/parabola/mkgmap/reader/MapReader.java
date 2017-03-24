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
package uk.me.parabola.mkgmap.reader;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.bin.OsmBinMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.o5m.O5mBinMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5MapDataSource;
import uk.me.parabola.mkgmap.reader.polish.PolishMapDataSource;
import uk.me.parabola.mkgmap.reader.test.ElementTestDataSource;

/**
 * Class to find the correct map reader to use, based on the type of the file
 * to be read.
 *
 * Allows new map readers to be registered, the map readers are in charge of
 * recognising file formats that they can deal with.
 *
 * @author Steve Ratcliffe
 */
public class MapReader {
	private static final List<LoadableMapDataSource> loaders;
	static {
		loaders = new ArrayList<>();
		loaders.add(new OsmBinMapDataSource());
		loaders.add(new O5mBinMapDataSource());
		loaders.add(new PolishMapDataSource());
		loaders.add(new ElementTestDataSource());
		//loaders.add(new Osm5MapDataSource()); not needed in list, is fall back option
	}

	/**
	 * Return a suitable map reader.  The name of the resource to be read is
	 * passed in.  This is usually a file name, but could be something else.
	 *
	 * @param name The resource name to be read.
	 * @return A LoadableMapDataSource that is capable of reading the resource.
	 */
	public static LoadableMapDataSource createMapReader(String name) {
		for (LoadableMapDataSource loader : loaders) {
			if (loader.isFileSupported(name)) {
				try {
					return loader.getClass().newInstance();
				} catch (InstantiationException e) {
					// try the next one.
				} catch (IllegalAccessException e) {
					// try the next one.
				}
			}
		}

		// Give up and assume it is in the XML format. If it isn't we will get an
		// error soon enough anyway.
		return new Osm5MapDataSource();
	}
}

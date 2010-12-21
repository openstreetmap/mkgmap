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
package uk.me.parabola.mkgmap.reader.plugin;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5MapDataSource;

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

	private static final List<Class<? extends LoadableMapDataSource>> loaders;

	static {
		String[] sources = {
				"uk.me.parabola.mkgmap.reader.osm.bin.OsmBinMapDataSource",
				"uk.me.parabola.mkgmap.reader.polish.PolishMapDataSource",
				"uk.me.parabola.mkgmap.reader.test.ElementTestDataSource",

				// must be last as it is the default
				"uk.me.parabola.mkgmap.reader.osm.xml.Osm5MapDataSource",
		};

		loaders = new ArrayList<Class<? extends LoadableMapDataSource>>();

		for (String source : sources) {
			try {
				@SuppressWarnings({"unchecked"})
				Class<? extends LoadableMapDataSource> c = (Class<? extends LoadableMapDataSource>) Class.forName(source);
				loaders.add(c);
			} catch (ClassNotFoundException e) {
				// not available, try the rest
			} catch (NoClassDefFoundError e) {
				// not available, try the rest
			}
		}
	}

	/**
	 * Return a suitable map reader.  The name of the resource to be read is
	 * passed in.  This is usually a file name, but could be something else.
	 *
	 * @param name The resource name to be read.
	 * @return A LoadableMapDataSource that is capable of reading the resource.
	 */
	public static LoadableMapDataSource createMapReader(String name) {
		for (Class<? extends LoadableMapDataSource> loader : loaders) {
			try {
				LoadableMapDataSource src = loader.newInstance();
				if (name != null && src.isFileSupported(name))
					return src;
			} catch (InstantiationException e) {
				// try the next one.
			} catch (IllegalAccessException e) {
				// try the next one.
			} catch (NoClassDefFoundError e) {
				// try the next one
			}
		}

		// Give up and assume it is in the XML format. If it isn't we will get an
		// error soon enough anyway.
		return new Osm5MapDataSource();
	}
}

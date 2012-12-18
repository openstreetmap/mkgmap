/*
 * Copyright (C) 2010 - 2012.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package uk.me.parabola.mkgmap.reader.osm.o5m;

import java.io.FileNotFoundException;
import java.io.InputStream;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.mkgmap.reader.osm.OsmMapDataSource;

/**
 * Read an OpenStreetMap data file in .o5m format.  It is converted
 * into a generic format that the map is built from.
 * <p>The intermediate format is important as several passes are required to
 * produce the map at different zoom levels. At lower resolutions, some roads
 * will have fewer points or won't be shown at all.
 *
 * @author GerdP
 */
public class O5mBinMapDataSource extends OsmMapDataSource {

	public boolean isFileSupported(String name) {
		return name.endsWith(".o5m");
	}

	/**
	 * Load the .o5m file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		InputStream is = Utils.openFile(name);

		O5mBinHandler handler = new O5mBinHandler(is);

		setupHandler(handler);

		handler.parse();
		elementSaver.finishLoading();

		osmReadingHooks.end();
		osmReadingHooks = null;
		
		// now convert the saved elements
		elementSaver.convert(getConverter());

		addBackground();
	}
}

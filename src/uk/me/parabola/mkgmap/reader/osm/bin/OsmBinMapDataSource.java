/*
 * Copyright (C) 2010 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */
package uk.me.parabola.mkgmap.reader.osm.bin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.mkgmap.reader.osm.OsmMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.bin.OsmBinHandler.BinParser;

import crosby.binary.file.BlockInputStream;

/**
 * Read an OpenStreetMap data file in .osm version 0.5 format.  It is converted
 * into a generic format that the map is built from.
 * <p>The intermediate format is important as several passes are required to
 * produce the map at different zoom levels. At lower resolutions, some roads
 * will have fewer points or won't be shown at all.
 *
 * @author Steve Ratcliffe
 */
public class OsmBinMapDataSource extends OsmMapDataSource {

	public boolean isFileSupported(String name) {
		// temporarily use the .bin extension to indicate Scott's format.
		return name.endsWith(".bin");
	}

	/**
	 * Load the .osm file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		InputStream is = Utils.openFile(name);

		OsmBinHandler handler = new OsmBinHandler(getConfig());

		setupHandler(handler);

		try {
			BinParser reader = handler.new BinParser();
			BlockInputStream stream = new BlockInputStream(is, reader);
			stream.process();
		} catch (NoClassDefFoundError e) {
			throw new FormatException("Failed to read binary file, probably missing protobuf.jar");
		} catch (IOException e) {
			throw new FormatException("Failed to read binary file " + name);
		}

		osmReadingHooks.end();

		// now convert the saved elements
		elementSaver.convert(getConverter());

		addBackground();
	}
}

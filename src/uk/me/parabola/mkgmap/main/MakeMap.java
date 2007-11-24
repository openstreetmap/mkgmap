/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ConfiguredByProperties;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapBuilder;
import uk.me.parabola.mkgmap.reader.plugin.MapReader;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main routine for the command line map-making utility.
 *
 * @author Steve Ratcliffe
 */
public class MakeMap  implements MapProcessor {
	private static final Logger log = Logger.getLogger(MakeMap.class);

	//private MapEventListener overview = new OverviewMapBuilder();
	private List<MapEventListener> mapListeners = new ArrayList<MapEventListener>();

	public void processFilename(CommandArgs args, String filename) {
		LoadableMapDataSource src;
		try {
			src = loadFromFile(args, filename);
			makeMap(args, src);
		} catch (FormatException e) {
			System.err.println("Bad file format: " + filename);
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file: " + filename);
		}
	}

	public void endOfOptions() {
		fireFinishEvent();
	}

	public void addMapListener(MapEventListener l) {
		mapListeners.add(l);
	}

	/**
	 * Make a map from the given map data source.
	 *
	 * @param args User supplied arguments.
	 * @param src The data source to load.
	 */
	void makeMap(CommandArgs args, LoadableMapDataSource src) {

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(args.getBlockSize());
		params.setMapDescription(args.getDescription());

		Map map;
		try {
			map = Map.createMap(args.getMapname(), params);
			setOptions(map, args);

			MapBuilder builder = new MapBuilder();
			builder.makeMap(map, src);

			// Collect information on map complete.
			fireMapEndEvent(args, src, map);
			log.info("finished making map, closing");
			if (map != null)
				map.close();

		} catch (FileExistsException e) {
			throw new ExitException("File exists already", e);
		} catch (FileNotWritableException e) {
			throw new ExitException("Could not create or write to file", e);
		}
	}

	/**
	 * Fire an event at the end of every map that is produced.
	 * @param args The command aguments in effect.
	 * @param src The data source used to produce the map.
	 * @param map The map that was created.
	 */
	private void fireMapEndEvent(CommandArgs args, LoadableMapDataSource src, Map map) {
		for (MapEventListener l : mapListeners) {
			l.onMapEnd(args, src, map);
		}
	}

	/**
	 * Fire an event to all the listeners at the end of all files.
	 */
	private void fireFinishEvent() {
		for (MapEventListener l : mapListeners) {
			l.onFinish();
		}
	}

	/**
	 * Set options from the command line.
	 *
	 * @param map The map to modify.
	 * @param args The command line arguments.
	 */
	private void setOptions(Map map, CommandArgs args) {
		String s = args.getCharset();
		if (s != null)
			map.setLabelCharset(s);

		int i = args.getCodePage();
		if (i != 0)
			map.setLabelCodePage(i);
	}

	/**
	 * Load up from the file.  It is not necessary for the map reader to completely
	 * read the whole file in at once, it could pull in map-features as needed.
	 *
	 * @param args The user supplied parameters.
	 * @param name The filename or resource name to be read.
	 * @return A LoadableMapDataSource that will be used to construct the map.
	 * @throws FileNotFoundException For non existing files.
	 * @throws FormatException When the file format is not valid.
	 */
	private LoadableMapDataSource loadFromFile(CommandArgs args, String name) throws
			FileNotFoundException, FormatException
	{
		LoadableMapDataSource src;
		src = MapReader.createMapReader(name);
		if (src instanceof ConfiguredByProperties)
			((ConfiguredByProperties) src).config(args.getProperties());
		src.load(name);
		return src;
	}
}

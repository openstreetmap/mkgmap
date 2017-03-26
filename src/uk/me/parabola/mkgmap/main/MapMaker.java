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

import java.io.File;
import java.io.FileNotFoundException;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.build.MapBuilder;
import uk.me.parabola.mkgmap.combiners.OverviewBuilder;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.MapReader;

/**
 * Main routine for the command line map-making utility.
 *
 * @author Steve Ratcliffe
 */
public class MapMaker implements MapProcessor {
	private static final Logger log = Logger.getLogger(MapMaker.class);
	private Sort sort;
	private final boolean createOverviewFiles;

	public MapMaker(boolean createOverviewFiles) {
		this.createOverviewFiles = createOverviewFiles;
	}

	public String makeMap(CommandArgs args, String filename) {
		try {
			LoadableMapDataSource src = loadFromFile(args, filename);
			sort = args.getSort();
			if (createOverviewFiles){
				if (src.overviewMapLevels() != null){
					makeMap(args, src, OverviewBuilder.OVERVIEW_PREFIX);
				} else {
					String fname = OverviewBuilder.getOverviewImgName(args.getMapname());
					File f = new File(fname);
					if (f.exists()) {
						if (f.isFile() )
							f.delete();
						else {
							// TODO: error message ?
						}
					}
				}
			}
			return makeMap(args, src, "");
		} catch (FormatException e) {
			System.err.println("Bad file format: " + filename);
			System.err.println(e.getMessage());
			return filename;
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file: " + filename);
			return filename;
		}
	}

	/**
	 * Make a map from the given map data source.
	 *
	 * @param args User supplied arguments.
	 * @param src The data source to load.
	 * @param mapNameExt 
	 * @return The output filename for the map.
	 */
	private String makeMap(CommandArgs args, LoadableMapDataSource src, String mapNamePrefix) {

		if (src.getBounds().isEmpty())
			return null;

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(args.getBlockSize());
		params.setMapDescription(args.getDescription());
		log.info("Started making", args.getMapname(), "(" + args.getDescription() + ")");
		try {
			Map map = Map.createMap(mapNamePrefix + args.getMapname(), args.getOutputDir(), params, args.getMapname(), sort);
			setOptions(map, args);

			MapBuilder builder = new MapBuilder();
			builder.config(args.getProperties());
			if(! OverviewBuilder.OVERVIEW_PREFIX.equals(mapNamePrefix)){
				if (args.getProperties().containsKey("route") || args.getProperties().containsKey("net"))
					builder.setDoRoads(true);
			}
			builder.makeMap(map, src);

			// Collect information on map complete.
			String outName = map.getFilename();
			log.info("finished making map", outName, "closing");
			map.close();
			return outName;
		} catch (FileExistsException e) {
			throw new MapFailedException("File exists already", e);
		} catch (FileNotWritableException e) {
			throw new MapFailedException("Could not create or write to file", e);
		}
	}

	/**
	 * Set options from the command line.
	 *
	 * @param map The map to modify.
	 * @param args The command line arguments.
	 */
	private void setOptions(Map map, CommandArgs args) {
		map.config(args.getProperties());

		String s = args.getCharset();
		if (s != null)
			map.setLabelCharset(s, args.isForceUpper());

		Sort sort = args.getSort();
		map.setSort(sort);
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
		LoadableMapDataSource src = MapReader.createMapReader(name);
		src.config(args.getProperties());
		log.info("Started loading", name);
		src.load(name, args.getProperties().getProperty("transparent", false) == false);
		log.info("Finished loading", name);
		return src;
	}
}

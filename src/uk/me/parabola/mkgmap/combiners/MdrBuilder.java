/*
 * Copyright (C) 2009.
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
package uk.me.parabola.mkgmap.combiners;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.Country;
import uk.me.parabola.imgfmt.app.lbl.POIRecord;
import uk.me.parabola.imgfmt.app.lbl.Region;
import uk.me.parabola.imgfmt.app.map.MapReader;
import uk.me.parabola.imgfmt.app.mdr.MDRFile;
import uk.me.parabola.imgfmt.app.mdr.Mdr5Record;
import uk.me.parabola.imgfmt.app.mdr.MdrConfig;
import uk.me.parabola.imgfmt.app.trergn.Point;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.mkgmap.CommandArgs;

/**
 * Create the global index file.  This consists of an img file containing
 * an MDR file and optionally an SRT file.
 *
 * @author Steve Ratcliffe
 */
public class MdrBuilder implements Combiner {
	private MDRFile mdrFile;

	// Push things onto this stack to have them closed in the reverse order.
	private final Deque<Closeable> toClose = new ArrayDeque<Closeable>();

	/**
	 * Create the mdr file and initialise.
	 * It has a name that is based on the overview-mapname option, as does
	 * the associated MDX file.
	 *
	 * @param args The command line arguments.
	 */
	public void init(CommandArgs args) {
		String name = args.get("overview-mapname", "osmmap");

		ImgChannel mdrChan;
		try {
			// Create the .img file system/archive
			FileSystemParam params = new FileSystemParam();
			params.setBlockSize(args.get("block-size", 4096));
			FileSystem fs = ImgFS.createFs(name + "_mdr.img", params);
			toClose.push(fs);

			// Create the MDR file within the .img
			mdrChan = fs.create(name.toUpperCase(Locale.ENGLISH) + ".MDR");
			toClose.push(mdrChan);
		} catch (IOException e) {
			throw new ExitException("Could not create global index file");
		}

		// Set the options that we are using for the mdr.
		MdrConfig config = new MdrConfig();
		config.setHeaderLen(286);
		config.setWritable(true);
		config.setForDevice(false);

		// Wrap the MDR channel with the MDRFile object
		mdrFile = new MDRFile(mdrChan, config);
		toClose.push(mdrFile);
	}

	/**
	 * Adds a new map to the file.  We need to read in the img file and
	 * extract all the information that can be indexed from it.
	 *
	 * @param finfo An interface to read the map.
	 */
	public void onMapEnd(FileInfo finfo) {
		if (!finfo.isImg())
			return;
		
		// Add the map name
		mdrFile.addMap(finfo.getMapnameAsInt());

		String filename = finfo.getFilename();
		MapReader mr = null;
		try {
			mr = new MapReader(filename);

			addCountries(mr);
			addRegions(mr);
			Map<Integer, Mdr5Record> cityMap = makeCityMap(mr);
			addPoints(mr, cityMap);
			addCities(cityMap);
			addStreets(mr);
		} catch (FileNotFoundException e) {
			throw new ExitException("Could not open " + filename + " when creating mdr file");
		} finally {
			Utils.closeFile(mr);
		}
	}

	private void addCountries(MapReader mr) {
		List<Country> countries = mr.getCountries();
		for (Country c : countries)
			mdrFile.addCountry(c);
	}

	private void addRegions(MapReader mr) {
		List<Region> regions = mr.getRegions();
		for (Region region : regions)
			mdrFile.addRegion(region);
	}

	private void addCities(Map<Integer, Mdr5Record> cityMap) {
		for (Mdr5Record c : cityMap.values()) {
			mdrFile.addCity(c);
		}
	}

	/**
	 * Make a map from the subdivision and point index of the city within
	 * its own map to the MDR city record.
	 *
	 * This is used to link the city to its name when we read the points.
	 *
	 * @param mr The reader for the map.
	 * @return Map with subdiv<<8 + point-index as the key and a newly created
	 * MDR city record as the value.
	 */
	private Map<Integer, Mdr5Record> makeCityMap(MapReader mr) {
		List<City> cities = mr.getCities();

		Map<Integer, Mdr5Record> cityMap = new LinkedHashMap<Integer, Mdr5Record>();
		for (City c : cities) {
			int key = (c.getSubdivNumber() << 8) + (c.getPointIndex() & 0xff);
			assert key < 0xffffff;
			cityMap.put(key, new Mdr5Record(c));
		}

		return cityMap;
	}

	/**
	 * Read points from this map and add them to the index.
	 * @param mr The currently open map.
	 * @param cityMap Cites indexed by subdiv and point index.
	 */
	private void addPoints(MapReader mr, Map<Integer, Mdr5Record> cityMap) {
		List<Point> list = mr.pointsForLevel(0);
		for (Point p : list) {
			Label label = p.getLabel();
			if (p.getNumber() > 256) {
				// I think we limit the number of points+ind-points, but just in case
				System.out.println("point number too big");
				continue;
			}

			Mdr5Record city = null;
			if (p.getType() < 0x11) {
				// This is itself a city, it gets a reference to its own MDR 5 record.
				// and we also use it to set the name of the city.
				city = cityMap.get((p.getSubdiv().getNumber() << 8) + p.getNumber());
				if (city != null) {
					city.setLblOffset(label.getOffset());
					city.setName(label.getText());
				}

			} else {
				// This is not a city, but we have information about which city
				// it is in.  If so then add the mdr5 record number of the city.
				POIRecord poi = p.getPOIRecord();
				City c = poi.getCity();
				if (c != null)
					city = cityMap.get((c.getSubdivNumber()<<8) + (c.getPointIndex() & 0xff));
			}

			if (label != null && label.getText().trim().length() > 0)
				mdrFile.addPoint(p, city);
		}
	}

	private void addStreets(MapReader mr) {
		List<Polyline> list = mr.linesForLevel(0);
		for (Polyline l : list) {
			// Routable street types 0x01-0x13; 0x16; 0x1a; 0x1b
			int type = l.getType();
			if (type < 0x13 || type == 0x16 || type == 0x1a || type == 0x1b) {
				Label label = l.getLabel();
				if (label != null && label.getText().trim().length() > 0)
					mdrFile.addStreet(l);
			}
		}
	}

	public void onFinish() {
		// Write out the mdr file
		mdrFile.write();

		// Close everything
		for (Closeable file : toClose)
			Utils.closeFile(file);
	}
}

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

import uk.me.parabola.mkgmap.osm.ReadOsm;
import uk.me.parabola.mkgmap.MapDetails;
import uk.me.parabola.mkgmap.MapLine;
import uk.me.parabola.mkgmap.MapSource;
import uk.me.parabola.mkgmap.FormatException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.*;
import uk.me.parabola.imgfmt.sys.FileSystem;

import java.io.FileNotFoundException;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * Main routine to make a map as a command line utility.
 *
 * @author Steve Ratcliffe
 */
public class MakeMap {
	private static final Logger log = Logger.getLogger(MakeMap.class);

	public static void main(String[] args) {
		String name = args[0];
		String mapname = "63240001";

		Args a = new Args();
		a.setName(name);
		a.setMapname(mapname);

		MakeMap mm = new MakeMap();
		mm.makeMap(a);
	}

	private void makeMap(Args args) {
		FileSystem fs = initMap(args.getMapname());
		Map map = Map.createMap(fs, "32860001");

		MapDetails mapDetails = loadFromFile(args.getName());

		processLines(map, mapDetails);

		map.close();
		fs.close();
	}

	private void processLines(Map map, MapDetails details) {
		TREFile tre = map.getTRE();
		LBLFile lbl = map.getLBL();
		RGNFile rgn = map.getRGN();

		// The bounds of the map.
		Area bounds = details.getBounds();
		tre.setBounds(bounds);

		// You can any old junk here.
		tre.addInfo("Created by mkgmap");
		tre.addInfo("Program released under the GPL");
		tre.addInfo("Map data licenced under Creative Commons Attribution-ShareAlike 2.0");

		// There must be an empty zoom level at the least detailed level.
		Zoom z1 = tre.createZoom(1, 24);

		z1.setInherited(true);
		Subdivision div = Subdivision.topLevelSubdivision(bounds, z1);
		rgn.addDivision(div);

		// Create the most detailed view.
		Zoom z = tre.createZoom(0, 24);
		div = div.createSubdivision(bounds, z);
		rgn.addDivision(div);

		// Set the list of features supported on the map.
		Overview ov = new Overview(6, 1);
		tre.addPolylineOverview(ov);

		// Set the fact that there are lines in the map.
		div.setHasPolylines(true);

		List<MapLine> lines = details.getLines();
		for (MapLine line : lines) {
			Polyline pl = new Polyline(div, 6);
			String name = line.getName();
			if (name == null)
				continue;

			log.debug("Road " + name);
			Label label = lbl.newLabel(name);
			List<Coord> points = line.getPoints();
			for (Coord co : points) {
				log.debug("  point at " + co);
				pl.addCoord(co);
			}

			pl.setLabel(label);
			rgn.addMapObject(pl);
		}
	}

	private FileSystem initMap(String mapname) {
		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("OSM street map");

		FileSystem fs = null;
		try {
			fs = new FileSystem(mapname + ".img", params);

			return fs;
		} catch (FileNotFoundException e) {
			System.err.println("Could not create map " + mapname);
			System.exit(1);

			// This can't really be reached.
			throw new IllegalArgumentException("ignore", e);
		} finally {
			if (fs != null)
				fs.close();
		}
	}

	private MapDetails loadFromFile(String name) {
		try {
			MapSource src = new ReadOsm();

			MapDetails details = new MapDetails();
			src.setMapCollector(details);
			src.load(name);

			return details;
		} catch (FileNotFoundException e) {
			log.error("open fail", e);
			System.err.println("Could not open file: " + name);
			System.exit(1);
		} catch (FormatException e) {
			System.err.println("Bad input file format");
			System.exit(1);
		}
		return null;
	}

	private static class Args {
		private String name;
		private String mapname;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getMapname() {
			return mapname;
		}

		public void setMapname(String mapname) {
			this.mapname = mapname;
		}
	}
}

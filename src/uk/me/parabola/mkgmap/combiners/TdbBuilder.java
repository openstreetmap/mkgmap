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
 * Create date: Dec 9, 2007
 */
package uk.me.parabola.mkgmap.combiners;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.build.MapBuilder;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.overview.OverviewMap;
import uk.me.parabola.mkgmap.reader.overview.OverviewMapDataSource;
import uk.me.parabola.tdbfmt.DetailMapBlock;
import uk.me.parabola.tdbfmt.TdbFile;

/**
 * Build the TDB file and the overview map.
 *
 * @author Steve Ratcliffe
 */
public class TdbBuilder implements Combiner {
	private static final Logger log = Logger.getLogger(TdbBuilder.class);

	private OverviewMap overviewSource = new OverviewMapDataSource();

	private final TdbFile tdb = new TdbFile();

	private int parent = 63240000;
	private String overviewMapname;

	/**
	 * Initialise by saving all the information we require from the command line
	 * args.
	 *
	 * @param args The command line arguments as they are at the end of the list.
	 * In otherwords if the same argument appears more than once, then it will
	 * have the value that was set last.
	 */
	public void init(CommandArgs args) {
		overviewMapname = "63240000";  // TODO get from args
		// TODO parent = ...

		// TODO Get these from command args
		int productId = 42;
		int productVersion = 1;
		String seriesName = "OSM map";
		String familyName = "OSM map";

		tdb.setProductInfo(productId, productVersion, seriesName, familyName);
		tdb.addCopyright("Coverted by mkgmap");
	}

	/**
	 * Called at the end of every map that is to be combined.  We only deal
	 * with IMG files and ignore everything else.
	 *
	 * @param finfo Information on the file.
	 */
	public void onMapEnd(FileInfo finfo) {
		if (!finfo.isImg())
			return;
		
		addToTdb(finfo);
		addToOverviewMap(finfo);
	}

	/**
	 * Add the information about the current map to the tdb file.
	 *
	 * @param finfo Information about the current .img file.
	 */
	private void addToTdb(FileInfo finfo) {
		DetailMapBlock detail = new DetailMapBlock();
		detail.setArea(finfo.getBounds());
		String mapname = finfo.getMapname();
		detail.setMapName(mapname);

		String desc = mapname + '(' + mapname + ')';
		detail.setDescription(desc);
		detail.setLblDataSize(finfo.getLblsize());
		detail.setTreDataSize(finfo.getTresize());
		detail.setRgnDataSize(finfo.getRgnsize());

		log.info("overview-name", parent);
		detail.setParentMapNumber(parent);

		tdb.addDetail(detail);
	}

	/**
	 * Add an individual .img file to the overview map.
	 *
	 * @param finfo Information about an individual map.
	 */
	private void addToOverviewMap(FileInfo finfo) {
		Area bounds = finfo.getBounds();

		// Add a background polygon for this map.
		Coord start, co;
		List<Coord> points = new ArrayList<Coord>();
		start = new Coord(bounds.getMinLat(), bounds.getMinLong());
		points.add(start);
		overviewSource.addToBounds(start);

		co = new Coord(bounds.getMinLat(), bounds.getMaxLong());
		points.add(co);
		overviewSource.addToBounds(co);

		co = new Coord(bounds.getMaxLat(), bounds.getMaxLong());
		points.add(co);
		overviewSource.addToBounds(co);

		co = new Coord(bounds.getMaxLat(), bounds.getMinLong());
		points.add(co);
		overviewSource.addToBounds(co);

		points.add(start);

		// Create the background rectangle
		MapShape bg = new MapShape();
		bg.setType(0x4a);
		bg.setPoints(points);
		bg.setMinResolution(10);
		bg.setName(finfo.getDescription() + '\u001d' + finfo.getMapname());

		overviewSource.addShape(bg);
	}

	/**
	 * Called when all the .img files have been processed.  We finish up and
	 * create the TDB file and the overview map.
	 */
	public void onFinish() {
		log.debug("finishing overview");

		// We can set the overall bounds easily as it was calcualted as part of
		// the overview map.
		tdb.setOverview(overviewMapname, overviewSource.getBounds());

		writeTdbFile();
		writeOverviewMap();
	}

	/**
	 * Write out the overview map.
	 */
	private void writeOverviewMap() {
		MapBuilder mb = new MapBuilder();
		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("Overview map");

		try {
			Map map = Map.createMap(overviewMapname, params);
			mb.makeMap(map, overviewSource);
			map.close();
		} catch (FileExistsException e) {
			throw new ExitException("Could not create overview map", e);
		} catch (FileNotWritableException e) {
			throw new ExitException("Could not write to overview map", e);
		}
	}

	/**
	 * Write out the TDB file at the end of processing.
	 */
	private void writeTdbFile() {
		try {
			tdb.write(overviewMapname + ".tdb");
		} catch (IOException e) {
			log.error("tdb write", e);
			throw new ExitException("Could not write the TDB file", e);
		}
	}
}

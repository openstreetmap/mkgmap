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

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.build.MapBuilder;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.tdbfmt.DetailMapBlock;
import uk.me.parabola.tdbfmt.TdbFile;

/**
 * Build the TDB file and the overview map.
 *
 * @author Steve Ratcliffe
 */
public class TdbBuilder implements Combiner {
	private static final Logger log = Logger.getLogger(TdbBuilder.class);

	private OverviewMap overviewSource;

	private TdbFile tdb;

	private int parent = 63240000;
	private String overviewMapname;
	private int tdbVersion;

	/**
	 * Initialise by saving all the information we require from the command line
	 * args.
	 *
	 * @param args The command line arguments as they are at the end of the list.
	 * In otherwords if the same argument appears more than once, then it will
	 * have the value that was set last.
	 */
	public void init(CommandArgs args) {
		overviewMapname = args.get("overview-mapname", "63240000");
		try {
			parent = Integer.parseInt(overviewMapname);
		} catch (NumberFormatException e) {
			log.debug("overview map name not an integer", overviewMapname);
		}

		int familyId = args.get("family-id", 0);
		int productId = args.get("product-id", 1);
		
		String seriesName = args.get("series-name", "OSM map");
		String familyName = args.get("family-name", "OSM map");

		if (args.exists("tdb-v4")) {
			tdbVersion = TdbFile.TDB_V407;
		} else {
			tdbVersion = TdbFile.TDB_V3;
		}

		tdb = new TdbFile(tdbVersion);
		tdb.setProductInfo(familyId, productId, (short) 100, seriesName, familyName);
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
		DetailMapBlock detail = new DetailMapBlock(tdbVersion);
		detail.setArea(finfo.getBounds());
		String mapname = finfo.getMapname();
		String mapdesc = finfo.getDescription();

		detail.setMapName(mapname);

		String desc = mapdesc + '(' + mapname + ')';
		detail.setDescription(desc);
		detail.setLblDataSize(finfo.getLblsize());
		detail.setTreDataSize(finfo.getTresize());
		detail.setRgnDataSize(finfo.getRgnsize());
		detail.setNetDataSize(finfo.getNetsize());
		detail.setNodDataSize(finfo.getNodsize());

		log.info("overview-name", parent);
		detail.setParentMapNumber(parent);

		tdb.addDetail(detail);

		String[] msgs = finfo.getCopyrights();
		for (String m : msgs)
			tdb.addCopyright(m);
	}

	/**
	 * Add an individual .img file to the overview map.
	 *
	 * @param finfo Information about an individual map.
	 */
	private void addToOverviewMap(FileInfo finfo) {
		Area bounds = finfo.getBounds();

		//System.out.printf("overview shift %d\n", overviewSource.getShift());
		int overviewMask = ((1 << overviewSource.getShift()) - 1);
		//System.out.printf("mask %x\n", overviewMask);
		//System.out.println("overviewSource.getShift() = " + overviewSource.getShift());

		int maxLon = roundUp(bounds.getMaxLong(), overviewMask);
		int maxLat = roundUp(bounds.getMaxLat(), overviewMask);
		int minLat = roundDown(bounds.getMinLat(), overviewMask);
		int minLon = roundDown(bounds.getMinLong(), overviewMask);

		//System.out.printf("maxLat 0x%x, modified=0x%x\n", bounds.getMaxLat(), maxLat);
		//System.out.printf("maxLat %f, modified=%f\n", Utils.toDegrees(bounds.getMaxLat()), Utils.toDegrees(maxLat));
		//System.out.printf("minLat 0x%x, modified=0x%x\n", bounds.getMinLat(), minLat);
		//System.out.printf("minLat %f, modified=%f\n", Utils.toDegrees(bounds.getMinLat()), Utils.toDegrees(minLat));
		//System.out.printf("maxLon 0x%x, modified=0x%x\n", bounds.getMaxLong(), maxLon);
		//System.out.printf("maxLon %f, modified=%f\n", Utils.toDegrees(bounds.getMaxLong()), Utils.toDegrees(maxLon));
		//System.out.printf("minLon 0x%x, modified=0x%x\n", bounds.getMinLong(), minLon);
		//System.out.printf("minLon %f, modified=%f\n", Utils.toDegrees(bounds.getMinLong()), Utils.toDegrees(minLon));

		// Add a background polygon for this map.
		List<Coord> points = new ArrayList<Coord>();

		Coord start = new Coord(minLat, minLon);
		points.add(start);
		overviewSource.addToBounds(start);

		Coord co = new Coord(maxLat, minLon);
		points.add(co);
		overviewSource.addToBounds(co);

		co = new Coord(maxLat, maxLon);
		points.add(co);
		overviewSource.addToBounds(co);

		co = new Coord(minLat, maxLon);
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

	private int roundUp(int len, int overviewMask) {
		//System.out.printf("before up 0x%x\n", len);
		if (len > 0)
			return (len + overviewMask) & ~overviewMask;
		else
			return len & ~overviewMask;
	}

	private int roundDown(int len, int overviewMask) {
		//System.out.printf("before down 0x%x\n", len);
		if (len > 0)
			return len & ~overviewMask;
		else
			return -(-len +overviewMask & ~overviewMask);
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

	public void setOverviewSource(OverviewMap overviewSource) {
		this.overviewSource = overviewSource;
	}
}

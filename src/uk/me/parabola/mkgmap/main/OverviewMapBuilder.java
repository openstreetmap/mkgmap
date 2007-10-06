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
 * Create date: 29-Sep-2007
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapBuilder;
import uk.me.parabola.mkgmap.reader.overview.OverviewMapDataSource;
import uk.me.parabola.tdbfmt.DetailMapBlock;
import uk.me.parabola.tdbfmt.TdbFile;

import java.io.IOException;
import java.util.Properties;

/**
 * Builds an overview map and the corresponding TDB file for use with
 * QLandkarte and MapSource etc.
 *
 * @author Steve Ratcliffe
 */
public class OverviewMapBuilder implements MapEvents {
	private static final Logger log = Logger.getLogger(OverviewMapBuilder.class);
	
	private final OverviewMapDataSource overviewSource = new OverviewMapDataSource();
	private final TdbFile tdb = new TdbFile();

	public OverviewMapBuilder() {
		init();
	}

	private void init() {
		tdb.setProductInfo(42, 1, "OSM map", "OSM map");
	}

	public void onMapEnd(CommandArgs args, LoadableMapDataSource src, Map map) {
		log.info("end of map", args);
		Properties currentOptions = args.getProperties();
		overviewSource.addMapDataSource(src, currentOptions);

		for (String c : src.copyrightMessages()) {
			tdb.addCopyright(c);
		}

		long lblsize = map.getLblFile().position();
		long tresize = map.getTreFile().position();
		long rgnsize = map.getRgnFile().position();

		DetailMapBlock detail = new DetailMapBlock();
		detail.setArea(src.getBounds());
		String mapname = args.getMapname();
		detail.setMapName(mapname);

		String desc = mapname + '(' + mapname + ')';
		detail.setDescription(desc);
		detail.setLblDataSize((int) lblsize);
		detail.setTreDataSize((int) tresize);
		detail.setRgnDataSize((int) rgnsize);

		int parent = Integer.parseInt(currentOptions.getProperty("overview-name"));
		log.info("overview-name", parent);
		detail.setParentMapNumber(parent);

		tdb.addDetail(detail);

		log.debug("sizes", lblsize, tresize, rgnsize);
	}

	public void onFinish() {
		log.debug("finishing overview");

		String overviewMapname = "63240000";

		tdb.setOverview(overviewMapname, overviewSource.getBounds());

		try {
			tdb.write(overviewMapname + ".tdb");
		} catch (IOException e) {
			log.error("tdb write", e);
			throw new ExitException("Could not write the TDB file");
		}

		MapBuilder mb = new MapBuilder();
		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("Overview map");

		Map map;
		try {
			map = Map.createMap(overviewMapname, params);
			mb.makeMap(map, overviewSource);
			map.close();
		} catch (FileExistsException e) {
			throw new ExitException("Could not create overview map");
		} catch (FileNotWritableException e) {
			throw new ExitException("Could not write to overview map");
		}
	}
}

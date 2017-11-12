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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.map.MapReader;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.tdbfmt.DetailMapBlock;
import uk.me.parabola.tdbfmt.TdbFile;

/**
 * Build the TDB file and the overview map.
 *
 * @author Steve Ratcliffe
 */
public class TdbBuilder implements Combiner {
	private static final Logger log = Logger.getLogger(TdbBuilder.class);

	private final OverviewBuilder overviewBuilder;
	
	private TdbFile tdb;

	private int parent = 63240000;
	private String overviewMapname;
	private String overviewMapnumber;
	private String outputDir;
	private int tdbVersion;
	private final List<String[]> copyrightMsgs = new ArrayList<>();

	public TdbBuilder(OverviewBuilder ovb) {
		overviewBuilder = ovb;
	}


	/**
	 * Initialise by saving all the information we require from the command line
	 * args.
	 *
	 * @param args The command line arguments as they are at the end of the list.
	 * In other words if the same argument appears more than once, then it will
	 * have the latest value set.
	 */
	public void init(CommandArgs args) {
		overviewMapname = args.get("overview-mapname", "osmmap");
		overviewMapnumber = args.get("overview-mapnumber", "63240000");
		
		try {
			parent = Integer.parseInt(overviewMapnumber);
		} catch (NumberFormatException e) {
			log.debug("overview map number not an integer", overviewMapnumber);
		}

		String areaName = args.get("area-name", "Overview Map");

		int familyId = args.get("family-id", CommandArgs.DEFAULT_FAMILYID);
		int productId = args.get("product-id", 1);
		short productVersion = (short)args.get("product-version", 100);

		String seriesName = args.get("series-name", "OSM map");
		String familyName = args.get("family-name", "OSM map");

		tdbVersion = TdbFile.TDB_V407;

		// enable "show profile" button for routes in mapsource 
		// this is supported only in version 403 and above
		byte enableProfile = (byte) args.get("show-profiles", 0);

		tdb = new TdbFile(tdbVersion);
		tdb.setProductInfo(familyId, productId, productVersion, seriesName,
				familyName, areaName, enableProfile);
		tdb.setCodePage(args.getCodePage());
		
		outputDir = args.getOutputDir();
	}

	/**
	 * Called at the end of every map that is to be combined.  We only deal
	 * with IMG files and ignore everything else.
	 *
	 * @param info Information on the file.
	 */
	public void onMapEnd(FileInfo info) {
		if (!info.isImg())
			return;
		
		addToTdb(info);
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
		detail.setInnername(finfo.getInnername());

		String desc = mapdesc + " (" + mapname + ')';
		detail.setDescription(desc);
		detail.setLblDataSize(finfo.getLblsize());
		detail.setTreDataSize(finfo.getTresize());
		detail.setRgnDataSize(finfo.getRgnsize());
		detail.setNetDataSize(finfo.getNetsize());
		detail.setNodDataSize(finfo.getNodsize());
		detail.setDemDataSize(finfo.getDemsize());
		if (finfo.getDemsize() > 0)
			tdb.setHasDem();

		log.info("overview-mapname", overviewMapname);
		log.info("overview-mapnumber", parent);
		detail.setParentMapNumber(parent);

		tdb.addDetail(detail);

		String[] msgs = finfo.getLicenseInfo();
		for (String m : msgs)
			tdb.addCopyright(m);

		MapReader mapReader = null;
		String filename = finfo.getFilename();
		try{
			mapReader = new MapReader(filename);

			msgs = mapReader.getCopyrights();
			boolean found = false;
			for (String[] block : copyrightMsgs) {
				if (Arrays.deepEquals(block, msgs)){
					found = true;
					break;
				}
			}
			if (!found ){
				copyrightMsgs.add(msgs);

				for (String m : msgs)
					tdb.addCopyright(m);
			}

		} catch (FileNotFoundException e) {
			throw new ExitException("Could not open " + filename + " when creating tdb file");
		} finally {
			Utils.closeFile(mapReader);
		}


	}

	/**
	 * Called when all the .img files have been processed.  We finish up and
	 * create the TDB file and the overview map.
	 */
	public void onFinish() {
		log.debug("finishing overview");

		// We can set the overall bounds easily as it was calculated as part of
		// the overview map.
		tdb.setOverview(overviewBuilder.getBounds(), overviewMapnumber);

		writeTdbFile();
	}

	public String getFilename() {
		return Utils.joinPath(outputDir, overviewMapname, "tdb");
	}

	/**
	 * Write out the TDB file at the end of processing.
	 */
	private void writeTdbFile() {
		try {
			tdb.write(Utils.joinPath(outputDir, overviewMapname, "tdb"));
		} catch (IOException e) {
			log.error("tdb write", e);
			throw new ExitException("Could not write the TDB file", e);
		}
	}
}

/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryFile2Gpx {
	private final File boundaryFile;
	private List<Boundary> boundaries;

	public BoundaryFile2Gpx(File boundaryFile) {
		this.boundaryFile = boundaryFile;
		boundaries = new ArrayList<Boundary>();
	}

	public void loadFile() {
		try {
			boundaries = BoundaryUtil.loadBoundaryFile(boundaryFile, null);
			System.out.println(boundaries.size() + " boundaries loaded");
		} catch (IOException exp) {
			System.err.println("Error while converting boundary file "
					+ boundaryFile);
			System.err.println(exp);
			exp.printStackTrace();
		}
	}

	public void saveEmptyAreas(String tagKey, String tagValue) {
		Area tileArea = Java2DConverter.createBoundsArea(BoundaryUtil
				.getBbox(boundaryFile));

		int matchingBounds = 0;
		for (Boundary b : boundaries) {
			// check if the boundary matches to the filter
			if (tagKey == null || tagValue == null
					|| tagValue.equals(b.getTags().get(tagKey))) {

				// it matches => subtract it from the tile area
				tileArea.subtract(b.getArea());
				matchingBounds++;
			}
		}

		if (matchingBounds == 0) {
			System.out.println("No boundary with " + tagKey + "=" + tagValue
					+ " found.");
		} else if (tileArea.isEmpty()) {
			System.out.println(tagKey + "=" + tagValue
					+ " is covered completely.");
		} else {
			String gpxBasename = "gpx/" + boundaryFile.getName()
					+ "/uncovered/";
			if (tagKey == null || tagValue == null) {
				gpxBasename += "all/";
			} else {
				gpxBasename += tagKey + tagValue + "/";
			}

			List<List<Coord>> emptyPolys = Java2DConverter
					.areaToShapes(tileArea);
			Collections.reverse(emptyPolys);

			int i = 0;
			for (List<Coord> emptyPart : emptyPolys) {
				Way w = new Way(0, emptyPart);
				String attr = w.clockwise() ? "o" : "i";
				GpxCreator.createGpx(gpxBasename + i + "_" + attr, emptyPart);
				i++;
			}
		}
	}

	public void saveAsGpx() {
		System.out.println("Start converting " + boundaryFile);

		for (Boundary b : boundaries) {
			// get the admin_level tag
			String admLevel = b.getTags().get("admin_level");
			if (admLevel == null) {
				admLevel = "notset";
			}

			String bId = b.getTags().get("mkgmap:boundaryid");

			String gpxBasename = "gpx/" + boundaryFile.getName()
					+ "/covered/admin_level=" + admLevel + "/" + admLevel + "_" + bId
					+ "_";

			int i = 0;
			for (BoundaryElement be : b.getBoundaryElements()) {
				String gpxFile = gpxBasename;
				if (be.isOuter()) {
					gpxFile += "o_" + i;
				} else {
					gpxFile = "i_" + i;
				}
				GpxCreator.createGpx(gpxFile, be.getPoints());
				i++;
			}
		}
		System.out.println("Finished " + boundaryFile);

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File boundaryDir = new File(args[0]);
		List<File> boundaryFiles = new ArrayList<File>();
		System.out.println(boundaryDir.getName());
		if (boundaryDir.isFile() && boundaryDir.getName().endsWith(".bnd")) {
			boundaryFiles.add(boundaryDir);
		} else {
			File[] bFiles = boundaryDir.listFiles(new FileFilter() {

				public boolean accept(File pathname) {
					return pathname.getName().endsWith(".bnd");
				}
			});
			boundaryFiles.addAll(Arrays.asList(bFiles));
		}

		for (File boundaryFile : boundaryFiles) {
			BoundaryFile2Gpx converter = new BoundaryFile2Gpx(boundaryFile);
			converter.loadFile();
			converter.saveAsGpx();

			for (int adminlevel = 2; adminlevel < 12; adminlevel++) {
				converter.saveEmptyAreas("admin_level",
						String.valueOf(adminlevel));
			}
		}

	}

}

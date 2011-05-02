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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.me.parabola.util.GpxCreator;

public class BoundaryFile2Gpx {

	public static void saveAsGpx(File boundaryFile) {
		System.out.println("Start converting " + boundaryFile);

		try {
			List<Boundary> boundaries = BoundaryUtil.loadBoundaryFile(
					boundaryFile, null);
			System.out.println(boundaries.size() + " boundaries loaded");

			for (Boundary b : boundaries) {
				// get the admin_level tag
				String admLevel = b.getTags().get("admin_level");
				if (admLevel == null) {
					admLevel = "notset";
				}

				String bId = b.getTags().get("mkgmap:boundaryid");

				String gpxBasename = "gpx/" + boundaryFile.getName()
						+ "/admin_level=" + admLevel + "/"+ admLevel+"_"+ bId + "_";

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

		} catch (IOException exp) {
			System.err.println("Error while converting boundary file "+boundaryFile);
			System.err.println(exp);
			exp.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File boundaryDir = new File(args[0]);
		List<File> boundaryFiles = new ArrayList<File>();
		System.out.println(boundaryDir.isFile());
		System.out.println(boundaryDir.isDirectory());
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
			saveAsGpx(boundaryFile);
		}

	}

}

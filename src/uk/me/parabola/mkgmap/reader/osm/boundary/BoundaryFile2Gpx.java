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
import java.awt.geom.Path2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryFile2Gpx {
	private final String boundaryFileName;
	private final BoundaryQuadTree bqt; 
	public BoundaryFile2Gpx(String boundaryDirName, String boundaryFileName) {
		this.boundaryFileName = boundaryFileName;
		bqt = BoundaryUtil.loadQuadTree(boundaryDirName, boundaryFileName);
	}

	/**
	 * Create gpx files for areas which contain no information for a given
	 * admin level.  
	 * @param admLevel reasonable values are 2..11
	 */
	public void saveEmptyAreas(int admLevel) {
		Area tileArea = Java2DConverter.createBoundsArea(BoundaryUtil
				.getBbox(boundaryFileName));

		Area coveredArea = bqt.getCoveredArea(admLevel);
		tileArea.subtract(coveredArea);
		String levelId = "admin_level" ;
		if (admLevel < 12)
			levelId += admLevel;
		else
			levelId += "notset";
		if (tileArea.isEmpty()) {
			System.out.println(levelId + " is covered completely.");
		} else {
			String gpxBasename = "gpx/" + boundaryFileName
					+ "/uncovered/" + levelId + "/";
			

			List<List<Coord>> emptyPolys = Java2DConverter
					.areaToShapes(tileArea);
			Collections.reverse(emptyPolys);

			int i = 0;
			for (List<Coord> emptyPart : emptyPolys) {
				String attr = Way.clockwise(emptyPart) ? "o" : "i";
				GpxCreator.createGpx(gpxBasename + i + "_" + attr, emptyPart);
				i++;
			}
		}
	}

	/**
	 * create gpx files for all boundaries contained in one *.bnd file
	 */
	public void saveAsGpx() {
		System.out.println("Start converting " + boundaryFileName);

		Map<String, Tags> bTags = bqt.getTagsMap();
		Map<String, List<Area>> areas = bqt.getAreas();
		
		// verify data: remove boundary ids that have no related areas 
		Iterator<Entry<String, Tags>> tagIter = bTags.entrySet().iterator();
		while (tagIter.hasNext()) {
			Entry<String, Tags> entry = tagIter.next();
			List<Area> aList = areas.get(entry.getKey());
			if (aList == null || aList.isEmpty()){
				System.err.println("no area info for "+ entry.getKey());
				tagIter.remove();
				continue;
			}
		}
		
		for (int adminlevel = 2; adminlevel < 12; adminlevel++) {
			boolean found = false;
			for (Entry<String, Tags> entry: bTags.entrySet()){
				String admLevel = entry.getValue().get("admin_level");
				if (admLevel != null && admLevel.equals(String.valueOf(adminlevel))){
					found = true;
					break;
				}
			}
			if (found == false){
				System.out.println("No boundary with admin_level=" + adminlevel
						+ " found.");
				continue;
			}
			
			for (Entry<String, Tags> entry: bTags.entrySet()){
				// get the admin_level tag
				String admLevel = entry.getValue().get("admin_level");
				if (admLevel == null) {
					admLevel = "notset";
				}

				String bId = entry.getKey();

				String gpxBasename = "gpx/" + boundaryFileName
						+ "/covered/admin_level=" + admLevel + "/" + admLevel + "_" + bId
						+ "_";

				Path2D.Double path = new Path2D.Double();
				List<Area> aList = areas.get(bId);
				for (Area area : aList){
					BoundaryUtil.addToPath(path, area);
				}
				int i = 0;
				List<BoundaryElement> bElements = BoundaryUtil.splitToElements(new Area(path),bId);
				for (BoundaryElement be : bElements){
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
			saveEmptyAreas(adminlevel);
		}
		System.out.println("Finished " + boundaryFileName);

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String boundaryDirName = args[0];
		System.out.println(boundaryDirName);
		File boundaryDir = new File(boundaryDirName);
		List<String> boundaryFileNames;
		if (boundaryDir.isFile() && boundaryDir.getName().endsWith(".bnd")) {
			boundaryDirName = boundaryDir.getParent();
			if (boundaryDirName == null)
				boundaryDirName = ".";
			boundaryFileNames = new ArrayList<String>();
			boundaryFileNames.add(boundaryDir.getName());
		} else {
			boundaryFileNames = BoundaryUtil.getBoundaryDirContent(boundaryDirName);
		}

		for (String boundaryFileName : boundaryFileNames) {
			BoundaryFile2Gpx converter = new BoundaryFile2Gpx(boundaryDirName,boundaryFileName);
			converter.saveAsGpx();
		}

	}

}

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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.Java2DConverter;

/**
 * Compare two boundary files or two directories with boundary files.
 * Write differences as gpx files.
 *  
 * @author WanMil (initial version), rewritten for BoundaryQuadTree by GerdP
 *
 */
public class BoundaryDiff {
	private final String inputName1;
	private final String inputName2;

	public BoundaryDiff(String boundaryDirName1, String boundaryDirName2) {
		this.inputName1 = boundaryDirName1;
		this.inputName2 = boundaryDirName2;
	}

	/** 
	 * Return list of file names
	 * @param dirName a directory or a zip file containing *.bnd files,
	 * or a single *.bnd file
	 * @return
	 */
	private static List<String> getBoundsFiles(String dirName) {
		File dir = new File(dirName);
		System.out.println(dirName);
		if (dir.isFile() && dir.getName().endsWith(".bnd")) {
			List<String> boundaryFiles = new ArrayList<>();
			boundaryFiles.add(dir.getName());
			return boundaryFiles;
		}
		return BoundaryUtil.getBoundaryDirContent(dirName);
	}

	/**
	 * Compare all files in one list with the files in another list.
	 * Optionally restrict the comparison to boundaries with the
	 * given tag/value combination.
	 * @param tag should be admin_level
	 * @param value any value appropriate for the tag
	 */
	public void compare(String tag, String value) {
		List<String> b1 = getBoundsFiles(inputName1);
		List<String> b2 = getBoundsFiles(inputName2);

		if (b1.size() == 0 && b2.size() == 0)
			return;
		
		Collections.sort(b1);
		Collections.sort(b2);

		Queue<String> bounds1 = new LinkedList<>(b1);
		Queue<String> bounds2 = new LinkedList<>(b2);
		b1 = null;
		b2 = null;

		Area only1 = new Area();
		Area only2 = new Area();
		
		int bAll = bounds1.size() + bounds2.size();
		long tProgress = System.currentTimeMillis();

		while (bounds1.isEmpty() == false || bounds2.isEmpty() == false) {
			String f1 = bounds1.peek();
			String f2 = bounds2.peek();

			if (f1 == null) {
				only2.add(loadArea(inputName2, f2, tag, value));
				bounds2.poll();
			} else if (f2 == null) {
				only1.add(loadArea(inputName1, f1, tag, value));
				bounds1.poll();
			} else {
				int cmp = f1.compareTo(f2);
				if (cmp == 0) {
					Area a1 = loadArea(inputName1, f1, tag, value);
					Area a2 = loadArea(inputName2, f2, tag, value);
					if (a1.isEmpty() == false|| a2.isEmpty() == false){
						Area o1 = new Area(a1);
						o1.subtract(a2);
						if (o1.isEmpty() == false)
							only1.add(o1);
						Area o2 = new Area(a2);
						o2.subtract(a1);
						if (o2.isEmpty() == false)
							only2.add(o2);
					}
					bounds1.poll();
					bounds2.poll();
				} else if (cmp < 0) {
					only1.add(loadArea(inputName1, f1, tag, value));
					bounds1.poll();
				} else {
					only2.add(loadArea(inputName2, f2, tag, value));
					bounds2.poll();
				}
			}
			
			long tNow = System.currentTimeMillis();
			if (tNow - tProgress >= 10*1000L) {
				int bNow = bounds1.size()+ bounds2.size();
				System.out.println(tag+"="+value+": "+(bAll-bNow)+"/"+bAll+" files - "+(bAll-bNow)*100/bAll+"%");
				tProgress = tNow;
			}
		}

		saveArea(only1, "removed", tag, value);
		saveArea(only2, "new", tag, value);

	}

	/**
	 * Calculate the area that is covered by a given tag /value pair, e.g. admin_level=2 
	 * @param dirName the name of a directory or *.zip file containing *.bnd files, or a single *.bnd file
	 * @param fileName the name of the *.bnd file that should be read
	 * @param tag the tag key
	 * @param value the tag value
	 * @return a new Area (which might be empty) 
	 */
	private static Area loadArea(String dirName, String fileName, String tag, String value) {
		String dir = dirName;
		String bndFileName = fileName;
		if (dir.endsWith(".bnd")){
			File f = new File(dir);
			if (f.isFile()){
				dir = f.getParent();
				bndFileName = f.getName();
			}
			if (dir == null)
				dir = "."; // the local directory
		}
		BoundaryQuadTree bqt = BoundaryUtil.loadQuadTree(dir, bndFileName);
		if (tag.equals("admin_level"))
			return (bqt.getCoveredArea(Integer.valueOf(value)));
		Map<String, Tags> bTags = bqt.getTagsMap();
		Map<String, List<Area>> areas = bqt.getAreas();
		Path2D.Double path = new Path2D.Double();
		for (Entry<String, Tags> entry: bTags.entrySet()){
			if (value.equals(entry.getValue().get(tag))){
				List<Area> aList = areas.get(entry.getKey());
				for (Area area : aList){
					path.append(area, false);
				}
			}
		}
		Area a = new Area(path);
		return a;
	}

	/**
	 * Create gpx file(s) for a single area. 
	 * @param a the Area
	 * @param subDirName target sub-directory
	 * @param tagKey used to build the gpx file name
	 * @param tagValue used to build the gpx file name
	 */
	private static void saveArea(Area a, String subDirName, String tagKey, String tagValue) {

		String gpxBasename = "gpx/diff/" + subDirName + "/"
				+ tagKey + "=" + tagValue + "/";

		List<List<Coord>> singlePolys = Java2DConverter.areaToShapes(a);
		Collections.reverse(singlePolys);

		int i = 0;
		for (List<Coord> polyPart : singlePolys) {
			String attr = Way.clockwise(polyPart) ? "o" : "i";
			GpxCreator.createGpx(gpxBasename + i + "_" + attr, polyPart);
			i++;
		}
	}

	/**
	 * print usage info
	 */
	private static void printUsage(){
		System.err.println("Usage:");
		System.err
				.println("java -cp mkgmap.jar uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryDiff <boundsdir1> <boundsdir2> [<tag=value> [<tag=value>]]");
		System.err.println(" <boundsdir1> ");
		System.err
				.println(" <boundsdir2>: defines two directories or zip files containing boundsfiles to be compared ");
		System.err
		.println(" <tag=value>: defines a tag/value combination for which the diff is created");
		System.err
		.println(" sample:");
		System.err
		.println(" java -cp mkgmap.jar uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryDiff world_20120113.zip bounds admin_level=2");

		System.exit(-1);
	}

	public static void main(final String[] args) {
		if (args.length < 2) 
			printUsage();
		File f1 = new File(args[0]);
		if (f1.exists() == false){
			System.err.println(args[0] + " does not exist");
			printUsage();
		}
		File f2 = new File(args[1]);
		if (f2.exists() == false){
			System.err.println(args[1] + " does not exist");
			printUsage();
		}

		List<Entry<String,String>> tags = new ArrayList<>();
		
		if (args.length > 2) {
			for (int i = 2; i < args.length; i++) {
				final String[] parts = args[i].split(Pattern.quote("="));
				tags.add(new AbstractMap.SimpleImmutableEntry<>(
						parts[0], parts[1]));
			}
		} else {
			for (int adminlevel = 2; adminlevel <= 11; adminlevel++) {
			tags.add(new AbstractMap.SimpleImmutableEntry<>(
					"admin_level", String.valueOf(adminlevel)));
			}
		}
 		
			
		int processors = Runtime.getRuntime().availableProcessors();
		ExecutorService excSvc = Executors.newFixedThreadPool(processors);
		ExecutorCompletionService<String> executor = new ExecutorCompletionService<>(excSvc);

		
		for (final Entry<String, String> tag : tags) {
			executor.submit(new Runnable() {
				public void run() {
					BoundaryDiff bd = new BoundaryDiff(args[0],args[1]);
					bd.compare(tag.getKey(), tag.getValue());
				}
			}, tag.getKey() + "=" + tag.getValue());
		}
		
		for (int i = 0; i < tags.size(); i++) {
			try {
				String tag = executor.take().get();
				System.out.println(tag + " finished.");
			} catch (InterruptedException exp) {
				exp.printStackTrace();
			} catch (ExecutionException exp) {
				exp.printStackTrace();
			}
		}

		excSvc.shutdown();
	}

}

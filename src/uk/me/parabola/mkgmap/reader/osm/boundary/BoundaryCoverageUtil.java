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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryCoverageUtil {
	private final BoundaryQuadTree bqt;

	public BoundaryCoverageUtil(String boundaryDirName, String boundaryFileName) {
		bqt = BoundaryUtil.loadQuadTree(boundaryDirName, boundaryFileName);
	}

	public Area getCoveredArea(int admLevel) {
		return bqt.getCoveredArea(admLevel);
	}

	private static void saveArea(String attribute, Integer level, Area covered) {
		String gpxBasename = "gpx/summary/" + attribute + "/admin_level="
				+ level;

		List<List<Coord>> coveredPolys = Java2DConverter.areaToShapes(covered);
		Collections.reverse(coveredPolys);
		int i = 0;
		for (List<Coord> coveredPart : coveredPolys) {
			Way w = new Way(0, coveredPart);
			String attr = w.clockwise() ? "o" : "i";
			GpxCreator.createGpx(gpxBasename + "/" + i + "_" + attr, coveredPart);
			i++;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		int processors = Runtime.getRuntime().availableProcessors();
		ExecutorService excSvc = Executors.newFixedThreadPool(processors);
		ExecutorCompletionService<Area> executor = new ExecutorCompletionService<Area>(
				excSvc);
		String workDirName = args[0];
		System.out.println(workDirName);
		File boundaryDir = new File(workDirName);
		final Set<String> boundsFileNames = new HashSet<String>();
		if (boundaryDir.isFile() && boundaryDir.getName().endsWith(".bnd")) {
			workDirName = boundaryDir.getParent();
			if (workDirName == null)
				workDirName = ".";
			boundsFileNames.add(boundaryDir.getName());
		} else {
			boundsFileNames.addAll(BoundaryUtil
					.getBoundaryDirContent(workDirName));
		}

		int minLat = Integer.MAX_VALUE;
		int maxLat = Integer.MIN_VALUE;
		int minLon = Integer.MAX_VALUE;
		int maxLon = Integer.MIN_VALUE;
		for (String fileName : boundsFileNames) {
			String[] parts = fileName.substring("bounds_".length(),
					fileName.length() - 4).split("_");
			int lat = Integer.valueOf(parts[0]);
			int lon = Integer.valueOf(parts[1]);
			if (lat < minLat)
				minLat = lat;
			if (lat > maxLat)
				maxLat = lat;
			if (lon < minLon)
				minLon = lon;
			if (lon > maxLon)
				maxLon = lon;
		}
		System.out.format("Covered area: (%d,%d)-(%d,%d)\n", minLat, minLon,
				maxLat, maxLon);
		int maxSteps = 2;

		final String boundaryDirName = workDirName;
		for (int adminlevel = 2; adminlevel < 12; adminlevel++) {
			final Set<String> boundaryFileNames = Collections.synchronizedSet(new HashSet<String>(boundsFileNames));
			final int adminLevel = adminlevel;
			final Queue<Future<Area>> areas = new LinkedBlockingQueue<Future<Area>>();
			for (int lat = minLat; lat <= maxLat; lat += maxSteps
					* BoundaryUtil.RASTER) {
				for (int lon = minLon; lon <= maxLon; lon += maxSteps
						* BoundaryUtil.RASTER) {
					for (int latStep = 0; latStep < maxSteps
							&& lat + latStep * BoundaryUtil.RASTER <= maxLat; latStep++) {
						for (int lonStep = 0; lonStep < maxSteps
								&& lon + lonStep * BoundaryUtil.RASTER <= maxLon; lonStep++) {
							final int fLat = lat + latStep
									* BoundaryUtil.RASTER;
							final int fLon = lon + lonStep
									* BoundaryUtil.RASTER;

							areas.add(executor.submit(new Callable<Area>() {
								public Area call() {
									String filename = "bounds_" + fLat + "_"
											+ fLon + ".bnd";
									if (boundaryFileNames.contains(filename) == false) {
										return new Area();
									}
									BoundaryCoverageUtil converter = new BoundaryCoverageUtil(
											boundaryDirName, filename);
									boundaryFileNames.remove(filename);
									System.out.format("%5d bounds files remaining\n", boundaryFileNames.size());
									return converter.getCoveredArea(adminLevel);
								}
							}));
						}
					}
				}
			}

			final AtomicInteger mergeSteps = new AtomicInteger();
			while (areas.size() > 1) {
				final List<Future<Area>> toMerge = new ArrayList<Future<Area>>();
				for (int i = 0; i < maxSteps * 2 && areas.isEmpty() == false; i++) {
					toMerge.add(areas.poll());
				}
				mergeSteps.incrementAndGet();
				areas.add(executor.submit(new Callable<Area>() {
					public Area call() {
						Area a = new Area();
						ListIterator<Future<Area>> mergeAreas = toMerge
								.listIterator();
						while (mergeAreas.hasNext()) {
							try {
								a.add(mergeAreas.next().get());
							} catch (InterruptedException exp) {
								System.err.println(exp);
							} catch (ExecutionException exp) {
								System.err.println(exp);
							}
							mergeAreas.remove();
						}
						System.out.format("%5d merges remaining\n",mergeSteps.decrementAndGet());
						return a;
					}
				}));
			}
			try {
				Area finalArea = areas.poll().get();
				System.out.println("Joining finished. Saving results.");
				saveArea("covered", adminlevel, finalArea);
			} catch (Exception exp) {
				System.err.println(exp);
			}
			// }
		}
		excSvc.shutdown();
	}

}

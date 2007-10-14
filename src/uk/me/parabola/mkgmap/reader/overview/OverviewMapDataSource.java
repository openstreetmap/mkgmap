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
 * Create date: 25-Sep-2007
 */
package uk.me.parabola.mkgmap.reader.overview;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.plugin.MapperBasedMapDataSource;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Class for creating an overview map.  Nothing is actually read in from a file,
 * we just save some detail from the other img files that are going into the
 * map set.
 * 
 * @author Steve Ratcliffe
 */
public class OverviewMapDataSource extends MapperBasedMapDataSource
		implements LoadableMapDataSource, OverviewMap
{
	// We keep all non-duplicated copyright messages from the component maps.
	private final Set<String> copyrights = new HashSet<String>();

	// We need the exact bounds that the map covers, so keep our own copy
	private int minLat = Integer.MAX_VALUE;
	private int minLong = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLong = Integer.MIN_VALUE;

	/**
	 * This is a fake source of data and is not read from a file, so always
	 * return false here.
	 *
	 * @param name The filename, ignored.
	 * @return Always false.
	 */
	public boolean isFileSupported(String name) {
		return false;
	}

	/*
	 * This is never called as isFileSupported always returns false.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		throw new FileNotFoundException("This is not supposed to be called");
	}

	public LevelInfo[] mapLevels() {
		// We use higher zoom levels for the overview map.
		// Lets hope the rest of the code will cope!
		// TODO: calculate this based on what we see in the maps passed in.
		return new LevelInfo[]{
				new LevelInfo(6, 14),
		};
	}

	/**
	 * All the copyright messages that were found in the input files are
	 * returned here.
	 *
	 * @return An array of copyright messages.
	 */
	public String[] copyrightMessages() {
		return copyrights.toArray(new String[copyrights.size()]);
	}

	/**
	 * Get the area covered by this overview map.  It will be the bounding box
	 * of all the maps in the map set.
	 *
	 * @return The bounding box of the overview map.
	 */
	public Area getBounds() {
		return new Area(minLat, minLong, maxLat, maxLong);
	}

	/**
	 * Each map in the map set will have its data passed in here.  We extract
	 * things like bounding box and some key features to include on this map.
	 *
	 * We also add a polygon to the map that covers the area of this map
	 * and named after it.
	 *
	 * @param src One of the individual maps in the set.
	 * @param props Current options that are in force.
	 */
	public void addMapDataSource(LoadableMapDataSource src, Properties props) {
		// Save all the copyright messages, discarding duplicates.
		copyrights.addAll(Arrays.asList(src.copyrightMessages()));

		// Add to the bounds.
		Area a = src.getBounds();
		if (a.getMinLat() < minLat)
			minLat = a.getMinLat();
		if (a.getMinLong() < minLong)
			minLong = a.getMinLong();
		if (a.getMaxLat() > maxLat)
			maxLat = a.getMaxLat();
		if (a.getMaxLong() > maxLong)
			maxLong = a.getMaxLong();

		// Add a background polygon for this map.
		Coord start, co;
		List<Coord> points = new ArrayList<Coord>();
		start = new Coord(a.getMinLat(), a.getMinLong());
		points.add(start);
		co = new Coord(a.getMinLat(), a.getMaxLong());
		points.add(co);
		co = new Coord(a.getMaxLat(), a.getMaxLong());
		points.add(co);
		co = new Coord(a.getMaxLat(), a.getMinLong());
		points.add(co);
		points.add(start);

		MapShape bg = new MapShape();
		bg.setType(0x4a);
		bg.setPoints(points);
		bg.setMinResolution(10);
		bg.setName(props.getProperty("mapname"));

		mapper.addShape(bg);

		// Save whatever points, lines and polygons that we want.
		processPoints(src.getPoints());
		processLines(src.getLines());
		processShapes(src.getShapes());
	}

	private void processPoints(List<MapPoint> points) {
		// ignore all for present.
	}

	private void processLines(List<MapLine> lines) {
		for (MapLine l : lines) {
			int type = l.getType();
			if (type <= 2)
				mapper.addLine(l);
		}
	}

	private void processShapes(List<MapShape> shapes) {
	}
}

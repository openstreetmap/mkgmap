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
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

/**
 * Class for creating an overview map.  Nothing is actually read in from a file,
 * we just save some detail from the other img files that are going into the
 * map set.
 * 
 * @author Steve Ratcliffe
 */
public class OverviewMapDataSource extends MapperBasedMapDataSource
		implements OverviewMap
{
	// We keep all non-duplicated copyright messages from the component maps.
	private final Set<String> copyrights = new HashSet<String>();

	// We need the exact bounds that the map covers, so keep our own copy
	private int minLat = Integer.MAX_VALUE;
	private int minLong = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLong = Integer.MIN_VALUE;

	// TODO need to change this.
	private final int topLevel = 4;
	private final int topBits = 14;

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
		// We use one level of zoom for the overview map and it has a level
		// that is greater than that of the maps that go to make it up.
		// (An extra invisible level will be added as always).
		LevelInfo info = new LevelInfo(topLevel + 2, topBits - 1);

		LevelInfo[] levels = new LevelInfo[1];
		levels[0] = info;

		return levels;
	}

	/**
	 * Add a copyright string to the map.
	 *
	 * @param cw The string to add.
	 */
	public void addCopyright(String cw) {
		copyrights.add(cw);
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
	 * Add the given point to the total bounds for the map.
	 *
	 * @param p The coordinates of the point to add.  The type here will change to
	 * Node.
	 */
	public void addToBounds(Coord p) {
		mapper.addToBounds(p);
	}

	/**
	 * Add a point to the map.
	 *
	 * @param point The point to add.
	 */
	public void addPoint(MapPoint point) {
		mapper.addPoint(point);
	}

	/**
	 * Add a line to the map.
	 *
	 * @param line The line information.
	 */
	public void addLine(MapLine line) {
		mapper.addLine(line);
	}

	/**
	 * Add the given shape (polygon) to the map.  A shape is very similar to a line
	 * but they are separate because they need to be put in different sections in
	 * the output map.
	 *
	 * @param shape The polygon to add.
	 */
	public void addShape(MapShape shape) {
		mapper.addShape(shape);
	}
}

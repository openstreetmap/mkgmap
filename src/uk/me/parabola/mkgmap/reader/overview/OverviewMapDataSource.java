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

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.net.GeneralRouteRestriction;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.combiners.OverviewMap;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;

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
	private static final Logger log = Logger.getLogger(OverviewMapDataSource.class);
	
	private final List<String> copyrights = new ArrayList<String>();
	LevelInfo[] levels = null;	
	
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
		return levels;
	}
	
	public LevelInfo[] overviewMapLevels() {
		return mapLevels();
	}
	
	public void setMapLevels (LevelInfo[] mapLevels) {
		if (levels == null){
			levels = mapLevels;
		} else {
			boolean ok = true;
			if (levels.length != mapLevels.length)
				ok = false;
			else {
				for (int i = 0; i < levels.length; i++){
					if (levels[i].compareTo(mapLevels[i]) != 0){
						ok = false;
					}
				}
			}
			if (!ok)
				log.error("invalid attempt to change map levels" );
		}
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

	public void addRoad(MapRoad road) {
		addLine(road);
	}

	public int addRestriction(GeneralRouteRestriction grr) {
		log.error("This is not supposed to be called");
		return 0;
	}

	public void addThroughRoute(int junctionNodeId, long roadIdA, long roadIdB) {
		log.error("This is not supposed to be called");
	}
}

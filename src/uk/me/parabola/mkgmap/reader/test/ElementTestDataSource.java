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
 * Create date: 02-Sep-2007
 */
package uk.me.parabola.mkgmap.reader.test;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * This is a map data source that just generates maps without reference to
 * any external data.
 * 
 * @author Steve Ratcliffe
 */
public class ElementTestDataSource implements LoadableMapDataSource {
	private final MapDetails mapper = new MapDetails();

	/**
	 * 'Filenames' that are supported begin with TEST:
	 * @param name The name to check.
	 * @return True if a recognised test name beginning with TEST:
	 */
	public boolean fileSupported(String name) {
		if (name.startsWith("TEST:"))
			return true;
		return false;
	}

	public void load(String name) throws FileNotFoundException, FormatException {
		if ("TEST:ALL-ELEMENTS".equals(name)) {
			AllElements all = new AllElements();
			all.load(mapper);
		} else {
			throw new FileNotFoundException("Invalid test file name");
		}
	}

	public LevelInfo[] mapLevels() {
		return new LevelInfo[] {
				//new LevelInfo(1, 20),
				new LevelInfo(0, 24),
		};
	}

	public String copyrightMessage() {
		return "test data";
	}

	public List<Overview> getOverviews() {
		return mapper.getOverviews();
	}

	public Area getBounds() {
		return mapper.getBounds();
	}

	public List<MapPoint> getPoints() {
		return mapper.getPoints();
	}

	public List<MapLine> getLines() {
		return mapper.getLines();
	}

	public List<MapShape> getShapes() {
		return mapper.getShapes();
	}
}

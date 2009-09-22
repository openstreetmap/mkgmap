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
package uk.me.parabola.mkgmap.reader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.Overview;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.RoadNetwork;
import uk.me.parabola.mkgmap.reader.dem.DEM;
import uk.me.parabola.util.Configurable;
import uk.me.parabola.util.EnhancedProperties;

/**
 * A convenient base class for all map data that is based on the MapDetails
 * class (which is all of them so far).
 *
 * @author Steve Ratcliffe
 */
public abstract class MapperBasedMapDataSource implements MapDataSource, Configurable {
	protected final MapDetails mapper = new MapDetails();
	private EnhancedProperties configProps;

	/**
	 * Get the area that this map covers. Delegates to the map collector.
	 *
	 * @return The area the map covers.
	 */
	public Area getBounds() {
		return mapper.getBounds();
	}

	/**
	 * Get the list of lines that need to be rendered to the map. Delegates to
	 * the map collector.
	 *
	 * @return A list of {@link MapLine} objects.
	 */
	public List<MapLine> getLines() {
		return mapper.getLines();
	}

	public List<MapShape> getShapes() {
		return mapper.getShapes();
	}

	public RoadNetwork getRoadNetwork() {
		return mapper.getRoadNetwork();
	}

	/**
	 * Get a list of every feature that is used in the map.  As features are
	 * created a list is kept of each separate feature that is used.  This
	 * goes into the .img file and is important for points and polygons although
	 * it doesn't seem to matter if lines are represented or not on my Legend Cx
	 * anyway.
	 *
	 * @return A list of all the types of point, polygon and polyline that are
	 * used in the map.
	 */
	public List<Overview> getOverviews() {
		return mapper.getOverviews();
	}

	public List<MapPoint> getPoints() {
		return mapper.getPoints();
	}

	/**
	 * Open a file and apply filters necessary to reading it such as decompression.
	 *
	 * @param name The file to open.
	 * @return A stream that will read the file, positioned at the beginning.
	 * @throws FileNotFoundException If the file cannot be opened for any reason.
	 */
	protected InputStream openFile(String name) throws FileNotFoundException {
		InputStream is = new FileInputStream(name);
		if (name.endsWith(".gz")) {
			try {
				is = new GZIPInputStream(is);
			} catch (IOException e) {
				throw new FileNotFoundException( "Could not read as compressed file");
			}
		}
		return is;
	}

	public void config(EnhancedProperties props) {
		configProps = props;
	}

	protected EnhancedProperties getConfig() {
		return configProps;
	}

	public MapDetails getMapper() {
		return mapper;
	}

	/**
	 * We add the background polygons if the map is not transparent.
	 */
	protected void addBackground() {
		if (!getConfig().getProperty("transparent", false)) {
			// Make a list of points to trace out the background area.
			List<Coord> coords = new ArrayList<Coord>();
			Area bounds = mapper.getBounds();
			Coord start = new Coord(bounds.getMinLat(), bounds.getMinLong());
			coords.add(start);
			Coord co = new Coord(bounds.getMinLat(), bounds.getMaxLong());
			coords.add(co);
			co = new Coord(bounds.getMaxLat(), bounds.getMaxLong());
			coords.add(co);
			co = new Coord(bounds.getMaxLat(), bounds.getMinLong());
			coords.add(co);
			//coords.add(start);

			// Now add the background area
			MapShape background = new MapShape();
			background.setType(0x4b); // background type
			background.setMinResolution(0); // On all levels
			background.setPoints(coords);

			// Note we add directly to the shapes list, we do not add to
			// the overview section.
			mapper.addShape(background);
		}
		if (getConfig().getProperty("contours", false)) {		    
		    DEM.createContours((LoadableMapDataSource) this, getConfig());
		}
	}

	public void addBoundaryLine(Area area, int type) {
		List<Coord> coords = new ArrayList<Coord>();
		coords.add(new Coord(area.getMinLat(), area.getMinLong()));
		coords.add(new Coord(area.getMinLat(), area.getMaxLong()));
		coords.add(new Coord(area.getMaxLat(), area.getMaxLong()));
		coords.add(new Coord(area.getMaxLat(), area.getMinLong()));
		coords.add(new Coord(area.getMinLat() + 1, area.getMinLong()));
		MapLine boundary = new MapLine();
		boundary.setType(type);
		boundary.setMinResolution(0); // On all levels
		boundary.setPoints(coords);
		mapper.addLine(boundary);
	}
}

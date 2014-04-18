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

import java.util.ArrayList;
import java.util.List;

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

	public void config(EnhancedProperties props) {
		configProps = props;
		mapper.config(props);
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
		addBackground(false);
	}

	protected void addBackground(boolean mapHasPolygon4B) {
		if (!mapHasPolygon4B && !getConfig().getProperty("transparent", false)) {

			MapShape background = new MapShape();
			background.setPoints(mapper.getBounds().toCoords());
			background.setType(0x4b); // background type
			background.setMinResolution(0); // On all levels

			mapper.addShape(background);
		}
		if (getConfig().getProperty("contours", false)) {		    
		    DEM.createContours((LoadableMapDataSource) this, getConfig());
		}
	}

	public void addBoundaryLine(Area area, int type, String name) {
		List<Coord> coords = new ArrayList<Coord>();
		coords.add(new Coord(area.getMinLat(), area.getMinLong()));
		coords.add(new Coord(area.getMinLat(), area.getMaxLong()));
		coords.add(new Coord(area.getMaxLat(), area.getMaxLong()));
		coords.add(new Coord(area.getMaxLat(), area.getMinLong()));
		coords.add(new Coord(area.getMinLat() + 1, area.getMinLong()));
		MapLine boundary = new MapLine();
		boundary.setType(type);
		if(name != null)
			boundary.setName(name);
		boundary.setMinResolution(0); // On all levels
		boundary.setPoints(coords);
		mapper.addLine(boundary);
	}
}

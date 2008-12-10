/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 13-Jul-2008
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.net.RoadDef;

/**
 * Used to represent a road.  A road is a special kind of line in that
 * it can be used to route down and can have addresses etc.
 *
 * A road has several coordinates, and some of those coordinates can be
 * routing nodes.
 *
 * A lot of the information is kept in a {@link RoadDef} this is done
 * because it needs to be shared between all sections and all levels
 * of the same road.
 *  
 * @author Steve Ratcliffe
 */
public class MapRoad extends MapLine {

	private final long roadId;
	private final RoadDef roadDef;

	public MapRoad(long roadId, MapLine line) {
		super(line);
		setPoints(line.getPoints());
		this.roadId = roadId;
		this.roadDef = new RoadDef(roadId, getName());
	}

	MapRoad(MapRoad r) {
		super(r);
		this.roadId = r.roadId;
		this.roadDef = r.roadDef;
	}

	public MapElement copy() {
		return new MapRoad(this);
	}

	public long getRoadId() {
		return roadId;
	}

	public void setRoadClass(int roadClass) {
		this.roadDef.setRoadClass(roadClass);
	}

	public void setSpeed(int speed) {
		this.roadDef.setSpeed(speed);
	}

	public void setDirIndicator(boolean dir) {
		this.roadDef.setDirIndicator(dir);
	}

	public void setOneway(boolean oneway) {
		this.roadDef.setOneway(oneway);
	}

	public void setToll(boolean toll) {
		this.roadDef.setToll(toll);
	}

	// XXX: currently passing PolishMapSource-internal format
	public void setAccess(boolean[] access) {
		this.roadDef.setAccess(access);
	}

	public void setStartsWithNode(boolean s) {
		this.roadDef.setStartsWithNode(s);
	}

	public void setInternalNodes(boolean s) {
		this.roadDef.setInternalNodes(s);
	}

	public void setNumNodes(int n) {
		this.roadDef.setNumNodes(n);
	}

	public RoadDef getRoadDef() {
		return roadDef;
	}
}

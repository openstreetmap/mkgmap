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
 * @author Steve Ratcliffe
 */
public class MapRoad extends MapLine {
	private long roadId;
	private byte roadClass;
	private byte speed;// So top code can link objects from here
	private final RoadDef roadDef;

	public MapRoad(long roadId, MapLine line) {
		super(line);
		setPoints(line.getPoints());
		this.roadId = roadId;
		this.roadDef = new RoadDef();
	}

	public long getRoadId() {
		return roadId;
	}

	public void setRoadClass(int roadClass) {
		this.roadClass = (byte) roadClass;
	}

	public void setSpeed(int speed) {
		this.speed = (byte) speed;
	}

	public RoadDef getRoadDef() {
		return roadDef;
	}

	public byte getRoadClass() {
		return roadClass;
	}
}

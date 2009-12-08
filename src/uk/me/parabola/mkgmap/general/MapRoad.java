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

import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.Zip;
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

	private final RoadDef roadDef;

	public MapRoad(long id, MapLine line) {
		super(line);
		setPoints(line.getPoints());
		this.roadDef = new RoadDef(id, getName());
	}

	private MapRoad(MapRoad r) {
		super(r);
		this.roadDef = r.roadDef;
	}

	public MapRoad copy() {
		return new MapRoad(this);
	}

	public boolean isRoad() {
		return true;
	}

	public void setRoadClass(int roadClass) {
		this.roadDef.setRoadClass(roadClass);
	}

	public void setSpeed(int speed) {
		this.roadDef.setSpeed(speed);
	}

	public void setOneway() {
		this.roadDef.setOneway();
	}

	public void setToll() {
		this.roadDef.setToll();
	}

	public void paved(boolean p) {
		this.roadDef.paved(p);
	}

	public void ferry(boolean f) {
		this.roadDef.ferry(f);
	}

	public void showOSMBrowseURL() {
		this.roadDef.showOSMBrowseURL();
	}

	public void setSynthesised(boolean s) {
		this.roadDef.setSynthesised(s);
	}

	// XXX: currently passing PolishMapSource-internal format
	public void setAccess(boolean[] access) {
		this.roadDef.setAccess(access);
	}

	public void setNoThroughRouting() {
		this.roadDef.setNoThroughRouting();
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

	public void setRoadCity(City c) {
		this.roadDef.setCity(c);
	}

	public void setRoadZip(Zip z) {
		this.roadDef.setZip(z);
	}

	public void setRoundabout(boolean r) {
		this.roadDef.setRoundabout(r);
	}

	public void doFlareCheck(boolean fc) {
		this.roadDef.doFlareCheck(fc);
	}

	public void doDeadEndCheck(boolean dec) {
		this.roadDef.doDeadEndCheck(dec);
	}

	public void setLinkRoad(boolean lr) {
		this.roadDef.setLinkRoad(lr);
	}
}

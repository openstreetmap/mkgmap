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

import java.util.List;

import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.Zip;
import uk.me.parabola.imgfmt.app.net.Numbers;
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
	private boolean segmentsFollowing;
	
	public MapRoad(long id, MapLine line) {
		super(line);
		setPoints(line.getPoints());
		roadDef = new RoadDef(id, getName());
	}

	private MapRoad(MapRoad r) {
		super(r);
		roadDef = r.roadDef;
		segmentsFollowing = r.segmentsFollowing;
	}

	public MapRoad copy() {
		return new MapRoad(this);
	}

	public boolean isRoad() {
		return true;
	}

	public void setRoadClass(int roadClass) {
		roadDef.setRoadClass(roadClass);
	}

	public void setSpeed(int speed) {
		roadDef.setSpeed(speed);
	}

	public void setOneway() {
		roadDef.setOneway();
	}

	public void setToll() {
		roadDef.setToll();
	}

	public void paved(boolean p) {
		roadDef.paved(p);
	}

	public void ferry(boolean f) {
		roadDef.ferry(f);
	}

	public void setSynthesised(boolean s) {
		roadDef.setSynthesised(s);
	}

	public void setAccess(byte access) {
		roadDef.setAccess(access);
	}

	public void setCarpoolLane() {
		roadDef.setCarpoolLane();
	}
	
	public void setNoThroughRouting() {
		roadDef.setNoThroughRouting();
	}

	public void setStartsWithNode(boolean s) {
		roadDef.setStartsWithNode(s);
	}

	public void setInternalNodes(boolean s) {
		roadDef.setInternalNodes(s);
	}

	public void setNumNodes(int n) {
		roadDef.setNumNodes(n);
	}

	public void setNumbers(List<Numbers> numbers) {
		roadDef.setNumbersList(numbers);
	}

	public RoadDef getRoadDef() {
		return roadDef;
	}

	public void setRoadCity(City c) {
		roadDef.setCity(c);
	}

	public void setRoadZip(Zip z) {
		roadDef.setZip(z);
	}

	public void setRoundabout(boolean r) {
		roadDef.setRoundabout(r);
	}

	public void doFlareCheck(boolean fc) {
		roadDef.doFlareCheck(fc);
	}

	public void setLinkRoad(boolean lr) {
		roadDef.setLinkRoad(lr);
	}

	public boolean hasSegmentsFollowing() {
		return segmentsFollowing;
	}

	public void setSegmentsFollowing(boolean segmentsFollowing) {
		this.segmentsFollowing = segmentsFollowing;
	}

}

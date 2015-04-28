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
	private boolean skipHousenumberProcessing;
	private final int roadId;
	
	public MapRoad(int roadId, long OSMid, MapLine line) {
		super(line);
		this.roadId = roadId;
		setPoints(line.getPoints());
		roadDef = new RoadDef(OSMid, getName());
	}

	private MapRoad(MapRoad r) {
		super(r);
		roadId = r.roadId;
		roadDef = r.roadDef;
		segmentsFollowing = r.segmentsFollowing;
	}

	/**
	 * @return value that can be used to sort MapRoad instances
	 */
	public int getRoadId(){
		return roadId;
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

	public void setNumbers(List<Numbers> numbers) {
		roadDef.setNumbersList(numbers);
	}
	public List<Numbers> getNumbers() {
		return roadDef.getNumbersList();
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

	public boolean isSkipHousenumberProcessing() {
		return skipHousenumberProcessing;
	}

	public void setSkipHousenumberProcessing(boolean skipHousenumberProcessing) {
		this.skipHousenumberProcessing = skipHousenumberProcessing;
	}

	public boolean skipAddToNOD() {
		return roadDef.skipAddToNOD();
	}

	public void skipAddToNOD(boolean skip) {
		roadDef.skipAddToNOD(skip);
	}

	public boolean addLabel(String label){
		if (label == null)
			return false;
		for (int i = 0; i < labels.length; i++){
			if (labels[i] == null){
				labels[i] = label;
				return true;
			}
			if (labels[i].equals(label))
				return false;
		}
		return false;
	}
	
	public String toString(){
		return "id="+this.getRoadDef().getId() + ", " + this.getName();
	}
}

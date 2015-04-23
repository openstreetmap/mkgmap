/*
 * Copyright (C) 2013.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package uk.me.parabola.mkgmap.osmstyle.housenumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Locatable;

/**
 * Stores the matching data between a housenumber and its road.
 * @author WanMil
 */
public class HousenumberMatch extends HousenumberElem implements Locatable {
	private MapRoad road;
	private HousenumberRoad housenumberRoad;
	
	private double distance = Double.POSITIVE_INFINITY;
	private int segment = -1;
	private boolean left;
	
	private double segmentFrac;
	
	private boolean ignored;
	private boolean isDuplicate;
	private boolean interpolated;
	private int moved;
	// distance in m between closest point on road and the point that is found in the address search
	private double searchDist = Double.NaN;
	private boolean isFarDuplicate;
	private HousenumberGroup group;
	private List<MapRoad> alternativeRoads;
	private int intervalInfoRefs; // counter
	
	public HousenumberMatch(HousenumberElem he) {
		super(he);
	}

	public MapRoad getRoad() {
		return road;
	}

	public void setRoad(MapRoad road) {
		this.road = road;
	}

	/**
	 * Retrieves the distance to the road.
	 * @return distance in m
	 */
	public double getDistance() {
		return distance;
	}

	/**
	 * Sets the distance to the road
	 * @param distance distance in m
	 */
	public void setDistance(double distance) {
		this.distance = distance;
	}

	/**
	 * Retrieves the segment number the house number belongs to.
	 * @return the segment number
	 */
	public int getSegment() {
		return segment;
	}

	/**
	 * Sets the segment number the house number belongs to.
	 * @param segment the segment number
	 */
	public void setSegment(int segment) {
		this.segment = segment;
	}

	public boolean isLeft() {
		return left;
	}

	/**
	 * Sets if the house number is on the left or right side of the street.
	 * @param left {@code true} left side; {@code false} right side
	 */
	public void setLeft(boolean left) {
		this.left = left;
	}

	/**
	 * Retrieve the relative position of this house number within the segement
	 * of the related road.
	 * @return the relative position within the roads segment
	 */
	public double getSegmentFrac() {
		return segmentFrac;
	}

	/**
	 * Sets the relative position of this house number within its segment
	 * of the related road.
	 * @param segmentFrac relative position within the segment
	 */
	public void setSegmentFrac(double segmentFrac) {
		this.segmentFrac = segmentFrac;
	}

	public boolean hasAlternativeRoad() {
		return alternativeRoads != null && alternativeRoads.isEmpty() == false;
	}

	public boolean isIgnored() {
		return ignored;
	}

	public void setIgnored(boolean ignored) {
		this.ignored = ignored;
	}

	public boolean isDuplicate() {
		return isDuplicate;
	}

	public void setDuplicate(boolean isDuplicate) {
		this.isDuplicate = isDuplicate;
	}

	public boolean isInterpolated() {
		return interpolated;
	}

	public void setInterpolated(boolean interpolated) {
		this.interpolated = interpolated;
	}

	public int getMoved() {
		return moved;
	}

	public void incMoved() {
		this.moved++;
	}

	public double getSearchDist() {
		return searchDist;
	}

	public void setSearchDist(double searchDist) {
		this.searchDist = searchDist;
	}

	public String toString() {
		String s1 = String.valueOf(getHousenumber());
		if (getSign().length() > 2 + s1.length())
			return s1 + "("+segment+")";
		return getSign() + "("+segment+")";
	}

	public void setFarDuplicate(boolean b) {
		this.isFarDuplicate = b;
		
	}

	public boolean isFarDuplicate() {
		return isFarDuplicate;
	}

	/**
	 * @return either an existing point on the road
	 * or the calculated perpendicular. In the latter case
	 * the highway count is zero.
	 *   
	 */
	public Coord getClosestPointOnRoad(){
		if (segmentFrac <= 0)
			return getRoad().getPoints().get(segment);
		if (segmentFrac >= 1)
			return getRoad().getPoints().get(segment+1);
		Coord c1 = getRoad().getPoints().get(segment);
		Coord c2 = getRoad().getPoints().get(segment+1);
		return c1.makeBetweenPoint(c2, segmentFrac);
	}

	/**
	 * @param other a different house on the same road
	 * @return  the distance in m between the perpendiculars on the road
	 * of two houses.
	 */
	public double getDistOnRoad(HousenumberMatch other) {
		if (getRoad() != other.getRoad()){
			assert false : "cannot compute distance on road for different roads"; 
		}
		List<Coord> points = getRoad().getPoints();
		HousenumberMatch house1 = this;
		HousenumberMatch house2 = other;
		if (house1.segment > house2.segment || house1.segment == house2.segment && house1.segmentFrac > house2.segmentFrac){
			house1 = other;
			house2 = this;
		}
		int s1 = house1.segment;
		int s2 = house2.segment;
		double distOnRoad = 0;
		while (s1 < s2){
			double segLen = points.get(s1).distance(points.get(s1 + 1));
			if (s1 == house1.getSegment() && house1.getSegmentFrac() > 0){
				// rest of first segment
				distOnRoad += Math.max(0, 1-house1.getSegmentFrac()) * segLen;
			} else
				distOnRoad += segLen;
			s1++;
		}
		double segLen = points.get(s1).distance(points.get(s1 + 1));
		if (house2.getSegmentFrac() > 0)
			distOnRoad += Math.min(1, house2.getSegmentFrac()) * segLen;
		if (house1.getSegmentFrac() > 0 && s1 == house1.segment)
			distOnRoad -= Math.min(1, house1.getSegmentFrac()) * segLen;
		return distOnRoad;
	}

	public HousenumberRoad getHousenumberRoad() {
		return housenumberRoad;
	}

	public void setHousenumberRoad(HousenumberRoad housenumberRoad) {
		this.housenumberRoad = housenumberRoad;
	}
	
	public void setGroup(HousenumberGroup housenumberBlock) {
		this.group = housenumberBlock;
	}

	public HousenumberGroup getGroup() {
		return group;
	}

	public void addAlternativeRoad(MapRoad road2) {
		if (alternativeRoads == null){
			alternativeRoads = new ArrayList<>();
		}
		alternativeRoads.add(road2);
	}
	public List<MapRoad> getAlternativeRoads() {
		if (alternativeRoads == null)
			return Collections.emptyList();
		return alternativeRoads;
	}
	public void forgetAlternativeRoads(){
		alternativeRoads = null;
	}

	public int getIntervalInfoRefs() {
		return intervalInfoRefs;
	}

	public void incIntervalInfoRefs() {
		intervalInfoRefs++;
	}

	public void decIntervalInfoRefs() {
		if (intervalInfoRefs > 0)
			--intervalInfoRefs;
	}

	public boolean isDirectlyConnected(HousenumberMatch other){
		if (getElement() instanceof Way && other.getElement() instanceof Way){
			List<Coord> s1 = ((Way) getElement()).getPoints();
			List<Coord> s2 = ((Way) other.getElement()).getPoints();
			for (int i = 0; i+1 < s1.size(); i++){
			    Coord co = s1.get(i);
			    co.setPartOfShape2(false);
			}
			for (int i = 0; i+1 < s2.size(); i++){
			    Coord co = s2.get(i);
			    co.setPartOfShape2(true);
			}
			for (int i = 0; i+1 < s1.size(); i++){
			    Coord co = s1.get(i);
			    if (co.isPartOfShape2())
			    	return true;
			}
		}
		return false;
	}


	public void calcRoadSide(){
		if (getRoad() == null)
			return;
		Coord c1 = getRoad().getPoints().get(getSegment());
		Coord c2 = getRoad().getPoints().get(getSegment()+1);
		setLeft(HousenumberGenerator.isLeft(c1, c2, getLocation()));
		
	}
}


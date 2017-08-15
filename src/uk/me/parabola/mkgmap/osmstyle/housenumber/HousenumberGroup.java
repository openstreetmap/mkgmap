/*
 * Copyright (C) 2015 Gerd Petermann
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

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
import uk.me.parabola.mkgmap.general.MapRoad;

/**
 * Combine two or more HousenumberMatch instances that 
 * can be found at the same point on the road
 * (with a tolerance of a few meters).
 * We will create a single Numbers instance for them.
 * 
 * @author Gerd Petermann
 *
 */
public class HousenumberGroup {

	private static final Logger log = Logger.getLogger(HousenumberGroup.class);
	final HousenumberRoad hnr;
	final List<HousenumberMatch> houses = new ArrayList<>();
	Int2IntOpenHashMap usedNumbers;
	int minNum,  maxNum;
	int minSeg , maxSeg;
	double minFrac, maxFrac;
	HousenumberMatch closestHouseToRoad ;
	HousenumberMatch farthestHouseToRoad;
	int odd,even;
	Coord linkNode;
	boolean findSegmentWasCalled;
	
	public HousenumberGroup(HousenumberRoad hnr, List<HousenumberMatch> housesToUse) {
		this.hnr = hnr;
		reset();
		for (HousenumberMatch house : housesToUse){
			addHouse(house);
		}
	}
	
	private void addHouse(HousenumberMatch house){
		int num = house.getHousenumber();
		if (num % 2 == 0)
			++even;
		else 
			++odd;
		int count = usedNumbers.get(num);
		usedNumbers.put(num, count + 1);
		
		if (houses.isEmpty()){
			minNum = maxNum = house.getHousenumber();
			minSeg = maxSeg = house.getSegment();
			minFrac = maxFrac = house.getSegmentFrac();
			closestHouseToRoad = farthestHouseToRoad = house;
		} else {
			if (house.getSegment() < minSeg){
				minSeg = house.getSegment();
				minFrac = house.getSegmentFrac();
			} else if (house.getSegment() > maxSeg){
				maxSeg = house.getSegment();
				maxFrac = house.getSegmentFrac();
			} else if (house.getSegment() == minSeg ){
				minFrac = Math.min(minFrac, house.getSegmentFrac());
			} else if (house.getSegment() == maxSeg ){
				maxFrac = Math.max(maxFrac, house.getSegmentFrac());
			}
			minNum = Math.min(minNum, num);
			maxNum = Math.max(maxNum, num);
			if (house.getDistance() < closestHouseToRoad.getDistance())
				closestHouseToRoad = house;
			if (house.getDistance() > farthestHouseToRoad.getDistance())
				farthestHouseToRoad = house;
		}
		houses.add(house);
	}
	
	private static final double MIN_DISTANCE_TO_EXISTING_POINT = 7.5;
	
	/**
	 * find place for the group, change to or add number nodes
	 * @param nodesForLinks 
	 * @return true if one or two nodes were added
	 */
	public boolean findSegment(String streetName, List<HousenumberGroup> groups){
		if (minSeg < 0 || maxSeg < 0){
			log.error("internal error: group is not valid:",this);
			return false;
		}
		findSegmentWasCalled = true;
		linkNode = null;
		List<Coord> points = getRoad().getPoints();
		Coord pointToUse = null;
		int seg = closestHouseToRoad.getSegment();
		Coord c1 = points.get(seg);
		Coord c2 = points.get(seg + 1);
		if (c1.highPrecEquals(c2)){
			boolean useExisting = true;
			// already a zero length segment
			for (HousenumberGroup hg : groups){
				if (hg == this)
					continue;
				if (hg.linkNode == c1){
					if (hg.closestHouseToRoad.isLeft() != this.closestHouseToRoad.isLeft()){
						// attach this group to the same segment on the other road side
						linkNode = c1;
						return false;
					} else {
						log.warn("two groups on same side of road share same point, group 1:",hg,"group 2:",this,"in road",getRoad());
						useExisting = false;
						break;
					}
				}
			}
			if (useExisting){
				c1.setNumberNode(true);
				c2.setNumberNode(true);
				linkNode = c1;
				return false;
			}
		}
		int timesToAdd = 1;
		double frac = closestHouseToRoad.getSegmentFrac();
		if (frac < 0) frac = 0;
		if (frac > 1) frac = 1;
		double segLen = c1.distance(c2);
		pointToUse = c1.makeBetweenPoint(c2, frac);
		double len1 = segLen * frac;
		double len2 = (1 - Math.min(1, frac)) * segLen;
		if (Math.min(len1, len2) < MIN_DISTANCE_TO_EXISTING_POINT){
			pointToUse = (len1 <= len2) ? c1 : c2;
		} else {
			Coord optPoint = ExtNumbers.rasterLineNearPoint(c1, c2, pointToUse, true);
			if (optPoint == null) {
				log.error("Internal error: ExtNumbers.rasterLineNearPoint() found no point, cannot improve address search for road",getRoad());
				return false;
			}
			double opt1Dist = c1.distance(optPoint);
			double opt2Dist = c2.distance(optPoint);
			pointToUse = optPoint;
			if (Math.min(opt1Dist, opt2Dist) <= MIN_DISTANCE_TO_EXISTING_POINT){
				pointToUse = (opt1Dist < opt2Dist) ? c1 : c2;
			}
			else {
				if (log.isInfoEnabled()){
					double distLine = pointToUse.distToLineSegment(c1.getDisplayedCoord(), c2.getDisplayedCoord());
					log.info("adding number nodes at",pointToUse.toDegreeString(),"to optimize address search", "dist to line is", HousenumberGenerator.formatLen(distLine));
				}
				timesToAdd = 2;
			}
		}
		if (points.size() + timesToAdd > LineSplitterFilter.MAX_POINTS_IN_LINE)
			return false;
		pointToUse.setNumberNode(true);
		if (timesToAdd == 2){
			// add two new points between c1 and c2
			points.add(seg + 1, pointToUse);
			pointToUse.setAddedNumberNode(true);
			pointToUse = new Coord (pointToUse);
			pointToUse.setNumberNode(true);
			points.add(seg + 1, pointToUse);
			pointToUse.setAddedNumberNode(true);
			linkNode = pointToUse;
		} else {
		// copy it
			pointToUse = new Coord(pointToUse);
			pointToUse.setNumberNode(true);
			// add copy before c2 
			points.add(seg + 1, pointToUse);
			pointToUse.setAddedNumberNode(true);
			if (pointToUse.highPrecEquals(c1)){
				linkNode = c1;
			} else { 
				// link to the copy of c2 which is before c2
				linkNode = pointToUse;
			}
		}
		return true;
	}

	public boolean verify(){
		if (findSegmentWasCalled)
			return true;
		
		if (minSeg < 0 || maxSeg < 0)
			return false;
		int step = 1;
		if (odd == 0 || even == 0)
			step = 2;
		boolean ok = false;
		if (usedNumbers.size() == (maxNum - minNum) / step + 1)
			ok = true;

		// final check: 
		double deltaDist = Math.abs(closestHouseToRoad.getDistance() - farthestHouseToRoad.getDistance());
		if (houses.size() > 2 &&  deltaDist < houses.size() * 3 ){
			// more than two houses: make sure that they are really not parallel to the road 
			// for each house we calculate 3m so that a group is kept if it forms an angle of 45° or more
			// with the road, presuming that the road is rather straight
			ok = false;
		}
		for (HousenumberMatch house : houses){
			// forget the group, it will not improve search
			house.setGroup(ok ? this : null);
		}
		return ok;
	}
	
	public MapRoad getRoad(){
		return hnr.getRoad();
	}
	
	private final static double CLOSE_HOUSES_DIST = 10;
	public static boolean housesFormAGroup(HousenumberMatch house1, HousenumberMatch house2) {
		if (house1.isIgnored() || house2.isIgnored())
			return false;
		if (house1.getRoad() != house2.getRoad()){
			log.error("internal error, group check with houses on different roads?",house1.getElement().getId(),house2.getElement().getId());
			return false;
		}
//		assert house1.getRoad() == house2.getRoad();
		

		if (house1.getSegment() > house2.getSegment()){
			HousenumberMatch help = house1;
			house1 = house2;
			house2 = help;
		}
		double distBetweenHouses = house1.getLocation().distance(house2.getLocation());
		if (distBetweenHouses == 0)
			return true;
		double minDistToRoad = Math.min(house1.getDistance(), house2.getDistance());
		double maxDistToRoad = Math.max(house1.getDistance(), house2.getDistance());
		double distOnRoad = house2.getDistOnRoad(house1);
		
		if (house1.getSegment() != house2.getSegment()){
			if (minDistToRoad > 40 && distBetweenHouses < CLOSE_HOUSES_DIST)
				return true;
			
			// not the same segment, the distance on road may be misleading when segments have a small angle 
			// and the connection point is a bit more away 
			Coord c1 = house1.getLocation();
			Coord c2 = house2.getLocation();
			Coord closest1 = house1.getClosestPointOnRoad();
			Coord closest2 = house2.getClosestPointOnRoad();
			double frac1 = HousenumberGenerator.getFrac(closest1, closest2, c1);
			double frac2 = HousenumberGenerator.getFrac(closest1, closest2, c2);
			double segLen = closest1.distance(closest2);
			if (frac1 < 0) frac1 = 0;
			if (frac2 < 0) frac2 = 0;
			if (frac1 > 1) frac1 = 1;
			if (frac2 > 1) frac2 = 1;
			double distOnRoadSimple = (Math.max(frac1, frac2) - Math.min(frac1, frac2)) * segLen;
			if (distOnRoadSimple != distOnRoad){
//				log.debug("distOnRoad recalculation:", house1.getRoad(),house1,house2,distOnRoad,"--->",distOnRoadSimple);
				distOnRoad = distOnRoadSimple;
			}
		}
		if (distOnRoad <= 0){
			return true;
		}
		
		// two houses form a group when the distance on road is short
		// how short? The closer the houses are to the road, the shorter
		double toleranceDistOnRoad = 5 + maxDistToRoad/ 10;
		
		if (distOnRoad > toleranceDistOnRoad){
			return false;
		}
		
		double deltaDistToRoad = maxDistToRoad - minDistToRoad;
		double ratio2 = deltaDistToRoad / distBetweenHouses;
		// a ratio2 near or higher 1 means that the two houses and the closest point on the 
		// road are on a straight line
		if (ratio2 > 0.9)
			return true;
		if (ratio2 < 0.666)
			return false;
		return true;
	}

	public boolean tryAddHouse(HousenumberMatch house) {
		if (house.isInterpolated() || house.getRoad() == null || house.isIgnored())
			return false;
		int num = house.getHousenumber();
		int step = 1;
		if (odd == 0 || even == 0)
			step = 2;
		if (num - maxNum != step)
			return false;
		HousenumberMatch last = houses.get(houses.size()-1);
		if (last.getGroup() != null){ 
			if (last.getGroup() == house.getGroup()){
				addHouse(house);
				return true;
			} else 
				return false;
		}
		if (last.getDistance() + 3 < house.getDistance() && last.isDirectlyConnected(house)){
			addHouse(house);
			return true;
		}
			
		if (housesFormAGroup(house, last) == false){
			return false;
		}
		if (houses.size() > 1){
			HousenumberMatch first = houses.get(0);
			if (housesFormAGroup(house, first) == false){
				HousenumberMatch preLast = houses.get(houses.size()-2);
				double angle = Utils.getAngle(house.getLocation(), last.getLocation(), preLast.getLocation());
 				if (Math.abs(angle) > 30)
					return false;
			}
		}
		addHouse(house);
		return true;
	}


	public boolean recalcPositions(){
		List<HousenumberMatch> saveHouses = new ArrayList<>(houses);
		reset();
		for (HousenumberMatch house : saveHouses)
			addHouse(house);
		if (!verify()){
			for (HousenumberMatch house : houses){
				HousenumberGenerator.findClosestRoadSegment(house, getRoad());
			}
			return false;
		}
		return true;
	}

	private void reset() {
		usedNumbers = new Int2IntOpenHashMap();
		minNum = Integer.MAX_VALUE; 
		maxNum = -1;
		minSeg = Integer.MAX_VALUE;
		maxSeg = -1;
		minFrac = maxFrac = Double.NaN;
		closestHouseToRoad = null;
		farthestHouseToRoad = null;
		odd = even = 0;
		houses.clear();
	}

	public String toString(){
		return houses.toString();
	}
}

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
	Int2IntOpenHashMap usedNumbers = new Int2IntOpenHashMap();
	int minNum = Integer.MAX_VALUE, maxNum = -1;
	int minSeg = Integer.MAX_VALUE, maxSeg = -1;
	double minFrac = Double.NaN, maxFrac = Double.NaN;
	int odd,even;
	Coord linkNode;
	
	public HousenumberGroup(HousenumberRoad hnr, List<HousenumberMatch> houses) {
		this.hnr = hnr;
		this.houses.addAll(houses);
		updateFields();
	}
	
	private void updateFields() {
		HousenumberMatch pred = null;
		for (HousenumberMatch hnm: houses){
			int num = hnm.getHousenumber();
			if (num % 2 == 0)
				++even;
			else 
				++odd;
			int count = usedNumbers.get(num);
			usedNumbers.put(num, count + 1);
			
			hnm.setGroup(this);
			if (pred == null){
				minNum = maxNum = hnm.getHousenumber();
				minSeg = maxSeg = hnm.getSegment();
				minFrac = maxFrac = hnm.getSegmentFrac();
			} else {
				if (hnm.getSegment() < minSeg){
					minSeg = hnm.getSegment();
					minFrac = hnm.getSegmentFrac();
				} else if (hnm.getSegment() > maxSeg){
					maxSeg = hnm.getSegment();
					maxFrac = hnm.getSegmentFrac();
				} else if (minSeg == maxSeg){
					minFrac = Math.min(minFrac, hnm.getSegmentFrac());
					maxFrac = Math.max(maxFrac, hnm.getSegmentFrac());
				}
				minNum = Math.min(minNum, num);
				maxNum = Math.max(maxNum, num);
			}
			pred = hnm;
		}
	}

	/**
	 * 
	 * @return true if one or two nodes were added
	 */
	public boolean findSegment(String streetName){
		// TODO: add checks, simplify
		List<Coord> points = getRoad().getPoints();
		Coord point = null;
		int countNumNodes = 0;
		int nodePos = -1;
		for (int i = minSeg+1; i <= maxSeg; i++){
			if (points.get(i).isNumberNode()){
				++countNumNodes;
				if (point == null){
					point = points.get(i);
					nodePos = i;
				}
			}
			if (countNumNodes > 1){
				for (HousenumberMatch hnm : houses){
					hnm.setSegment(i-1);
					hnm.setSegmentFrac(1);
					linkNode = point;
				}
				return false;
			}
		}
		if (countNumNodes > 0 && point != null) {
			if (points.size() + 1 > LineSplitterFilter.MAX_POINTS_IN_LINE)
				return false;
			linkNode = point;
			Coord c2 = points.get(nodePos + 1);
			if (point.highPrecEquals(c2) && c2.isNumberNode()){
				// nothing to do, we already have a zero-length segment with two number nodes
				return false;
			}
			// duplicate existing node
			point = Coord.makeHighPrecCoord(point.getHighPrecLat(), point.getHighPrecLon());
			point.setNumberNode(true);
			points.add(nodePos+1, point);
			return true;
			
		}
		
		if (point == null){
			if (minSeg != maxSeg){
				if (points.size() + 1 > LineSplitterFilter.MAX_POINTS_IN_LINE)
					return false;
				point = points.get(maxSeg);
				linkNode = point; 
				point.setNumberNode(true);
				point = Coord.makeHighPrecCoord(point.getHighPrecLat(), point.getHighPrecLon());
				point.setNumberNode(true);
				points.add(maxSeg + 1, point);
				return true;
			}
		}
		
		if (point == null){
			assert minSeg == maxSeg;
			Coord c1 = points.get(minSeg);
			Coord c2 = points.get(minSeg + 1);
			if (c1.highPrecEquals(c2)){
				// group is already attached to zero-segment-length
				linkNode = c1;
				return false;
			}
			if (points.size() + 2 > LineSplitterFilter.MAX_POINTS_IN_LINE)
				return false;
			int timesToAdd = 1;
			double seglen = c1.distance(c2);
			double midFrac = (Math.max(0, minFrac) + Math.min(1, maxFrac)) / 2;
			point = c1.makeBetweenPoint(c2, midFrac);
			double hard1Dist = c1.distance(point);
			double hard2Dist = c2.distance(point);
			
			if (minFrac <= 0 || hard1Dist <= 10)
				point = c1;
			else if (maxFrac >= 1 || hard2Dist <= 10)
				point = c2;
			else {
				Coord optPoint = ExtNumbers.rasterLineNearPoint(c1, c2, point, true);
//				log.debug("check optic for segment with length",HousenumberGenerator.formatLen(seglen),getRoad(),houses);
//				double hardDist = point.getDisplayedCoord().distToLineSegment(c1.getDisplayedCoord(), c2.getDisplayedCoord());
//				double optDist = optPoint.getDisplayedCoord().distToLineSegment(c1.getDisplayedCoord(), c2.getDisplayedCoord());
//				double usedFrac = HousenumberGenerator.getFrac(c1, c2, optPoint);
				double opt1Dist = c1.distance(optPoint);
				double opt2Dist = c2.distance(optPoint);
//				log.debug(HousenumberGenerator.formatLen(hard1Dist),HousenumberGenerator.formatLen(hard2Dist));
//				log.debug(HousenumberGenerator.formatLen(opt1Dist),HousenumberGenerator.formatLen(opt2Dist));
//				log.debug(HousenumberGenerator.formatLen(hardDist),HousenumberGenerator.formatLen(optDist), minFrac,maxFrac,midFrac,usedFrac);
				point = optPoint;
				if (Math.min(opt1Dist, opt2Dist) <= 10){
					point = (opt1Dist < opt2Dist) ? c1 : c2;
//					log.debug("duplicating existing node");
				}
				else {
					timesToAdd = 2;
//					log.debug("adding optimized point between original points two times");
				}
//				long dd = 4;
			}
			linkNode = null;
			if (timesToAdd == 1){
				// we are duplicating an existing point
				point.setNumberNode(true);
				if (point == c1)
					linkNode = point;
			}
			point = Coord.makeHighPrecCoord(point.getHighPrecLat(), point.getHighPrecLon());
			point.setNumberNode(true);
			points.add(minSeg + 1, point);
			if (timesToAdd > 1){
				point = Coord.makeHighPrecCoord(point.getHighPrecLat(), point.getHighPrecLon());
				point.setNumberNode(true);
				points.add(minSeg + 1, point);
				linkNode = point;
			}
			if (linkNode == null)
				linkNode = point;
			return true;
		}
		return false;
	}

	public boolean verify(){
		int step = 1;
		if (odd == 0 || even == 0)
			step = 2;
		if (usedNumbers.size() == (maxNum - minNum) / step + 1)
			return true;
		for (HousenumberMatch hnm : houses)
			hnm.setGroup(null);
		return false;
	}
	
	public MapRoad getRoad(){
		return hnr.getRoad();
	}
	
	public String toString(){
		return houses.toString();
	}
}

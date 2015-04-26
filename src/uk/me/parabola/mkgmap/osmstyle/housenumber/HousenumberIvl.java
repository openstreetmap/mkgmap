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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Represents a (part of an) addr:interpolation way.
 * It contains the points between two house number elements
 * and the information how numbers are interpolated along
 * the way that is described by these points. 
 * 
 * We try to use the information to find 
 * a) the right road for the houses
 * b) the right road segment 
 * c) the position of the interpolated houses
 * 
 * We have to be aware of several special cases so we use some
 * flags to say for which of the above points the information
 * can be used.
 * 
 * @author Gerd Petermann
 *
 */
public class HousenumberIvl {
	private static final Logger log = Logger.getLogger(HousenumberIvl.class);
	
	/** Gives the maximum distance between house number element and the matching road 
	 * when the number is part of an addr:interpolation way */
	public static final double MAX_INTERPOLATION_DISTANCE_TO_ROAD = 75.0;

	private final String streetName;
	private final Way interpolationWay;
	private MapRoad roadForInterpolatedHouses;
	private final Node n1,n2;
	private List<Coord> points;
	private int step, start, end, steps;
	private HousenumberMatch[] knownHouses = {null, null}; 
	
	private boolean hasMultipleRoads;
	private boolean foundCluster;
	private int interpolated; // counter to detect wrong double use 
	private boolean ignoreForInterpolation;

	private boolean equalEnds;
	private static final short streetTagKey = TagDict.getInstance().xlate("mkgmap:street");
	private static final short housenumberTagKey = TagDict.getInstance().xlate("mkgmap:housenumber");		
	private static final short addrInterpolationTagKey = TagDict.getInstance().xlate("addr:interpolation");

	
	public HousenumberIvl(String steetName, Way interpolationWay, Node n1, Node n2) {
		this.streetName = steetName;
		this.interpolationWay = interpolationWay;
		this.n1 = n1;
		this.n2 = n2;
	}
	
	public void setPoints(List<Coord> points) {
		this.points = new ArrayList<Coord>(points);
	}
	public void setStep(int step) {
		this.step = step;
	}
	public int getStep() {
		return step;
	}
	public void setStart(int start) {
		this.start = start;
	}

	public int getStart() {
		return start;
	}
	public void setEnd(int end) {
		this.end = end;
	}
	public int getEnd() {
		return end;
	}
	public void setSteps(int steps) {
		this.steps = steps;
	}


	public Node getNode1() {
		return n1;
	}

	public Node getNode2() {
		return n2;
	}

//	public boolean needsSplit(){
//		return needsSplit;
//	}
	
	public void addHousenumberMatch(HousenumberMatch house) {
		if (house.getElement() == n1)
			knownHouses[0] = house;
		else if (house.getElement() == n2)
			knownHouses[1] = house;
		else {
			log.error("cannot add",house,"to",this);
		}
	}
	
	public boolean checkRoads(){
		boolean res = checkRoads2();
		if (!res || equalEnds){
			// the interval is not ok --> ignore the numbers as well
			ignoreNodes();
		}  
		return res;
	}
	
	private boolean checkRoads2(){
		for (int i = 0; i < 2; i++){
			if (knownHouses[i] == null ){
				log.error("internal error: housenumber matches not properly set", this);
				return false;
			}
			if (knownHouses[i].getRoad() == null || knownHouses[i].getDistance() > 100 ){
				log.warn("cannot find any reasonable road for both nodes, ignoring them",streetName,this);
				return false;
			}
		}
		if (knownHouses[0].getRoad().getRoadDef().getId() == knownHouses[1].getRoad().getRoadDef().getId()){
			if (knownHouses[0].getRoad() != knownHouses[1].getRoad()){
				// special case: interval goes along clipped road, data is probably OK
				hasMultipleRoads = true;
				return true;
			}
			for (MapRoad r : knownHouses[0].getAlternativeRoads()){
				if (r.getRoadDef().getId() == knownHouses[0].getRoad().getRoadDef().getId()){
					// special case: interval may go along clipped road, data is probably OK
					hasMultipleRoads = true;
					return true;
				}
			}			
		}
		// make sure that the closest road is one with a matching name
		for (int i = 0; i < 2; i++){
			while (streetName.equals(knownHouses[i].getRoad().getStreet()) == false && knownHouses[i].hasAlternativeRoad()){
				HousenumberMatch testx = new HousenumberMatch(knownHouses[i]);
				MapRoad r = knownHouses[i].getAlternativeRoads().remove(0);
				HousenumberGenerator.findClosestRoadSegment(testx, r);
				if (testx.getDistance() < MAX_INTERPOLATION_DISTANCE_TO_ROAD){
					copyRoadData(testx, knownHouses[i]);
				}
			}
		}
		List<MapRoad> toTest = new ArrayList<>();
		toTest.add(knownHouses[0].getRoad());
		toTest.add(knownHouses[1].getRoad());
		for (MapRoad r : knownHouses[0].getAlternativeRoads()){
			if (knownHouses[1].getAlternativeRoads().contains(r))
				toTest.add(r);
		}
		HousenumberMatch[] test = new HousenumberMatch[2];
		boolean foundSingleRoad = false;
		for (MapRoad r : toTest){
			if (streetName.equals(r.getStreet()) == false)
				continue;
			foundSingleRoad = true;
			for (int i = 0; i < 2; i++){
				test[i] = knownHouses[i];
				if (test[i].getRoad() != r){
					test[i] = new HousenumberMatch(knownHouses[i]);
					HousenumberGenerator.findClosestRoadSegment(test[i], r);
					test[i].calcRoadSide();
				}
				if (test[i].getRoad() == null || test[i].getDistance() > MAX_INTERPOLATION_DISTANCE_TO_ROAD ){
					foundSingleRoad = false;
					break;
				}
			}
			if (foundSingleRoad){
				if (test[0].isLeft() != test[1].isLeft()){
					foundSingleRoad = false;
					continue;
				}
				int s0 = test[0].getSegment();
				int s1 = test[1].getSegment();
				if (s0 != s1){
					// not the same segment, find out if another is better
					// check if the road and the addr:interpolation way are nearly parallel lines
					double angle1 = Utils.getAngle(test[0].getClosestPointOnRoad(), points.get(0),points.get(1));
					if (Math.abs(angle1) < 30){
						foundSingleRoad = false;
						HousenumberMatch testx = new HousenumberMatch(test[0]);
						for (int s = Math.min(s0,s1); s <= Math.max(s0, s1); s++){
							if (s != test[0].getSegment()){
								HousenumberGenerator.findClosestRoadSegment(testx, r, s,s+1);
								angle1 = Utils.getAngle(testx.getClosestPointOnRoad(), points.get(0),points.get(1));
								if (Math.abs(angle1) >= 30 && testx.getDistance() < 2*test[0].getDistance()){
									test[0] = testx;
									foundSingleRoad = true;
									break;
								}
							}
						}
					}
					double angle2 = Utils.getAngle(points.get(points.size()-2),points.get(points.size()-1),test[1].getClosestPointOnRoad());
					if (Math.abs(angle2) < 30){
						foundSingleRoad = false;
						HousenumberMatch testx = new HousenumberMatch(test[1]);
						for (int s = Math.min(s0,s1); s <= Math.max(s0, s1); s++){
							if (s != test[1].getSegment()){
								HousenumberGenerator.findClosestRoadSegment(testx, r, s,s+1);
								angle2 = Utils.getAngle(points.get(points.size()-2),points.get(points.size()-1),testx.getClosestPointOnRoad());
								if (Math.abs(angle2) >= 30 && testx.getDistance() < 2*test[1].getDistance()){
									test[1] = testx;
									foundSingleRoad = true;
									break;
								}
							}
						}
					}
					
				}
				
			}
			if (foundSingleRoad)
				break;
		}
		if (!foundSingleRoad){
			if (streetName.equals(knownHouses[0].getRoad().getStreet()) == false || streetName.equals(knownHouses[1].getRoad().getStreet()) == false){
				log.warn("cannot find reasonable road for both nodes",streetName,this);
				return false;
			}
			hasMultipleRoads = true;
			return true;
		}
		// we found the road that should be used for interpolation
		roadForInterpolatedHouses = test[0].getRoad();

		// we found a single plausible road, make sure that both nodes are using it
		for (int i = 0; i < 2; i++){
			if (knownHouses[i].getRoad() != test[i].getRoad() || knownHouses[i].getSegment() != test[i].getSegment()){
				copyRoadData(test[i], knownHouses[i]);
				knownHouses[i].forgetAlternativeRoads();
			}
			if (knownHouses[i].getSegmentFrac() < 0 || knownHouses[i].getSegmentFrac() > 1){
				hasMultipleRoads = true;
			}
		}
		if (knownHouses[0].isLeft() != knownHouses[1].isLeft()){
			log.warn("addr:interpolation way crosses road",streetName,this);
			return false;
		}
		return true;
	}

	private void copyRoadData(HousenumberMatch source, HousenumberMatch dest) {
		if (log.isInfoEnabled()){
			if (source.getRoad() != dest.getRoad())
				log.info("moving",streetName,dest.getSign(),dest.getElement().toBrowseURL(),"from road",dest.getRoad(),"to road",source.getRoad());
			else 
				log.info("moving",streetName,dest.getSign(),dest.getElement().toBrowseURL(),"from segment",dest.getSegment(),"to ",source.getSegment(),"in road",source.getRoad());
		}
		dest.setRoad(source.getRoad());
		dest.setSegment(source.getSegment());
		dest.setSegmentFrac(source.getSegmentFrac());
		dest.setDistance(source.getDistance());
		dest.calcRoadSide();
	}
	
	public List<HousenumberMatch> getInterpolatedHouses(){
		List<HousenumberMatch> houses = new ArrayList<>();
		if (ignoreForInterpolation|| start == end || steps <= 0)
			return houses;
		List<Coord> interpolatedPoints = getInterpolatedPoints();
		int usedStep = (start < end) ? step : -step;
		int hn = start;
		boolean distanceWarningIssued = false;
		for (Coord co : interpolatedPoints){
			hn += usedStep;
			Node generated = new Node(interpolationWay.getId(), co);
			generated.setFakeId();
			generated.addTag(streetTagKey, streetName);
			String number = String.valueOf(hn);
			generated.addTag(housenumberTagKey, number);
			// TODO: maybe add check that city info and zip code of both houses is equal ?
			// what if not ?
			HousenumberElem houseElem = new HousenumberElem(generated, knownHouses[0].getCityInfo());
			houseElem.setHousenumber(hn);
			houseElem.setZipCode(knownHouses[0].getZipCode());
			houseElem.setStreet(streetName);
			houseElem.setSign(number);
			HousenumberMatch house = new HousenumberMatch(houseElem);
			if (roadForInterpolatedHouses != null){
				HousenumberGenerator.findClosestRoadSegment(house, roadForInterpolatedHouses);
				if (house.getRoad() == null || house.getDistance() > MAX_INTERPOLATION_DISTANCE_TO_ROAD ){
					if (distanceWarningIssued == false){
						log.warn("interpolated house is not close to expected road",this,house);
						distanceWarningIssued = true;
					}
					continue;
				}
				house.calcRoadSide();
			}
			house.setInterpolated(true);
			houses.add(house);
		}
		if (log.isDebugEnabled()){
			String addrInterpolationMethod = interpolationWay.getTag(addrInterpolationTagKey);
			if (hasMultipleRoads == false)
				log.debug(this,"generated",addrInterpolationMethod,"interpolated number(s) for",knownHouses[0].getRoad());
			else 
				log.debug(this,"generated",addrInterpolationMethod,"interpolated number(s) for",streetName);
		}
		return houses;
	}
	/**
	 * Calculate the wanted number of coords on a way so that they have
	 * similar distances to each other (and to the first and last point 
	 * of the way).
	 * @param points list of points that build the way
	 * @param num the wanted number 
	 * @return a list with the number of points or the empty list in 
	 * case of errors
	 */
	public List<Coord> getInterpolatedPoints(){
		if (interpolated > 0){
			log.debug("interpolating numbers again for", this );
		}
		interpolated++;
		if (steps < 1 || points.size() < 2)
			return Collections.emptyList();

		List<Coord> interpolated = new ArrayList<>(steps);
		double wayLen = 0;
		for (int i = 0; i+1 < points.size(); i++){
			wayLen += points.get(i).distance(points.get(i+1));
		}
		double ivlLen = wayLen / (steps+1);
		if (ivlLen < 0.1){
			if (log.isInfoEnabled())
				log.info("addr:interpolation",interpolationWay.toBrowseURL(),"segment ignored, would generate",steps,"houses with distance of",ivlLen,"m");
			return interpolated;
		}
		int pos = 0;
		double rest = 0;
		while (pos+1 < points.size()){
			Coord c1 = points.get(pos);
			Coord c2 = points.get(pos+1);
			pos++;
			double neededPartOfSegment = 0;
			double segmentLen = c1.distance(c2);
			for(;;){
				neededPartOfSegment += ivlLen - rest;
				if (neededPartOfSegment <= segmentLen){
					double fraction = neededPartOfSegment / segmentLen;
					Coord c = c1.makeBetweenPoint(c2, fraction);
					interpolated.add(c);
					if (interpolated.size() >= steps){
//						GpxCreator.createGpx("e:/ld/road", knownHouses[0].getRoad().getPoints());
//						GpxCreator.createGpx("e:/ld/test", interpolated, Arrays.asList(points.get(0),points.get(points.size()-1)));
						return interpolated;
					}
					rest = 0;
				} else {
					rest = segmentLen - neededPartOfSegment + ivlLen;
					break;
				}
			}
			
		}
		log.warn("addr:interpolation",interpolationWay.toBrowseURL(),"interpolation for segment with nodes",n1.getId(),n2.getId(),"failed");
		return interpolated;
	}
	
	public String toString() {
		return interpolationWay.toBrowseURL() + " " + start + ".." + end + ", step=" + step;
	}

	public String getDesc() {
		return streetName + "_" + start + ".." + end + "_" + step;
	}

//	public boolean setNodeRefs(HashMap<Element, HousenumberMatch> houses) {
//		knownHouses[0] = houses.get(n1);
//		knownHouses[1] = houses.get(n2);
//		if (knownHouses[0] == null || knownHouses[1] == null)
//			return false;
//		knownHouses[0].incIntervalInfoRefs();
//		knownHouses[1].incIntervalInfoRefs();
//		return true;
//	}
//
	public void ignoreNodes() {
		for (int i = 0; i < 2; i++){
			if (knownHouses[i] != null){
				knownHouses[i].decIntervalInfoRefs();
				if (knownHouses[i].getIntervalInfoRefs() == 0)
					knownHouses[i].setIgnored(true);
			}
		}
	}

	public long getId() {
		return interpolationWay.getId();
	}

	
	public boolean ignoreForInterpolation() {
		return ignoreForInterpolation;
	}

	public void setIgnoreForInterpolation(boolean ignoreForInterpolation) {
		this.ignoreForInterpolation = ignoreForInterpolation;
	}

	public boolean isBad() {
		return false;
	}


	public boolean inCluster(List<HousenumberMatch> housesNearCluster) {
		int count = 0;
		for (HousenumberMatch house : housesNearCluster){
			if (knownHouses[0] == house || knownHouses[1] == house){
				++count;
				
			}
			if (count == 2)
				break;
		}
		if (count > 0){
			foundCluster = true;
			return true;
		}
		return false;
	}

	public boolean foundCluster() {
		return foundCluster;
	}

	public void setEqualEnds() {
		this.equalEnds = true;
		
	}

	public boolean setNodeRefs(Map<Long, Integer> interpolationNodes,
			List<HousenumberElem> houseElems) {
		for (int i = 0; i < 2; i++){
			long id = (i == 0) ? n1.getId(): n2.getId();
			Integer elemPos = interpolationNodes.get(id);
			if (elemPos == null || elemPos >= houseElems.size())
				return false;
			HousenumberElem he = houseElems.get(elemPos);
			if (he instanceof HousenumberMatch == false)
				return false;
			if (he.getElement().getId() != id)
				return false;
			knownHouses[i] = (HousenumberMatch) he;
			knownHouses[i].incIntervalInfoRefs();
		}
		return true;
	}
	
	public HousenumberMatch[] getHouseNodes (){
		return knownHouses;
	}
}

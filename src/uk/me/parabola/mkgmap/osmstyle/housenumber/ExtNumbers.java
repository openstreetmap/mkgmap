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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.net.NumberStyle;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
import uk.me.parabola.mkgmap.general.CityInfo;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.ZipCodeInfo;
import uk.me.parabola.mkgmap.osmstyle.housenumber.HousenumberGenerator.HousenumberMatchByPosComparator;

/**
 * Helper class to allow easy corrections like splitting.
 * 
 * If we want to split an interval because it overlaps
 * with another one, we have different options.
 * 1) if the interval covers multiple points of the road, we may change a point to a number node
 * 2) or if the road segment is long enough we may add a point to split it,
 * long enough means that the we can find a point that is so close to the original
 * line that it is not too distorting,   
 * 3) or we may duplicate the node at one end (or both) and move some of the numbers to that new
 * zero-length-interval.
 * 
 * When we find no more overlaps, we can start to reduce the distance
 * of the calculated position (the result of the address search in 
 * Garmin products) and the known best position on the road.
 * This is a bit tricky: Garmin software places  
 * a)  a single house in the middle of the 
 * segment covered by the interval
 * b) two or more houses are placed so that the first
 * is at the very beginning, the last is at the very end,
 * the rest is between them with equal distances.
 * The problem: We can have houses on both sides of the segment,
 * so the optimal length for the left side may not be the
 * best for the right side. 
 * We try to find a good compromise between good search result
 * and the number of additional intervals. 
 *  
 * @author GerdP
 *
 */
public class ExtNumbers {
	private static final Logger log = Logger.getLogger(ExtNumbers.class);

	private final HousenumberRoad housenumberRoad;
	private static final int MAX_LOCATE_ERROR = 40; 
	
	private static final List<HousenumberMatch> NO_HOUSES = Collections.emptyList();
	
	public ExtNumbers prev,next;
	class RoadSide {
		List<HousenumberMatch> houses = Collections.emptyList();
		boolean multipleZipCodes;
		boolean multipleCities;
		boolean notInOrder;
	}
	
	private RoadSide leftSide,rightSide;
	private Numbers numbers = null;
	private int startInRoad, endInRoad;
	private int nodeIndex;
	
	private boolean needsSplit;
	private HousenumberMatch worstHouse;
	
	// indicates a number that is found in the interval, but should not   
	private int badNum;
	
	private boolean hasGaps; // true if interval covers more numbers than known

	// constants representing reasons for splitting
	public static final int SR_FIX_ERROR = 0;
	public static final int SR_OPT_LEN = 1;
	public static final int SR_SPLIT_ROAD_END = 2;
	
	public ExtNumbers(HousenumberRoad housenumberRoad) {
		super();
		this.housenumberRoad = housenumberRoad;
		reset();
	}

	private void setNeedsSplit(boolean b) {
		needsSplit = true;
	}

	public boolean needsSplit() {
		return needsSplit;
	}

	private boolean notInOrder(boolean left){
		RoadSide rs = (left) ? leftSide : rightSide;
		return (rs != null) ? rs.notInOrder : false; 
	}

	private List<HousenumberMatch> getHouses(boolean left){
		RoadSide rs = (left) ? leftSide : rightSide;
		return (rs != null) ? rs.houses : NO_HOUSES;  
	}

	private MapRoad getRoad(){
		return housenumberRoad.getRoad();
	}
	
	private void reset() {
		numbers = null;
		needsSplit = false;
		hasGaps = false;
	}
	
	
	public void setNodeIndex(int nodeIndex) {
		this.nodeIndex = nodeIndex; 
		if (numbers != null)
			numbers.setIndex(nodeIndex);
	}


	
	public Numbers getNumbers() {
		if (numbers == null){
			numbers = new Numbers();
			numbers.setIndex(nodeIndex);
			fillNumbers(Numbers.LEFT);
			fillNumbers(Numbers.RIGHT);
			if (!numbers.isEmpty()){
				verify(getHouses(Numbers.LEFT)); // TODO : remove 
				verify(getHouses(Numbers.RIGHT)); // TODO : remove
			}
		}
		return numbers;
	}

	/**
	 * Store given house numbers and meta info
	 * @param housenumbers a list of house numbers, sorted by appearance on the road
	 * @param startSegment index of road point where this segment starts
	 * @param endSegment index of road point where this segment ends
	 * @param left {@code true} the left side of the street; {@code false} the right side of the street
	 * @return the number of elements which were used (from the beginning)
	 */
	public int setNumbers(List<HousenumberMatch> housenumbers, int startSegment, int endSegment, boolean left) {
		int assignedNumbers = 0;
		if (housenumbers.isEmpty() == false) {
			RoadSide rs = new RoadSide(); 
			if (left)
				leftSide = rs;
			else
				rightSide = rs;
			// get the sublist of house numbers
			int maxN = -1;
			for (int i = 0; i< housenumbers.size(); i++) {
				HousenumberMatch house = housenumbers.get(i);
				if (house.isIgnored())
					continue;
				if (house.getSegment() >= endSegment) {
					break;
				} 
				maxN = i;
			}
			
			if (maxN >= 0) {
				assignedNumbers = maxN + 1;
				rs.houses = new ArrayList<>(housenumbers.subList(0, assignedNumbers));
				startInRoad = startSegment;
				endInRoad = endSegment;
				assert startSegment < endSegment;
				if (getRoad().getPoints().get(startInRoad).isNumberNode() == false || getRoad().getPoints().get(endInRoad).isNumberNode() == false){
					log.error("internal error: start or end is not a number node", this);
				}
			}
		}
		return assignedNumbers;
	}
	
	/**
	 * Apply the given house numbers to the numbers object.
	 * @param left {@code true} the left side of the street; {@code false} the right side of the street
	 */
	private void fillNumbers(boolean left) {
		NumberStyle style = NumberStyle.NONE;
		List<HousenumberMatch> houses = getHouses(left);
		if (houses.isEmpty() == false) {
			Set<CityInfo> cityInfos = new HashSet<>();
			Set<ZipCodeInfo> zipCodes = new HashSet<>();
			// get the sublist of house numbers
			boolean even = false;
			boolean odd = false;
			boolean inOrder = true;
			int lastDiff = 0;
			HousenumberMatch highest, lowest;
			lowest = highest = houses.get(0);
			Int2IntOpenHashMap distinctNumbers = new Int2IntOpenHashMap();
			int numHouses = houses.size();
			HousenumberMatch pred = null;
			for (int i = 0; i< numHouses; i++) {
				HousenumberMatch house = houses.get(i);
				if (house.getCityInfo() != null)
					cityInfos.add(house.getCityInfo());
				if (house.getZipCode() != null)
					zipCodes.add(house.getZipCode());
				int num = house.getHousenumber();
				if (!hasGaps)
					distinctNumbers.put(num, 1);
				if (num > highest.getHousenumber())
					highest = house;
				if (num < lowest.getHousenumber())
					lowest = house;
				if (num % 2 == 0) {
					even = true;
				} else {
					odd = true;
				}
				
				if (pred != null){
					int diff = num - pred.getHousenumber();
					if(lastDiff * diff < 0){
						// sign changed
						inOrder = false;
					}
					lastDiff = diff;
				}
				pred = house;
			}
			
			if (even && odd) {
				style = NumberStyle.BOTH;
			} else if (even) {
				style = NumberStyle.EVEN;
			} else {
				style = NumberStyle.ODD;
			}
			int highestNum = highest.getHousenumber();
			int lowestNum = lowest.getHousenumber();
			int start = houses.get(0).getHousenumber();
			int end = houses.get(numHouses-1).getHousenumber();
			boolean increasing = false; // from low to high
			if (start == end && highestNum - lowestNum != 0){
				if (prev != null){
					int lastEnd = prev.getNumbers().getEnd(left );
					if (lastEnd <= lowestNum)
						increasing = true;
				} else if (next != null){
					int nextStart = next.getNumbers().getStart(left);
					if (highestNum < nextStart)
						increasing = true;
				} else {
					increasing = true;
				}
			}
			else if (start != highestNum && start != lowestNum
					|| end != highestNum && end != lowestNum) {
				inOrder = false;
				if (start <= end)
					increasing = true;
			} else if (start < end){
				increasing = true;
			}
			if (increasing){
				start = lowestNum;
				end = highestNum;
			} else {
				start = highestNum;
				end = lowestNum;
			}
			if (!hasGaps){
				int step = (style == NumberStyle.BOTH) ? 1 : 2;
				for (int n = lowestNum+step; n < highestNum; n += step){
					if (distinctNumbers.containsKey(n))
						continue;
					hasGaps = true;
					break;
				}
			}
			RoadSide rs = (left) ? leftSide : rightSide;
			numbers.setNumbers(left, style, start, end);
			rs.multipleCities = (cityInfos.size() > 1);
			rs.multipleZipCodes = (zipCodes.size() > 1);
			if (cityInfos.size() == 1){
				CityInfo ci = cityInfos.iterator().next();
				if (ci.isEmpty() == false){
					if (ci.equals(housenumberRoad.getRoadCityInfo()) == false)
						numbers.setCityInfo(left, ci);
				}
			}
			
			if (zipCodes.size() == 1){
				ZipCodeInfo zipCodeInfo = zipCodes.iterator().next();
				if (zipCodeInfo.getZipCode() != null){
					if (zipCodeInfo.equals(housenumberRoad.getRoadZipCode()) == false){
						// we found a zip code and the road doesn't yet have one, use it for the whole road
						if (getRoad().getZip() == null){
							housenumberRoad.setZipCodeInfo(zipCodeInfo);
						} else
							numbers.setZipCode(left, zipCodeInfo);
					}
				}
			}
			rs.notInOrder = !inOrder;
		}
	}

	/**
	 * Return the intervals in the format used for the writer routines
	 * @return
	 */
	public List<Numbers> getNumberList() {
		// do we have numbers?
		boolean foundNumbers = false;
		for (ExtNumbers curr = this; curr != null; curr = curr.next){
			if (curr.hasNumbers()){
				foundNumbers = true;
				break;
			}
		}
		if (!foundNumbers)
			return null;
		
		List<Numbers> list = new ArrayList<>();
		for (ExtNumbers curr = this; curr != null; curr = curr.next){
			if (curr.hasNumbers() == false)
				continue;
			list.add(curr.getNumbers());
			if (log.isInfoEnabled()) {
				Numbers cn = curr.getNumbers();
				if (curr.prev == null)
					log.info("final numbers for",getRoad(),"in",getRoad().getCity());
				log.info("Left: ",cn.getLeftNumberStyle(),cn.getIndex(),"Start:",cn.getLeftStart(),"End:",cn.getLeftEnd(), "numbers "+curr.getHouses(Numbers.LEFT));
				log.info("Right:",cn.getRightNumberStyle(),cn.getIndex(),"Start:",cn.getRightStart(),"End:",cn.getRightEnd(), "numbers "+curr.getHouses(Numbers.RIGHT));
			}
		}
		return list;
	}
	
	public ExtNumbers checkSingleChainSegments(String streetName, boolean removeGaps) {
		ExtNumbers curr = this;
		ExtNumbers head = this;
		if (housenumberRoad.isRandom() || removeGaps){
			for (curr = head; curr != null; curr = curr.next){
				while (curr.hasGaps && (removeGaps || curr.notInOrder(true) || curr.notInOrder(false))){
					curr.worstHouse = null;
					curr.badNum = -1;
					ExtNumbers test = curr.tryChange(SR_FIX_ERROR);
					if (test != curr){
						if (curr.prev == null)
							head = test;
						curr = test;
					}
					else {
						log.warn("can't split numbers interaval for road", curr.getNumbers(), curr);
						break;
					}
				}

			}
		}
		for (curr = head; curr != null; curr = curr.next){
			while (curr.isPlausible() == false){
				// this happens in the following cases:
				// 1. correct OSM data, multiple houses build a block. Standing on the road
				// you probably see a small service road which leads to the houses.
				// It is okay to use each of them. 
				// 2. correct OSM data, one or more house should be connected to a 
				// different road with the same name, we want to ignore them 
				// 3. wrong OSM data, one or more numbers are wrong, we want to ignore them
				// 4. other cases, e.g. numbers 1,3,5 followed by 10,14,12. This should be fixed
				// by splitting the segment first, as the OSM data might be correct.
				if (log.isInfoEnabled())
					log.info("detected unplausible interval in",streetName, curr.getNumbers(),"in road", getRoad());
				if (log.isDebugEnabled()){
					if (curr.notInOrder(Numbers.LEFT))
						log.debug("left numbers not in order:", getRoad(), curr.getHouses(Numbers.LEFT));
					if (curr.notInOrder(Numbers.RIGHT))
						log.debug("right numbers not in order:", getRoad(), curr.getHouses(Numbers.RIGHT));
				}
				curr.setNeedsSplit(true);
				curr.findGoodSplitPos();
				ExtNumbers test = curr.tryChange(SR_FIX_ERROR);
				if (test != curr){
					housenumberRoad.setChanged(true);
					if (curr.prev == null)
						head = test;
					curr = test;
				}
				else {
					log.warn("can't fix unplausible numbers interaval for road",curr.getNumbers(),curr);
					break;
				}
			}
		}
		return head;
	}

	private void verify(List<HousenumberMatch> houses) {
		for (HousenumberMatch house : houses){
			if (house.isIgnored())
				continue;
			if (house.getSegment() < startInRoad || house.getSegment() >= endInRoad){
				log.error("internal error, house has wrong segment",getRoad(),"house",house,house.getElement().toBrowseURL());
			}
			if (Double.isNaN(house.getDistance()) || house.getDistance() > HousenumberGenerator.MAX_DISTANCE_TO_ROAD + 10){
				if (house.getGroup() == null)
					log.error("distance to road too large, road",getRoad(),"house",house,house.getElement().toBrowseURL());
			}
		}
	}

	
	/**
	 * @param reason
	 * @return
	 */
	public ExtNumbers tryChange(int reason){
		ExtNumbers en = this; 
		if (reason == SR_FIX_ERROR){
			if (notInOrder(Numbers.LEFT) == false && notInOrder(Numbers.RIGHT) == false){
				if (badNum < 0 && worstHouse != null)
					badNum = worstHouse.getHousenumber();
				if (badNum > 0){
					en = splitInterval();
					if(en == this){
						long dd  = 4;
					}
				}
				else {
					log.info("have to split",this);
				}
			}
		} 
		//TODO: in some cases it might be better to move a house to the prev or next interval instead of splitting
		if (en == this)
			en = tryAddNumberNode(reason);
		boolean changedInterval = false;
		if (en != this){
			if (en.hasNumbers() && en.next != null && en.next.hasNumbers()){
				changedInterval = true;
			} else {
				ExtNumbers test = en.hasNumbers() ?  en : en.next;
				if (test.getNumbers().isSimilar(this.getNumbers()) == false)
					changedInterval = true;
			}
			if (changedInterval)
				housenumberRoad.setChanged(true);
			else {
				if (reason == SR_FIX_ERROR){
					if (en.hasNumbers()){
						en.worstHouse = worstHouse;
						return en.tryChange(reason);
					} else {
						en.next.worstHouse = worstHouse;
						en.next = en.next.tryAddNumberNode(reason);
					}
				}
			}
		}
		return en;
	}
	
	/**
	 * Split an interval to remove overlaps 
	 * 1) detect the optimal split position 
	 * 2) calculate the new intervals
	 * @return
	 */
	private ExtNumbers splitInterval(){
		if (log.isDebugEnabled())
			log.debug("trying to split",this,"so that",badNum,"is not contained");
		boolean doSplit = false;
		
		Numbers origNumbers = getNumbers();
		if (origNumbers.countMatches(badNum) == 0){
			if (log.isDebugEnabled())
				log.debug("badNum",badNum,"is not contained in",this);
			return this;
		}
		// create an test interval to find out which side contains the  bad number
		Numbers testNumbers = new Numbers();
		testNumbers.setNumbers(Numbers.LEFT, origNumbers.getLeftNumberStyle(), origNumbers.getLeftStart(), origNumbers.getLeftEnd());
		boolean left = (testNumbers.countMatches(badNum) > 0);
		
		List<HousenumberMatch> before = new ArrayList<>();
		List<HousenumberMatch> after = new ArrayList<>();
		List<HousenumberMatch> toSplit = getHouses(left);
		boolean inc = (origNumbers.getEnd(left) > origNumbers.getStart(left));
		BitSet segmentsBefore = new BitSet();
		BitSet segmentsAfter = new BitSet();
		for (HousenumberMatch house : toSplit){
			List<HousenumberMatch> target;
			if (house.getHousenumber() < badNum){
				target = inc ? before : after;
			} else if (house.getHousenumber() > badNum){
				target = inc ? after : before;
			} else {
				int s = origNumbers.getStart(left);
				target = (s == badNum) ? before : after;
			}
			target.add(house);
			if (target == before){
				segmentsBefore.set(house.getSegment());
			} else {
				segmentsAfter.set(house.getSegment());
			}
		}
		if (before.isEmpty() || after.isEmpty())
			return this;
		if (log.isDebugEnabled())
			log.debug("todo: find best method to separate",before,"and",after);
		HousenumberMatch house1 = before.get(before.size() - 1);
		HousenumberMatch house2 = after.get(0);
		List<HousenumberMatch> testOrder = new ArrayList<>(); 
		testOrder.add(house1);
		testOrder.add(house2);
		Collections.sort(testOrder, new HousenumberMatchByPosComparator());
		if (testOrder.get(0) != house1){
			log.info("order indicates random case or missing road!",this);
			housenumberRoad.setRandom(true);
		}
		int splitSegment = -1; 
		if (house1.getSegment() != house2.getSegment()){
			// simple case: change point
			if (log.isDebugEnabled())
				log.debug("simple case: change point to number node between",house1,house2);
			// what point is best?, use beginning of 2nd for now 
			splitSegment = house2.getSegment();
			doSplit = true;
			
		} else {
			int seg = house1.getSegment();
			Coord c1 = getRoad().getPoints().get(seg);
			Coord c2 = getRoad().getPoints().get(seg + 1);
			double segmentLength = c1.distance(c2);

			Coord toAdd = null;
			boolean addOK = true;
			double wantedFraction = (house1.getSegmentFrac() + house2.getSegmentFrac()) / 2;
			// handle cases where perpendicular is not on the road
			if (wantedFraction <= 0){
				wantedFraction = 0;
				toAdd = new Coord(c1);
			} else if (wantedFraction >= 1){
				wantedFraction = 1;
				toAdd = new Coord(c2);
			} 
			double usedFraction = wantedFraction;

			if (toAdd == null) {
				Coord wanted = c1.makeBetweenPoint(c2, wantedFraction);
				toAdd = rasterLineNearPoint(c1, c2, wanted, true);
				if (toAdd != null){
					if (toAdd.equals(c1)){
						toAdd = new Coord(c1);
						usedFraction = 0.0;
					}
					else if (toAdd.equals(c2)){
						toAdd = new Coord(c2);
						usedFraction = 0;
					}
					else {
						addOK = checkLineDistortion(c1, c2, toAdd);
						if (addOK)
							usedFraction = HousenumberGenerator.getFrac(c1, c2, toAdd);
						else 
							toAdd = null;
					}
				}
			}
			if (toAdd == null){
				double len1 = wantedFraction * segmentLength;
				double len2 = (1 - wantedFraction) * segmentLength;
				if (Math.min(len1, len2) < MAX_LOCATE_ERROR){
					if (len1 < len2){
						toAdd = new Coord(c1);
						usedFraction = 0.0;
					} else {
						toAdd = new Coord(c2);
						usedFraction = 1.0;
					}
				}
			}
			if (toAdd == null){
				log.error("cannot split",this);
			}
			if (toAdd != null){
				if (log.isDebugEnabled())
					log.debug("solution: split segment with length",formatLen(segmentLength),"at",formatLen(usedFraction * segmentLength));
				doSplit = true;
				splitSegment = seg+1;
				addAsNumberNode(splitSegment, toAdd);
				this.endInRoad++;
				for (HousenumberMatch house : before){
					if (house.getSegment() >= seg){
						HousenumberGenerator.findClosestRoadSegment(house, getRoad(), seg, splitSegment);
					}
				}
				for (HousenumberMatch house : after){
					if (house.getSegment() < splitSegment)
						HousenumberGenerator.findClosestRoadSegment(house, getRoad(), splitSegment, splitSegment + 1);
					else 
						house.setSegment(house.getSegment()+1);
				}
				recalcHousePositions(getHouses(!left)); // the other side
			}
		}
		if (doSplit){
			getRoad().getPoints().get(splitSegment).setNumberNode(true);
			ExtNumbers en1 = split();
			ExtNumbers en2 = en1.next;
			if (housenumberRoad.isRandom() && endInRoad - startInRoad > 1){
				int leftUsed = en1.setNumbers(getHouses(Numbers.LEFT), startInRoad, splitSegment, true);
				en2.setNumbers(getHouses(Numbers.LEFT).subList(leftUsed,getHouses(Numbers.LEFT).size() ), splitSegment, endInRoad, true);
				int rightUsed = en1.setNumbers(getHouses(Numbers.RIGHT), startInRoad, splitSegment, false);
				en2.setNumbers(getHouses(Numbers.RIGHT).subList(rightUsed,getHouses(Numbers.RIGHT).size()), splitSegment, endInRoad, false);
			} else {
				if (left){
					en1.setNumbers(before, startInRoad, splitSegment, true);
					en2.setNumbers(after, splitSegment, endInRoad, true);
					int rightUsed = en1.setNumbers(getHouses(Numbers.RIGHT), startInRoad, splitSegment, false);
					en2.setNumbers(getHouses(Numbers.RIGHT).subList(rightUsed,getHouses(Numbers.RIGHT).size()), splitSegment, endInRoad, false);
				} else {
					int leftUsed = en1.setNumbers(getHouses(Numbers.LEFT), startInRoad, splitSegment, true);
					en2.setNumbers(getHouses(Numbers.LEFT).subList(leftUsed,getHouses(Numbers.LEFT).size() ), splitSegment, endInRoad, true);
					en1.setNumbers(before, startInRoad, splitSegment, false);
					en2.setNumbers(after, splitSegment, endInRoad, false);
				}
			}
			if (en1.getHouses(Numbers.LEFT).size() + en2.getHouses(Numbers.LEFT).size() != getHouses(Numbers.LEFT).size() ||
					en1.getHouses(Numbers.RIGHT).size() + en2.getHouses(Numbers.RIGHT).size() != getHouses(Numbers.RIGHT).size()){
				log.error("lost houses");
			}
			log.info("number node added in street",getRoad(),getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());		
			return en1;
		}
		return this;
	}
	
	private boolean checkLineDistortion(Coord c1, Coord c2, Coord toAdd){
		double distToLine = toAdd.getDisplayedCoord().distToLineSegment(c1.getDisplayedCoord(), c2.getDisplayedCoord());
		if (distToLine > 0.2){
			double angle = Utils.getDisplayedAngle(c1, toAdd, c2);
			if (Math.abs(angle) > 3){
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Try to add a number node.
	 * We may change an existing point to a number node or add a new 
	 * number node. A new node might be between the existing ones
	 * or a duplicate of one of them.
	 * @return
	 */
	private ExtNumbers tryAddNumberNode(int reason) {
		String action;
		if (endInRoad - startInRoad > 1)
			action = "change";
		else {
			if (getRoad().getPoints().size() + 1 > LineSplitterFilter.MAX_POINTS_IN_LINE){
				log.warn("can't change intervals, road has already",LineSplitterFilter.MAX_POINTS_IN_LINE,"points");
				return this; // can't add a node
			}
			Coord c1 = getRoad().getPoints().get(startInRoad);
			Coord c2 = getRoad().getPoints().get(startInRoad+1);
			if (c1.equals(c2)){
				return dupNode(0, SR_FIX_ERROR);
			} 
			double segmentLength = c1.distance(c2);
			int countAfterEnd = 0, countBeforeStart = 0;
			double minFraction0To1 = 2;
			double maxFraction0To1 = -1;
			for (List<HousenumberMatch> houses : Arrays.asList(getHouses(Numbers.LEFT), getHouses(Numbers.RIGHT))){
				for (HousenumberMatch house : houses){
					if (house.getSegmentFrac() < 0)
						++countBeforeStart;
					else if (house.getSegmentFrac() > 1)
						++countAfterEnd;
					else {
						if (minFraction0To1 > house.getSegmentFrac())
							minFraction0To1 = house.getSegmentFrac();
						if (maxFraction0To1 < house.getSegmentFrac())
							maxFraction0To1 = house.getSegmentFrac();
					}
				}
			}
			// special cases: perpendicular not on the road
			if (countBeforeStart > 0){
				return dupNode(0, SR_SPLIT_ROAD_END);
			}
			if (countAfterEnd > 0){
				return dupNode(1, SR_SPLIT_ROAD_END);
			}
			
			// try to find a good split point depending on the split reason
			double wantedFraction, midFraction;
			wantedFraction = midFraction = (minFraction0To1 + maxFraction0To1) / 2;
			Coord toAdd = null;  
			double len1 = segmentLength * minFraction0To1; // dist to first 
			double len2 = segmentLength * maxFraction0To1; 
			double len3 = (1-maxFraction0To1) * segmentLength;
			if (reason == SR_FIX_ERROR && worstHouse != null){
				wantedFraction = worstHouse.getSegmentFrac();
				if (wantedFraction < minFraction0To1 || wantedFraction > maxFraction0To1){
					log.error("internal error, worst house not found",this,worstHouse);
				}
			}
			boolean allowSplitBetween = true;
			if (reason == SR_OPT_LEN){
				if (log.isDebugEnabled()){
					if (maxFraction0To1 != minFraction0To1){
						log.debug("trying to find good split point, houses are between",formatLen(len1),"and",formatLen(len2),"in segment with",formatLen(segmentLength));
					} else 
						log.debug("trying to find good split point, houses are at",formatLen(len1),"in segment with",formatLen(segmentLength));
				}
				if (len2 - len1 < 10 && getHouses(Numbers.LEFT).size() <= 1 && getHouses(Numbers.RIGHT).size() <= 1){
					// one house or two opposite houses  
					// we try to split so that the house(s) are near the middle of one part
					wantedFraction = wantedFraction * 2 - (wantedFraction > 0.5 ? 1 : 0);
					if (wantedFraction == 0)
						wantedFraction = 5.0 / segmentLength;
					if (wantedFraction == 1)
						wantedFraction = (segmentLength - 5.0) / segmentLength;
					allowSplitBetween = false;
				} else {
					if (minFraction0To1 > 0.333){
						// create empty segment at start
						wantedFraction = Math.max(0, minFraction0To1 - (10.0 / segmentLength));  
					} 
					if (maxFraction0To1 < 0.666 && 1d - maxFraction0To1 > minFraction0To1){
						// create empty segment at end
						wantedFraction = Math.min(1, maxFraction0To1 + (10.0 / segmentLength));
					}
				}
			}
			double partLen = wantedFraction * segmentLength ;
			double shorterLen = Math.min(partLen , segmentLength - partLen);
			if (shorterLen < 10){
				if (reason == SR_FIX_ERROR && minFraction0To1 == maxFraction0To1)
					return dupNode(midFraction, SR_FIX_ERROR);
				double splitFrac = len1 < len3 ? minFraction0To1 : maxFraction0To1;
				return dupNode(splitFrac, SR_OPT_LEN);
			}
			double usedFraction = 0;
			double bestDist = Double.MAX_VALUE;
			for (;;){
				Coord wanted = c1.makeBetweenPoint(c2, wantedFraction);
				toAdd = rasterLineNearPoint(c1, c2, wanted, false);
				if (toAdd == null){
					if (wantedFraction != 0.5){
						wantedFraction = 0.5;
						continue;
					}
					break;
				}
				double foundDist = toAdd.distance(wanted);
				usedFraction = HousenumberGenerator.getFrac(c1, c2, toAdd);
//				log.debug(reason, minFraction0To1, maxFraction0To1, wantedFraction, usedFraction, foundDist );
				if (wantedFraction == 0.5)
					break;
				if (foundDist > 10 || usedFraction > minFraction0To1 && wantedFraction < minFraction0To1 || usedFraction < maxFraction0To1 && wantedFraction > maxFraction0To1){
					wantedFraction = 0.5;
				} else if (allowSplitBetween == false && usedFraction > minFraction0To1 && usedFraction < maxFraction0To1){
					wantedFraction = 0.5;
				} else 
					break;
			}
			
			boolean addOK = true;
			if (toAdd == null)
				addOK = false;
			else {
				toAdd.incHighwayCount();
				bestDist = toAdd.getDisplayedCoord().distToLineSegment(c1.getDisplayedCoord(), c2.getDisplayedCoord());
				if (log.isDebugEnabled()){
					log.debug("trying to split road segment",startInRoad,"at",formatLen(usedFraction * segmentLength));
				}
				if (bestDist > 0.2){
					double angle = Utils.getDisplayedAngle(c1, toAdd, c2);
					if (Math.abs(angle) > 3){
						if (log.isDebugEnabled())
							log.debug("segment too short to split without creating visible angle");
						addOK = false;
					}
				}
			}
			if (!addOK){
				if (reason == SR_FIX_ERROR && minFraction0To1 == maxFraction0To1)
					return dupNode(midFraction, SR_FIX_ERROR);
				if (Math.min(len1, len3) < MAX_LOCATE_ERROR ){
					double splitFrac = (minFraction0To1 != maxFraction0To1) ? midFraction : minFraction0To1;
					return dupNode(splitFrac, SR_OPT_LEN);
				}
				if(reason == SR_FIX_ERROR)
					log.warn("can't fix error in interval",this);
				else if (log.isDebugEnabled())
					log.debug("can't improve search result",this);
				return this;
			}
			if (log.isInfoEnabled())
				log.info("adding number node at",toAdd.toDegreeString(),"to split, dist to line is",formatLen(bestDist));
			action = "add";
			this.endInRoad = addAsNumberNode(startInRoad + 1, toAdd);
			this.recalcHousePositions(getHouses(Numbers.LEFT));
			this.recalcHousePositions(getHouses(Numbers.RIGHT));
		}
		int splitSegment = (startInRoad + endInRoad) / 2;
		if (worstHouse != null){
			if (worstHouse.getSegment() == startInRoad)
				splitSegment = startInRoad + 1;
			else if (worstHouse.getSegment() == endInRoad - 1)
				splitSegment = worstHouse.getSegment();
		} else if (endInRoad - startInRoad > 2){
			int firstSegWithHouses = endInRoad;
			int lastSegWithHouses = -1;
			for (List<HousenumberMatch> houses : Arrays.asList(getHouses(Numbers.LEFT),getHouses(Numbers.RIGHT))){
				for (HousenumberMatch house : houses){
					int s = house.getSegment();
					if (s < firstSegWithHouses)
						firstSegWithHouses = s;
					if (s > lastSegWithHouses)
						lastSegWithHouses = s;
				}
					
			}
			splitSegment = (firstSegWithHouses + lastSegWithHouses) / 2;
			if (splitSegment == startInRoad)
				splitSegment++;
		}
		getRoad().getPoints().get(splitSegment).setNumberNode(true);
		ExtNumbers en1 = split();
		ExtNumbers en2 = en1.next;
		int leftUsed = en1.setNumbers(getHouses(Numbers.LEFT), startInRoad, splitSegment, true);
		int rightUsed = en1.setNumbers(getHouses(Numbers.RIGHT), startInRoad, splitSegment, false);
		getHouses(Numbers.LEFT).subList(0, leftUsed).clear();
		getHouses(Numbers.RIGHT).subList(0, rightUsed).clear(); 				
		leftUsed = en2.setNumbers(getHouses(Numbers.LEFT), splitSegment, endInRoad, true);
		rightUsed = en2.setNumbers(getHouses(Numbers.RIGHT), splitSegment, endInRoad, false);
		if (reason == SR_OPT_LEN){
			// TODO: fill gaps, e.g. if split results in O,1,9 -> O,1,1 + O,9,9 ?
		}
		if ("add".equals(action))
			log.info("number node added in street",getRoad(),getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		else 
			log.info("point changed to number node in street",getRoad(),getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		return en1;
	}


	/**
	 * Duplicate a node in the road. This creates a zero-length segment.
	 * We can add house numbers to this segment and the position in the 
	 * address search will be the same for all of them.
	 * @param fraction a value below 0.5 means duplicate the start node, others 
	 * duplicate the end node 
	 * @return the new chain with two segments 
	 */
	private ExtNumbers dupNode(double fraction, int reason) {
		log.info("duplicating number node in road",getRoad(),getNumbers(),getHouses(Numbers.LEFT),getHouses(Numbers.RIGHT));
		boolean atStart = (fraction <= 0.5);
		
		// add a copy of an existing node 
		int index = (atStart) ? startInRoad : endInRoad;
		int splitSegment = (atStart) ? startInRoad + 1: endInRoad;
		Coord closePoint = getRoad().getPoints().get(index);
		Coord toAdd = new Coord(closePoint);
		toAdd.setOnBoundary(closePoint.getOnBoundary());
		toAdd.incHighwayCount();
		// we have to make sure that the road starts and ends with a CoordNode!
		this.endInRoad = addAsNumberNode(splitSegment, toAdd);
		
		// distribute the houses to the new intervals
		List<ArrayList<HousenumberMatch>> leftTargets = Arrays.asList(new ArrayList<HousenumberMatch>(),new ArrayList<HousenumberMatch>());
		List<ArrayList<HousenumberMatch>> rightTargets = Arrays.asList(new ArrayList<HousenumberMatch>(),new ArrayList<HousenumberMatch>());
		int target;
		if (reason == SR_SPLIT_ROAD_END || reason == SR_OPT_LEN){
			for (int side = 0; side < 2; side++){
				boolean left = side == 0;
				List<ArrayList<HousenumberMatch>> targets = left ? leftTargets : rightTargets;
				for (HousenumberMatch house : getHouses(left)){
					if (house.getSegmentFrac() < fraction)
						target = 0;
					else if (house.getSegmentFrac() > fraction)
						target = 1;
					else target = (atStart) ? 0 : 1;
					targets.get(target).add(house);
				}
			}
		} else if (getHouses(Numbers.LEFT).size() > 1 || getHouses(Numbers.RIGHT).size() > 1){
			int start,end;
			for (int side = 0; side < 2; side++){
				boolean left = side == 0;
				if (getHouses(left).isEmpty())
					continue;
				start = getNumbers().getStart(left);
				end = getNumbers().getEnd(left);
				List<ArrayList<HousenumberMatch>> targets = left ? leftTargets : rightTargets;
				if (start != end){
					int midNum = (start + end) / 2;
					for (HousenumberMatch house : getHouses(left)){
						if (house.getHousenumber() < midNum)
							target = 0;
						else if (house.getHousenumber() > midNum)
							target = 1;
						else target = (atStart) ? 0 : 1;
						targets.get(target).add(house);
					}
				} else if (multipleZipOrCity(left) == false){
					if (atStart) 
						targets.get(1).addAll(getHouses(left));
					else 
						targets.get(0).addAll(getHouses(left));
				} else {
					int mid = getHouses(left).size() / 2;
					targets.get(0).addAll(getHouses(left).subList(0, mid));
					targets.get(1).addAll(getHouses(left).subList(mid,getHouses(left).size()));
				}
				
			}
		} else {
			log.error("don't know how to split", this); 
		}
		
		assert splitSegment != startInRoad && splitSegment != endInRoad;
		setSegment(startInRoad, leftTargets.get(0));
		setSegment(startInRoad, rightTargets.get(0));
		setSegment(splitSegment, leftTargets.get(1));
		setSegment(splitSegment, rightTargets.get(1));
		ExtNumbers en1 = split();
		ExtNumbers en2 = en1.next;
		en1.setNumbers(leftTargets.get(0), startInRoad, splitSegment, true);
		en1.setNumbers(rightTargets.get(0), startInRoad, splitSegment, false);
		en2.setNumbers(leftTargets.get(1), splitSegment, endInRoad, true);
		en2.setNumbers(rightTargets.get(1), splitSegment, endInRoad, false);
		log.info("zero length interval added in street",getRoad(),getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		if (atStart && !en1.hasNumbers() ||  !atStart && !en2.hasNumbers()){
			log.error("zero length interval has no numbers in road",getRoad());
		}
		return en1;	
		
	}

	private void setSegment(int segment, List<HousenumberMatch> houses) {
		for (HousenumberMatch house : houses){
			HousenumberGenerator.findClosestRoadSegment(house, getRoad(), segment,segment+1);
			if (house.getRoad() == null || house.getSegment() != segment){
				// should not happen
				log.error("internal error, house too far from forced segment in road",getRoad(),house,house.getElement().toBrowseURL());
				house.setIgnored(true);
			}
		}
		
	}

	/**
	 * This should be called if a point was added to the road segment
	 * covered by this interval. We have to recalculate the segment numbers
	 * and fraction values.
	 */
	private void recalcHousePositions(List<HousenumberMatch> houses){
		for (HousenumberMatch house : houses){
			HousenumberGenerator.findClosestRoadSegment(house, getRoad(), startInRoad, endInRoad);
		}
		Collections.sort(houses, new HousenumberMatchByPosComparator());
	}
	
	
	/**
	 * Create two empty intervals which will replace this.
	 * @return
	 */
	private ExtNumbers split(){
		ExtNumbers en1 = new ExtNumbers(housenumberRoad);
		ExtNumbers en2 = new ExtNumbers(housenumberRoad);
		// maintain the linked list
		en1.prev = this.prev;
		if (prev != null)
			prev.next = en1;
		en1.next = en2;
		en2.prev = en1;
		en2.next = this.next;
		if (this.next != null)
			next.prev = en2;
		en1.setNodeIndex(nodeIndex);
		en2.setNodeIndex(nodeIndex+1);
		ExtNumbers work = en2.next;
		while (work != null){
			work.setNodeIndex(work.nodeIndex + 1);
			work = work.next;
		}
		return en1;
	}
	
	/**
	 * Add node to the road.
	 * Maintain the positions of houses in the following intervals.
	 * @param toAdd 
	 * @param pos 
	 * @return new start of next interval
	 */
	private int addAsNumberNode(int pos, Coord toAdd){
		toAdd.setNumberNode(true);
		getRoad().getPoints().add(pos, toAdd);
		
		ExtNumbers work = next;
		while (work != null){
			work.increaseNodeIndexes(startInRoad); 			
			work = work.next;
		}
		return endInRoad + 1;
	}
	
	/**
	 * This has to be called when a point is added to the road.
	 * @param startPos
	 */
	private void increaseNodeIndexes(int startPos){
		if (hasNumbers() == false)
			return;
		if (startInRoad > startPos){
			startInRoad++;
			endInRoad++;
		}
		for (List<HousenumberMatch> list: Arrays.asList(getHouses(Numbers.LEFT), getHouses(Numbers.RIGHT))){
			for (HousenumberMatch house : list){
				int s = house.getSegment();
				if (s > startPos)
					house.setSegment(s+1);
				else 
					assert false : "internal error " + getRoad() + " " + getHouses(Numbers.LEFT) + " " + getHouses(Numbers.RIGHT);
			}
		}
		
	}
	
	
	private void findGoodSplitPos(){
		badNum = -1;
		worstHouse = null;
		boolean multipleZipOrCity = false;
		for (int side = 0; side < 2; side++){
			boolean left = side == 0;
			List<HousenumberMatch> houses = left ? getHouses(Numbers.LEFT) : getHouses(Numbers.RIGHT);
			if (houses.size() <= 1)
				continue;
			if (multipleZipOrCity(left))
				multipleZipOrCity = true;
			for (HousenumberMatch house: houses){
				int hn = house.getHousenumber();
				if (countOccurence(houses, hn) > 1)
					continue;
				ExtNumbers modIvl = simulateRemovalOfHouseNumber(hn, left);
				if (modIvl.isPlausible()){
					badNum = hn;
					if (log.isDebugEnabled())
						log.debug("splitpos details: single remove of",badNum,"results in plausible interval");
					return;
				}
			}
		}
		if (multipleZipOrCity)
			return; // unlikely
//		log.debug("did not yet find good split position");
		Numbers ivl = getNumbers();
		int[] firstBad = {-1,-1};
		int[] lastBad = {-1,-1};
		for (int side = 0; side < 2; side++){
			boolean left = (side == 0);
			int step = 2;
			if (ivl.getNumberStyle(left) == NumberStyle.BOTH)
				step = 1;
			int s = ivl.getStart(left);
			int e = ivl.getEnd(left);
			int s2 = ivl.getStart(!left);
			int e2 = ivl.getEnd(!left );
			NumberStyle style2 = ivl.getNumberStyle(!left); 
			for (int hn = Math.min(s, e); hn <= Math.max(s, e); hn += step){
				if (style2 == NumberStyle.EVEN && hn % 2 == 1 || style2 == NumberStyle.ODD && hn % 2 == 0 ){
					if (firstBad[side] < 0)
						firstBad[side] = hn;
					lastBad[side] = hn;
					continue;
				}
				if (hn < Math.min(s2, e2) || hn > Math.max(s2, e2)){
					if (firstBad[side] < 0)
						firstBad[side] = hn;
					lastBad[side] = hn;
				}
			}
		}
		if (firstBad[0] == lastBad[0]){
			badNum = firstBad[0];
			if (badNum >= 0)
				return;
		}
		if (firstBad[1] == lastBad[1]){
			badNum = firstBad[1];
			if (badNum >= 0)
				return;
		}
		badNum = Math.max(firstBad[0], lastBad[0]);
		if (badNum == -1)
			badNum = Math.min(firstBad[1], lastBad[1]);
		if (log.isDebugEnabled())
			log.debug("splitpos details",Arrays.toString(firstBad), Arrays.toString(lastBad),"gives badNum",badNum);
	}
	
	private boolean multipleZipOrCity(boolean left) {
		RoadSide rs = left ? leftSide : rightSide;
		return rs == null ? false : rs.multipleCities || rs.multipleZipCodes;
	}

	public ExtNumbers checkChainPlausibility(String streetName,
			List<HousenumberMatch> potentialNumbersThisRoad) {
		// we try to repair up to 10 times
		ExtNumbers head = this;
		for (int loop = 0; loop < 10; loop++){
			boolean anyChanges = false;
			for (ExtNumbers en1 = head; en1 != null; en1 = en1.next){
				if (anyChanges)
					break;
				if (en1.hasNumbers() == false)
					continue;
				for (ExtNumbers en2 = en1.next; en2 != null; en2 = en2.next){
					if (anyChanges)
						break;
					if (en2.hasNumbers() == false)
						continue;
					
					int res = checkIntervals(streetName, en1, en2);
					switch (res) {
					case OK_NO_CHANGES:
					case NOT_OK_KEEP:
						break;
					case OK_AFTER_CHANGES:
						anyChanges = true;
						break;
					case NOT_OK_TRY_SPLIT:
						if (en1.needsSplit){
							ExtNumbers test = en1.tryChange(SR_FIX_ERROR);
							if (test != en1){
								housenumberRoad.setChanged(true);
								anyChanges = true;
								if (test.prev == null){
									head = test;
								}
							}
						}
						if (en2.needsSplit){
							ExtNumbers test = en2.tryChange(SR_FIX_ERROR);
							if (test != en2){
								anyChanges = true;
								housenumberRoad.setChanged(true);
							}
						}
						break;
					case NOT_OK_STOP:
						return head;
					default:
						break;
					}
				}
			}
			if (!anyChanges)
				break;
		}
		return head;
	}
	
	public static final int OK_NO_CHANGES = 0;
	public static final int OK_AFTER_CHANGES = 1;
	public static final int NOT_OK_TRY_SPLIT = 2;
	public static final int NOT_OK_KEEP  = 3;
	public static final int NOT_OK_STOP = 4;

	/**
	 * Check if two intervals are overlapping  (all combinations of left + right)
	 * @param streetName
	 * @param en1
	 * @param en2
	 * @return true if something was changed
	 */
	public static int checkIntervals(String streetName, ExtNumbers en1, ExtNumbers en2) {
		if (en1.getRoad() != en2.getRoad()){
			Coord cs1 = en1.getRoad().getPoints().get(en1.startInRoad);
			Coord ce1 = en1.getRoad().getPoints().get(en1.endInRoad);
			Coord ce2 = en2.getRoad().getPoints().get(en2.endInRoad);
			if (ce2 == cs1 || ce2 == ce1){
				ExtNumbers help = en1;
				en1 = en2;
				en2 = help;
			}
		}
		boolean allOK = true;
		Numbers ivl1 = en1.getNumbers();
		Numbers ivl2 = en2.getNumbers();
		for (int i = 0; i < 2; i++){
			boolean left1 = i == 0;
			NumberStyle style1 = ivl1.getNumberStyle(left1);
			if (style1 == NumberStyle.NONE)
				continue;
			int s1 = ivl1.getStart(left1);
			int e1 = ivl1.getEnd(left1);
			for (int j = 0; j < 2; j++){
				boolean left2 = (j == 0);
				NumberStyle style2 = ivl2.getNumberStyle(left2);
				if (style2 == NumberStyle.NONE)
					continue;
				int s2 = ivl2.getStart(left2);
				int e2 = ivl2.getEnd(left2);
				boolean ok = true;
				if (style1 == style2 || style1 == NumberStyle.BOTH || style2 == NumberStyle.BOTH)
					ok = checkIntervalBoundaries(s1, e1, s2, e2, left1 == left2 && en1.getRoad() == en2.getRoad());
				if (ok) 
					continue;
				if (en1.getRoad() != en2.getRoad() && en1.hasGaps == false && en2.hasGaps == false){
					allOK = false;
					continue;
				}

				if (s1 == e1){
					if (left1 && en1.getHouses(Numbers.LEFT).get(0).isFarDuplicate() || !left1 && en1.getHouses(Numbers.RIGHT).get(0).isFarDuplicate()){
						allOK = false;
						continue;
					}
				}
				if (s2 == e2){
					if (left2 && en2.getHouses(Numbers.LEFT).get(0).isFarDuplicate() || !left2 && en2.getHouses(Numbers.RIGHT).get(0).isFarDuplicate()){
						allOK = false;
						continue;
					}
				}
				List<HousenumberMatch> houses1 = left1 ? en1.getHouses(Numbers.LEFT) : en1.getHouses(Numbers.RIGHT);
				List<HousenumberMatch> houses2 = left2 ? en2.getHouses(Numbers.LEFT) : en2.getHouses(Numbers.RIGHT);
				if (log.isInfoEnabled()){
					log.info("detected unplausible combination of intervals in",streetName, 
							s1 + ".." + e1, "and", s2 + ".." + e2, "houses:",
							(left1 ? "left:" : "right"), houses1, 
							(left2 ? "left:" : "right"), houses2,
							(en1.getRoad() == en2.getRoad() ? "in road " + en1.getRoad() :  
								"road id(s):" + en1.getRoad().getRoadDef().getId() + ", " + en2.getRoad().getRoadDef().getId()));
				}
				double smallestDelta = Double.POSITIVE_INFINITY;
				HousenumberMatch bestMoveOrig = null;
				HousenumberMatch bestMoveMod = null;
				ExtNumbers bestRemove = null;
				List<HousenumberMatch> possibleRemoves1 = new ArrayList<>(); 
				List<HousenumberMatch> possibleRemoves2 = new ArrayList<>();
				
				if (en1.housenumberRoad.isRandom() == false && en2.housenumberRoad.isRandom() == false){
					// check if we can move a house from en1 to en2
					for (HousenumberMatch house : houses1){
						if (house.getGroup() != null)
							continue;
						int n = house.getHousenumber();
						if (countOccurence(houses1, n) > 1)
							continue;

						if (n == s1 || n == e1) {
							Numbers modNumbers = en1.simulateRemovalOfHouseNumber(n, left1).getNumbers();
							int s1Mod = modNumbers.getStart(left1);
							int e1Mod = modNumbers.getEnd(left1);
							NumberStyle modStyle = modNumbers.getNumberStyle(left1);
							boolean ok2 = true;
							if (modStyle == style2 || modStyle == NumberStyle.BOTH || style2 == NumberStyle.BOTH)
								ok2 = checkIntervalBoundaries(s1Mod, e1Mod, s2, e2, left1 == left2 && en1.getRoad() == en2.getRoad());
							if (ok2){
								// the intervals don't overlap if house is removed from en1
								if (houses1.size() > 1)
									possibleRemoves1.add(house);
								
								// check if it fits into en2
								HousenumberMatch test = checkMoveTo(house, en2, left2);
								if (test.getRoad() != null){
									double deltaDist = test.getDistance() - house.getDistance(); 
									if (deltaDist < smallestDelta){
										bestMoveMod = test;
										bestMoveOrig = house;
										smallestDelta = deltaDist;
										bestRemove = en1;
									}
								} 
							}
						}
					}
					for (HousenumberMatch house : houses2){
						if (house.getGroup() != null)
							continue;
						int n = house.getHousenumber();
						if (countOccurence(houses2, n) > 1)
							continue;

						if (n == s2 || n == e2) {
							Numbers modNumbers = en2.simulateRemovalOfHouseNumber(n, left2).getNumbers();
							int s2Mod = modNumbers.getStart(left2);
							int e2Mod = modNumbers.getEnd(left2);
							NumberStyle modStyle = modNumbers.getNumberStyle(left2);
							boolean ok2 = true;
							if (modStyle == style1 || modStyle == NumberStyle.BOTH || style1 == NumberStyle.BOTH)
								ok2 = checkIntervalBoundaries(s1, e1, s2Mod, e2Mod, left1 == left2 && en1.getRoad() == en2.getRoad());
							if (ok2){
								// the intervals don't overlap if house is removed from en2
								if (houses2.size() > 1)
									possibleRemoves2.add(house);
								
								
								// check if it fits into en1
								HousenumberMatch test = checkMoveTo(house, en1, left1);
								if (test.getRoad() != null){
									double deltaDist = test.getDistance() - house.getDistance(); 
									if (deltaDist < smallestDelta){
										bestMoveMod = test;
										bestMoveOrig = house;
										smallestDelta = deltaDist;
										bestRemove = en2;
									}
								} 								
							}
						}
					}
					if (bestMoveMod != null){
						if (bestMoveOrig.isDuplicate()){
							log.warn("duplicate number causes problems",streetName,bestMoveOrig.getSign(),bestMoveOrig.getElement().toBrowseURL() );
						}
						List<HousenumberMatch> fromHouses, toHouses;
						ExtNumbers from,to;
						if (bestRemove == en1){
							from = en1;
							to = en2;
							fromHouses = houses1;
							toHouses = houses2;
							bestMoveOrig.setLeft(left2);

						} else {
							from = en2;
							to = en1;
							fromHouses = houses2;
							toHouses = houses1;
							bestMoveOrig.setLeft(left1);
						}
						if (bestMoveOrig.getMoved() >= 3){
							bestMoveMod = null;
							bestMoveOrig = null;
							bestRemove.housenumberRoad.setRandom(true);
						} else {
							if (log.isInfoEnabled()){
								if (to.getRoad() == from.getRoad())
									log.info("moving",streetName,bestMoveOrig.getSign(),bestMoveOrig.getElement().toBrowseURL(),"from",fromHouses,"to",toHouses,"in road",to.getRoad());
								else 
									log.info("moving",streetName,bestMoveOrig.getSign(),bestMoveOrig.getElement().toBrowseURL(),"from",fromHouses,"in road",from.getRoad(),"to",toHouses,"in road",to.getRoad());
							}
							bestMoveOrig.incMoved();
							bestMoveOrig.setRoad(to.getRoad());
							bestMoveOrig.setHousenumberRoad(to.housenumberRoad);
							bestMoveOrig.setSegment(bestMoveMod.getSegment());
							bestMoveOrig.setDistance(bestMoveMod.getDistance());
							bestMoveOrig.setSegmentFrac(bestMoveMod.getSegmentFrac());
							fromHouses.remove(bestMoveOrig);
							toHouses.add(bestMoveOrig);
							Collections.sort(toHouses, new HousenumberMatchByPosComparator());
							en1.reset();
							en2.reset();
							en1.setNumbers(houses1, en1.startInRoad, en1.endInRoad, left1);
							en2.setNumbers(houses2, en2.startInRoad, en2.endInRoad, left2);
							return OK_AFTER_CHANGES;
						}
					} 
				}
				ExtNumbers toSplit = null;
				int splitNum = -1;
				int delta1 = Math.abs(e1-s1);
				int delta2 = Math.abs(e2-s2);
				if (delta1 > 0 && delta2 > 0){
					if (en1.hasGaps != en2.hasGaps){
						if (en1.hasGaps){
							if (possibleRemoves1.isEmpty() == false)
								splitNum = possibleRemoves1.get(0).getHousenumber();
							toSplit = en1;
						} else {
							if (possibleRemoves2.isEmpty() == false)
								splitNum = possibleRemoves2.get(0).getHousenumber();
							toSplit = en2;
						}
					} else if (possibleRemoves1.size() == 1){
						splitNum = possibleRemoves1.get(0).getHousenumber();
						toSplit = en1;
					} else if (possibleRemoves2.size() == 1){
						splitNum = possibleRemoves2.get(0).getHousenumber();
						toSplit = en2;
					} else if (possibleRemoves1.size() > 0){
						splitNum = possibleRemoves1.get(0).getHousenumber();
						toSplit = en1;
					} else if (possibleRemoves2.size() > 0){
						splitNum = possibleRemoves2.get(0).getHousenumber();
						toSplit = en2;
					} else {
						// intervals are overlapping, a single remove doesn't help
						if (ivl1.isContained(s2, left1) && ivl1.isContained(e2, left1)){
							// en2 is completely in en1
							toSplit = en1;
							splitNum = s2;
						} else if (ivl2.isContained(s1, left2) && ivl2.isContained(e1, left2)){
							// en1 is completely in en2
							toSplit = en2;
							splitNum = s1;
						} 
						else {
							if (ivl1.isContained(s2, left1)){
								toSplit = en1;
								splitNum = s2;
							} else if (ivl1.isContained(e2, left1)){
								toSplit = en1;
								splitNum = e2;
							} else if (ivl2.isContained(s1, left2)){
								toSplit = en2;
								splitNum = s1;
							} else if (ivl2.isContained(e1, left2)){
								toSplit = en2;
								splitNum = e1;
							} else if (style1 == NumberStyle.BOTH){
								toSplit = en1;
							} else if (style2 == NumberStyle.BOTH){
								toSplit = en2;
							} else {
								toSplit = (delta1 >= delta2) ? en1 : en2;
							}
						}
					}
				}
				else if (delta1 == 0 && delta2 > 0 && countOccurence(houses2, s1) == 0){
					toSplit = en2;
					splitNum = s1;
				}
				else if (delta2 == 0 && delta1 > 0 && countOccurence(houses1, s2) == 0){ 
					toSplit = en1;
					splitNum = s2;
				}
				if (toSplit != null){
					toSplit.worstHouse = null;
					toSplit.badNum = splitNum;
					toSplit.setNeedsSplit(true);
					return NOT_OK_TRY_SPLIT;
					
				}
				allOK = false;
			}
		}
		if (allOK)
			return OK_NO_CHANGES;
		return NOT_OK_KEEP;
	}

	
	private static HousenumberMatch checkMoveTo(HousenumberMatch house, ExtNumbers other, boolean otherLeft){
		HousenumberMatch test = new HousenumberMatch(house);
		Numbers otherIvl = other.getNumbers();
		int oStart = otherIvl.getStart(otherLeft);
		int oEnd = otherIvl.getEnd(otherLeft);
		// check if it fits into en2
		if (house.getHousenumber() <= Math.min(oStart, oEnd) ||house.getHousenumber() >= Math.max(oStart, oEnd))
			return test;
		boolean even = house.getHousenumber() % 2 == 0;
		NumberStyle oStyle = otherIvl.getNumberStyle(otherLeft);
		if (oStyle == NumberStyle.EVEN && !even || oStyle == NumberStyle.ODD && even)
			return test;
		HousenumberGenerator.findClosestRoadSegment(test, other.getRoad(), other.startInRoad, other.endInRoad);
		
		if (test.getDistance() <= HousenumberGenerator.MAX_DISTANCE_TO_ROAD){
			Coord c1 = other.getRoad().getPoints().get(test.getSegment());
			Coord c2 = other.getRoad().getPoints().get(test.getSegment() + 1);
			if (c1.highPrecEquals(c2) || otherLeft == HousenumberGenerator.isLeft(c1, c2, house.getLocation())){
				test.setLeft(otherLeft);
				return test;
			}
		}
		test.setRoad(null);
		return test;
	} 	
	
	/**
	 * Check the start and end values of two consecutive number intervals
	 * for plausibility.
	 * @param s1 1st interval start
	 * @param e1 1st interval end
	 * @param s2 2nd interval start
	 * @param e2 2nd interval end
	 * @return
	 */
	private static boolean checkIntervalBoundaries(int s1, int e1, int s2,int e2, boolean sameSide){
		boolean ok = false;
		// many cases, maybe someone finds simpler code?
		if (sameSide){
			// allow equal numbers at boundaries
			if (s1 == e1) {
				if (e1 == s2) ok = true; // 6 6 6 4 , 6 6 6 6 , 6 6 6 8 
				else if (e1 < s2 && e1 < e2) ok = true; // 4 4 8 6, 4 4 6 8 1st equal, not in higher 2nd 
				else if (e1 > s2 && e1 > e2) ok = true; // 6 6 4 2, 6 6 2 4 1st equal, not in lower 2nd
			} else if (s1 < e1){
				if (e1 <= s2 && s2 <= e2) ok = true; // 6 8 8 8, 6 8 8 10, 6 8 10 10, 6 8 10 12 up
				else if (s2 > e2 && e1 < e2) ok = true; // 6 8 12 10 up down, no overlap, 2nd is higher   
				else if (s1 > s2 && s1 > e2) ok = true; // 6 8 4 4, 6 8 4 2, 6 8 2 4 up down, 2nd is lower
			} else { // s1 > e1
				if (e1 >= s2 && s2 >= e2) ok = true;  // 8 6 6 6, 8 6 6 4, 8 6 4 4, 8 6 4 2 down
				else if (e1 > s2 && e1 > e2) ok = true;  // 8 6 2 4 down up, no overlap,2nd is lower 
				else if (s1 < s2 && s1 < e2) ok = true;  // 8 6 10 10, 8 6 10 12, 8 6 12 10, 1st down, no overlap,2nd is higher  
			}
		} else {
			// left and right: don't allow equal numbers in different intervals
			if (s1 == e1) {
				if (s2 == e2 && s1 != s2) ok = true; // 6 6 2 2, 6 6 8 8  
				else if (s2 < e2 && (s1 < s2 || s1 > e2)) ok = true; // 6 6 2 4  , 6 6 8 10
				else if (s2 > e2 && (s1 > s2 || s1 < e2)) ok = true; // 6 6 4 2, 6 6 10 8
			} else if (s1 < e1){
				if (e1 < s2 && s2 <= e2) ok = true; // 6 8 10 10, 6 8 10 12 up
				else if (s2 > e2 && e1 < e2) ok = true; // 6 8 12 10 up down, no overlap, 2nd is higher   
				else if (s1 > s2 && s1 > e2) ok = true; // 6 8 4 4, 6 8 4 2, 6 8 2 4 up down, 2nd is lower
			} else { // s1 > e1
				if (e1 > s2 && s2 >= e2) ok = true;  // 8 6 4 4, 8 6 4 2 down
				else if (e1 > s2 && e1 > e2) ok = true;  // 8 6 2 4 down up, no overlap,2nd is lower 
				else if (s1 < s2 && s1 < e2) ok = true;  // 8 6 10 10, 8 6 10 12, 8 6 12 10, 1st down, no overlap,2nd is higher  
			}
		}
		if (!ok){
//			log.error("interval check not ok: ", s1,e1,s2,e2,"same side:",sameSide);
		}
		return ok;
	}
	
	private ExtNumbers simulateRemovalOfHouseNumber(int hn, boolean left){
		ExtNumbers help = new ExtNumbers(housenumberRoad);
		help.prev = prev;
		help.next = next;
		
		List<HousenumberMatch> modifiedHouses = new ArrayList<>(left ? getHouses(Numbers.LEFT) : getHouses(Numbers.RIGHT));
		Iterator<HousenumberMatch> iter = modifiedHouses.iterator();
		while (iter.hasNext()){
			HousenumberMatch house = iter.next();
			if (house.getHousenumber() == hn)
				iter.remove();
		}
		help.setNumbers(modifiedHouses, startInRoad, endInRoad, left);
		help.setNumbers(left ? getHouses(Numbers.RIGHT) : getHouses(Numbers.LEFT), startInRoad, endInRoad, !left);
		return help;
	}

	public boolean hasNumbers(){
		return getNumbers().isEmpty() == false;
	}
	
	public String toString(){
		return getRoad().toString() + getHouses(Numbers.LEFT).toString() + getHouses(Numbers.RIGHT).toString();
	}

	/**
	 * Try to add node(s) to decrease the distance of the calculated
	 * position of an address.
	 * @return 
	 */
	public ExtNumbers splitLargeGaps(){
		if (hasNumbers() == false)
			return this;
		// calculate the length of each road segment and 
		// the overall length covered by this interval
		int numSegments = endInRoad - startInRoad;
		double[] segmentLenghts = new double[numSegments];
		double fullLength = 0;
		for (int i = startInRoad; i < endInRoad; i++){
			Coord c1 = getRoad().getPoints().get(i);
			Coord c2 = getRoad().getPoints().get(i+1);
			double len = c1.distance(c2);
			segmentLenghts[i-startInRoad] = len;
			fullLength += len;
		}
		if (fullLength < MAX_LOCATE_ERROR){
			if (log.isDebugEnabled())
				log.debug("segment",this.getNumbers(), "with length",formatLen(fullLength),"is considered OK");
			
			return this;
		}
		TreeMap<Integer, Double> searchPositions = new TreeMap<>();
		boolean ok = calcSearchPositions(fullLength, searchPositions);
		
		if (!ok)
			return this;
		
		double worstDelta = 0;
		worstHouse = null;
		for (int side = 0; side < 2; side++){
			List<HousenumberMatch> houses = (side == 0) ? getHouses(Numbers.LEFT) : getHouses(Numbers.RIGHT);
			for (int i = 0; i < houses.size(); i++){
				HousenumberMatch house = houses.get(i);
				double distToStart = 0;
				for (int k = startInRoad; k < house.getSegment(); k++)
					distToStart += segmentLenghts[k-startInRoad];
				if (house.getSegmentFrac() > 0){
					try {
						distToStart += Math.min(1, house.getSegmentFrac()) * segmentLenghts[house.getSegment() - startInRoad];		
					} catch (Exception e) {
						log.error(e);
					}
				
				}
				Double searchDist = searchPositions.get(house.getHousenumber());
				if (searchDist == null){
					log.warn("can't compute address search result of",house);
				} else {
					double delta = distToStart - searchDist;
					house.setSearchDist(delta);
					if (Math.abs(delta) > worstDelta){
						worstDelta = Math.abs(delta);
						worstHouse = house;
					}
				}
			}
		}
		if (worstDelta > MAX_LOCATE_ERROR){
			if (log.isInfoEnabled())
				log.info("trying to optimize address search for house number in road",getRoad(),worstHouse,"error before opt is",formatLen(worstDelta));
			return tryChange(SR_OPT_LEN);
		}
		if (log.isDebugEnabled())
			log.debug("segment",this.getNumbers(), "with length",formatLen(fullLength),"is OK, worst address search for house number in road",getRoad(),worstHouse,"error is",formatLen(worstDelta));
		return this;
	}

	/**
	 * Try to simulate a Garmin address search for each number covered by the interval. 
	 * @param fullLength 
	 * @param searchPositions filled by this routine
	 * @return false if calculation failed
	 */
	private boolean calcSearchPositions(double fullLength, TreeMap<Integer, Double> searchPositions){
		Numbers ivl = getNumbers();
		for (int side = 0; side < 2; side++){
			boolean left = side == 0;
			NumberStyle style = ivl.getNumberStyle(left);
			if (style != NumberStyle.NONE){
				int start = ivl.getStart(left);
				int end = ivl.getEnd(left);
				int step = style == NumberStyle.BOTH ? 1 : 2;
				if (step != 1 && start % 2 != end % 2){
					log.error("bad interval in optimization",this);
					return false;
				}
				if (start == end){
					searchPositions.put(start, fullLength / 2);
				} else {
					int parts = Math.abs(end - start) / step;
					double partLen = fullLength / parts;
					if (start > end)
						step = -step;
					int hn = start;
					double dist = 0;
					while (true) {
						searchPositions.put(hn, dist);
						if (hn == end)
							break;
						dist += partLen;
						hn += step;
					} 
					if (parts > 1)
						assert Math.abs(fullLength - dist) < 0.1; 
				}
			}
		}
		return true;
	}
	
	/**
	 * Use Bresenham algorithm to get the Garmin points which are close to the line
	 * described by c1 and c2 and the point p.
	 * @param c1
	 * @param c2
	 * @param p
	 * @return the list of points
	 */
	public static Coord rasterLineNearPoint(Coord c1, Coord c2, Coord p, boolean includeEndPoints){
		int x0 = c1.getLongitude();
		int y0 = c1.getLatitude();
		int x1 = c2.getLongitude();
		int y1 = c2.getLatitude();
		Coord c1Dspl = c1.getDisplayedCoord();
		Coord c2Dspl = c2.getDisplayedCoord();
		int x = x0, y = y0;
		int dx =  Math.abs(x1-x), sx = x<x1 ? 1 : -1;
		int dy = -Math.abs(y1-y), sy = y<y1 ? 1 : -1;
		int err = dx+dy, e2; /* error value e_xy */
		double minDistLine = Double.MAX_VALUE;
		double minDistTarget = Double.MAX_VALUE;
		int bestX = Integer.MAX_VALUE, bestY = Integer.MAX_VALUE;
//		List<Coord> nearLinePoints = new ArrayList<>();
		
		for(;;){  /* loop */
			if (!includeEndPoints && x==x1 && y==y1)
				break;
			if (Math.abs(y - p.getLatitude()) <= 1  || Math.abs(x - p.getLongitude()) <= 1){
				Coord t = new Coord(y, x);
				double distToTarget = t.distance(p);

				if (includeEndPoints || x != x0 || y != y0){
					if (distToTarget < 10){ 
						double distLine = t.distToLineSegment(c1Dspl, c2Dspl);
						if (distLine < minDistLine || distLine == minDistLine && distToTarget < minDistTarget || distLine < 0.2 && distToTarget < minDistTarget){
							bestX = x;
							bestY = y;
							minDistLine = distLine;
							minDistTarget = distToTarget;
						} 
					}
					//					nearLinePoints.add(t);
				}
			}
			if (x==x1 && y==y1) break;
			e2 = 2*err;
			if (e2 > dy) { err += dy; x += sx; } /* e_xy+e_x > 0 */
			if (e2 < dx) { err += dx; y += sy; } /* e_xy+e_y < 0 */
		}
		if (minDistLine == Double.MAX_VALUE)
			return null;
		Coord best = new Coord(bestY, bestX);
//		GpxCreator.createGpx("e:/ld/raster", Arrays.asList(c1,c2,best,c1), nearLinePoints);
		return best;
	}

	private static int countOccurence(List<HousenumberMatch> houses, int num){
		int count = 0;
		for (HousenumberMatch house : houses){
			if (house.getHousenumber() == num)
				count++;
		}
		return count;
	}
	
	/**
	 * @param length
	 * @return string with length, e.g. "0.23 m" or "116.12 m"
	 */
	private static String formatLen(double length){
		return HousenumberGenerator.formatLen(length);
	}

	public void detectRandom() {
		int countFilledIvls = 0;
		int countFilledSides = 0;
		int countNotInOrder = 0;
		for (ExtNumbers curr = this; curr != null; curr = curr.next){
			if (curr.hasNumbers() == false)
				continue;
			countFilledIvls++;
			if (curr.notInOrder(Numbers.LEFT))
				countNotInOrder++;
			if (curr.notInOrder(Numbers.RIGHT))
				countNotInOrder++;
			if (curr.getHouses(Numbers.LEFT).size() > 1)
				++countFilledSides;
			if (curr.getHouses(Numbers.RIGHT).size() > 1)
				++countFilledSides;
			
		}
		if (countNotInOrder > 0){
			if (countNotInOrder > countFilledIvls || countNotInOrder > 2 || countFilledSides == countNotInOrder)
				housenumberRoad.setRandom(true);
		}
	}
	
	private boolean isPlausible(){
		if (getNumbers().isPlausible() == false)
			return false;
		if (multipleZipOrCity(true) || multipleZipOrCity(false))
			return false;  

		return true;
	}

	
}


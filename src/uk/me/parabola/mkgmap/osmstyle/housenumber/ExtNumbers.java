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
import java.util.TreeMap;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.net.NumberStyle;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.osmstyle.housenumber.HousenumberGenerator.HousenumberMatchComparator;
//import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.MultiHashMap;

/**
 * Helper class to allow easy corrections like splitting.
 * @author GerdP
 *
 */
public class ExtNumbers {
	private static final Logger log = Logger.getLogger(ExtNumbers.class);
	private final MapRoad road;
	ExtNumbers prev,next;
	private List<HousenumberMatch> leftHouses = Collections.emptyList();
	private List<HousenumberMatch> rightHouses = Collections.emptyList();
	private TreeMap<Integer,List<HousenumberMatch>> sortedNumbers; 
	private Numbers numbers = null;
	private int startInRoad, endInRoad;
	private int rnodNumber;
	
	public ExtNumbers(MapRoad road) {
		super();
		this.road = road;
		reset();
	}


	private void reset() {
		numbers = null;
		sortedNumbers = null;
	}
	
	
	public void setRnodNumber(int rnodNumber) {
		this.rnodNumber = rnodNumber; // what is this used for?
		if (numbers != null)
			numbers.setRnodNumber(rnodNumber);
	}


	
	public Numbers getNumbers() {
		if (numbers == null){
			numbers = new Numbers();
			numbers.setRnodNumber(rnodNumber);
			fillNumbers(true);
			fillNumbers(false);
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
			// get the sublist of house numbers
			int maxN = -1;
			for (int i = 0; i< housenumbers.size(); i++) {
				HousenumberMatch hnm = housenumbers.get(i);
				if (hnm.getSegment() >= endSegment) {
					break;
				} 
				maxN = i;
			}
			
			if (maxN >= 0) {
				assignedNumbers = maxN + 1;
				if (left) { 
					leftHouses = new ArrayList<>(housenumbers.subList(0, assignedNumbers));
				} else {
					rightHouses = new ArrayList<>(housenumbers.subList(0, assignedNumbers));
				}
				
				startInRoad = startSegment;
				endInRoad = endSegment;
				assert startSegment < endSegment;
				if (road.getPoints().get(startInRoad).isNumberNode() == false || road.getPoints().get(endInRoad).isNumberNode() == false){
					log.error("internal error: start or end is not a number node");
				}
			}
		}
		return assignedNumbers;
	}
	/**
	 * Apply the given house numbers to the numbers object.
	 * @param left {@code true} the left side of the street; {@code false} the right side of the street
	 */
	public void fillNumbers(boolean left) {
		NumberStyle style = NumberStyle.NONE;
		
		List<HousenumberMatch> housenumbers = left ? leftHouses : rightHouses;
		if (housenumbers.isEmpty() == false) {
			// get the sublist of house numbers
			boolean even = false;
			boolean odd = false;
			boolean inOrder = true;
			int lastNum = -1;
			int lastDiff = 0;
			HousenumberMatch highest, lowest;
			lowest = highest = housenumbers.get(0);
//			int highestNum = 0;
//			int lowestNum = Integer.MAX_VALUE;
			int numHouses = housenumbers.size();
			for (int i = 0; i< numHouses; i++) {
				HousenumberMatch hnm = housenumbers.get(i);
				int num = hnm.getHousenumber();
				addToSorted(hnm);
				if (num > highest.getHousenumber())
					highest = hnm;
				if (num < lowest.getHousenumber())
					lowest = hnm;
				if (lastNum > 0){
					int diff = num - lastNum;
					if(lastDiff * diff < 0){
						inOrder = false; // sign changed
					}
					lastDiff = diff;
				}
				lastNum = num;
				if (num % 2 == 0) {
					even = true;
				} else {
					odd = true;
				}
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
			int start = housenumbers.get(0).getHousenumber();
			int end = housenumbers.get(numHouses-1).getHousenumber();
			boolean increasing = false; // from low to high
			if (start == end && highestNum - lowestNum != 0){
				if (prev != null){
					int lastEnd = left ?  prev.getNumbers().getLeftEnd() : prev.getNumbers().getRightEnd();
					if (lastEnd <= lowestNum)
						increasing = true;
				} else if (next != null){
					int nextStart = left ? next.getNumbers().getLeftStart() : next.getNumbers().getRightStart();
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
			if (left) { 
				getNumbers().setLeftStart(start);
				getNumbers().setLeftEnd(end);
			} else {
				getNumbers().setRightStart(start);
				getNumbers().setRightEnd(end);
			}

			if (!inOrder){
				if (log.isDebugEnabled())
					log.debug((left? "left" : "right") ,"numbers not in order:", housenumbers.get(0).getRoad(),housenumbers);
			}
		}
		if (left)
			getNumbers().setLeftNumberStyle(style);
		else
			getNumbers().setRightNumberStyle(style);
	}

	public List<Numbers> getNumberList() {
		List<Numbers> list = new ArrayList<>();
		ExtNumbers curr = this;
		boolean hasNumbers = false;
		while (curr != null){
			list.add(curr.getNumbers());
			if (curr.getNumbers().getLeftNumberStyle() != NumberStyle.NONE || curr.getNumbers().getRightNumberStyle() != NumberStyle.NONE)
				hasNumbers = true;
			if (log.isInfoEnabled()) {
				Numbers cn = curr.getNumbers();
				log.info("Left: ",cn.getLeftNumberStyle(),cn.getRnodNumber(),"Start:",cn.getLeftStart(),"End:",cn.getLeftEnd(), "numbers "+curr.leftHouses);
				log.info("Right:",cn.getRightNumberStyle(),cn.getRnodNumber(),"Start:",cn.getRightStart(),"End:",cn.getRightEnd(), "numbers "+curr.rightHouses);
			}
			
			curr = curr.next;
		}
		if (hasNumbers)
			return list;
		return null;
	}
	

	public ExtNumbers checkChainSegmentLengths(String streetName, List<HousenumberMatch> potentialNumbersThisRoad, MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		ExtNumbers curr = this;
		boolean changed = false;
		while (curr != null){
			boolean isOK = true;
			if (curr.getNumbers().getLeftNumberStyle() == NumberStyle.BOTH){
				int oddEven[] = new int[2];
				countDistinctOddEven(curr.leftHouses, oddEven);
				if (Math.abs(oddEven[0] - oddEven[1]) > 1)
					isOK = false;	
			}
			if (isOK && curr.getNumbers().getRightNumberStyle() == NumberStyle.BOTH){
				int oddEven[] = new int[2];
				countDistinctOddEven(curr.rightHouses, oddEven);
				if (Math.abs(oddEven[0] - oddEven[1]) > 1)
					isOK = false;	
			}
			if (!isOK){
				log.debug("trying to fix unplausible 'BOTH' interval ",curr.getNumbers());
				ExtNumbers test = curr.tryAddNumberNode();
				if (test != curr){
					changed = true;
					if (curr.prev == null)
						return test.checkChainSegmentLengths(streetName, potentialNumbersThisRoad, badMatches);
					curr = test;
				}
				
			}
			curr = curr.next;
		}
		if (changed)
			return checkChainPlausibility(streetName, potentialNumbersThisRoad, badMatches);
		return this;
	}

	
	public ExtNumbers checkChainSegments(MultiHashMap<HousenumberMatch, MapRoad> badMatches, Int2IntOpenHashMap usedNumbers) {
		ExtNumbers curr = this;
		boolean changed = false;
		while (curr != null){
			boolean checkCounts = true;

			while (curr.getNumbers().isPlausible() == false){
				// this happens in the following cases:
				// 1. correct OSM data, multiple houses build a block. Standing on the road
				// you probably see a small service road which leads to the houses.
				// It is okay to use each of them. 
				// 2. correct OSM data, one or more house should be connected to a 
				// different road with the same name, we want to ignore them 
				// 3. wrong OSM data, one or more numbers are wrong, we want to ignore them
				// 4. other cases, e.g. numbers 1,3,5 followed by 10,14,12. This should be fixed
				// by splitting the segment first, as the OSM data might be correct.
				changed = curr.tryToFindSimpleCorrection();
				if (!changed){
					ExtNumbers test = curr.tryAddNumberNode();
					if (test != curr){
						if (curr.prev == null)
							return test.checkChainSegments(badMatches, usedNumbers);
						curr = test;
					}
					else {
						log.error("can't fix unplausible numbers interaval for road",curr.road, curr.getNumbers(),"left",curr.leftHouses,"right",curr.rightHouses);
						checkCounts = false;
						break;
					}
				}
			}
			
			if (checkCounts && curr.hasNumbers()){
				for (Integer n : curr.sortedNumbers.keySet()){
					int matchesThis = curr.getNumbers().countMatches(n);
					if (matchesThis == 1)
						continue;
					log.error("interval",curr.getNumbers(),"is not ok for house number",n,"in road",curr.road);
				}
				
			}
			curr = curr.next;
		}
		
		return this;
	}

	
	/**
	 * Try to add a number node
	 * near the middle. This helps when numbers like 1,3,5,8,10,12 appear
	 * on one side of the road.  
	 * @return
	 */
	private ExtNumbers tryAddNumberNode() {
		String action;
		if (endInRoad - startInRoad > 1)
			action = "change";
		else {
			if (road.getPoints().size() + 1 > LineSplitterFilter.MAX_POINTS_IN_LINE)
				return this; // can't add a node
			Coord c1 = road.getPoints().get(startInRoad);
			Coord c2 = road.getPoints().get(startInRoad+1);
			double segmentLength = c1.distance(c2);

			int countAfterEnd = 0, countBeforeStart = 0, countBetween = 0;
			double minFraction0To1 = 2;
			double maxFraction0To1 = -1;
			for (List<HousenumberMatch> list : Arrays.asList(leftHouses, rightHouses)){
				for (HousenumberMatch hnm : list){
					if (hnm.getSegmentFrac() < 0)
						++countBeforeStart;
					else if (hnm.getSegmentFrac() > 1)
						++countAfterEnd;
					else {
						++countBetween;
						if (minFraction0To1 > hnm.getSegmentFrac())
							minFraction0To1 = hnm.getSegmentFrac();
						if (maxFraction0To1 < hnm.getSegmentFrac())
							maxFraction0To1 = hnm.getSegmentFrac();
					}
				}
			}
			if (countBetween == 0){
				if (countAfterEnd == 0 || countBeforeStart == 0)
					return combineSides();
			} 
			
			// find a good place to split
			if (minFraction0To1 != maxFraction0To1){
				// used interval as is
			}
			else if (countAfterEnd > 0 && countBeforeStart == 0)
				maxFraction0To1 = 0.999;
			else if (countAfterEnd == 0 && countBeforeStart > 0)
				minFraction0To1 = 0.001;
			Coord toAdd = null;
			double bestAngle = Double.MAX_VALUE;
			double bestFraction = -1;
			HashSet<Coord> tested = new HashSet<>();
			int n = 10;
			// try to find a good split point between the houses
			double midFraction = (minFraction0To1 + maxFraction0To1) / 2;
			double startFraction = Math.max(midFraction - 6.0 / segmentLength, minFraction0To1);
			double endFraction = Math.min(midFraction + 6.0 / segmentLength, maxFraction0To1);
			
			for (int i = 1; i < n; i++){
				double workFraction;
				workFraction = startFraction + (double) i / n * (endFraction - startFraction);
				Coord test = c1.makeBetweenPoint(c2, workFraction);
				
				if (tested.add(test) == false)
					continue;
				double angle = Math.abs(Utils.getDisplayedAngle(c1, test, c2));
				if (angle < bestAngle){
					bestFraction = workFraction;
					bestAngle = angle;
					toAdd = test;
					if (angle < 0.1 && i > n / 2)
						break;
				}
			}
			double usedFraction = bestFraction;
			
			if (c1.equals(toAdd) || c2.equals(toAdd))
				return combineSides();
			if (bestAngle > 3){
				log.debug("segment too short to split without creating zig-zagging line");
				return dupNode(midFraction);
				
			}
			toAdd.incHighwayCount();
			road.getPoints().add(startInRoad+1, toAdd);

			action = "add";
			ExtNumbers work = this;
			while (work != null){
				work.increaseNodeIndexes(startInRoad, usedFraction);
				work = work.next;
			}
		} 
		int splitSegment = (startInRoad + endInRoad) / 2;
		// try to find better split position
		BitSet segmentsWithNumbers = new BitSet();
		for (HousenumberMatch hnm : leftHouses)
			segmentsWithNumbers.set(hnm.getSegment());
		for (HousenumberMatch hnm : rightHouses)
			segmentsWithNumbers.set(hnm.getSegment());
		int bestPos = -1;
		int bestDiff = Integer.MAX_VALUE;
		
		for (int i = startInRoad + 1; i < endInRoad; i++){
			if (segmentsWithNumbers.get(i) == false){
				int diff = Math.abs(splitSegment - i);
				if (diff < bestDiff){
					bestDiff = diff;
					bestPos = i;
				}
			}
		}
		if (bestPos >= 0)
			splitSegment = bestPos;
		ExtNumbers en1 = new ExtNumbers(road);
		ExtNumbers en2 = new ExtNumbers(road);
		en1.prev = prev;
		if (prev != null)
			prev.next = en1;
		en1.next = en2;
		en2.prev = en1;
		en2.next = next;
		if (next != null)
			next.prev = en2;
		en1.setRnodNumber(rnodNumber);
		en2.setRnodNumber(rnodNumber+1); 
		ExtNumbers toChange = en2.next;
		while (toChange != null){
			toChange.setRnodNumber(toChange.rnodNumber+1);
			
			toChange = toChange.next;
		}

		
		road.getPoints().get(splitSegment).setNumberNode(true);
		road.setInternalNodes(true);
		int leftUsed = en1.setNumbers(leftHouses, startInRoad, splitSegment, true);
		int rightUsed = en1.setNumbers(rightHouses, startInRoad, splitSegment, false);
		leftHouses.subList(0, leftUsed).clear();
		rightHouses.subList(0, rightUsed).clear(); 				
		leftUsed = en2.setNumbers(leftHouses, splitSegment, endInRoad, true);
		rightUsed = en2.setNumbers(rightHouses, splitSegment, endInRoad, false);
		if ("add".equals(action))
			log.error("number node added in street",road,getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		else 
			log.error("point changed to number node in street",road,getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		return en1;
	}


	private ExtNumbers dupNode(double fraction) {
		log.error("duplicating number node",road,getNumbers(),leftHouses,rightHouses);
		ExtNumbers en1 = new ExtNumbers(road);
		ExtNumbers en2 = new ExtNumbers(road);
		int index = (fraction < 0.5) ? startInRoad : endInRoad;
		Coord closePoint = road.getPoints().get(index);
		Coord toAdd = Coord.makeHighPrecCoord(closePoint.getHighPrecLat(), closePoint.getHighPrecLon());
		toAdd.incHighwayCount();
		toAdd.setNumberNode(true);
		road.getPoints().add(index, toAdd);
		road.setInternalNodes(true);
		en1.prev = prev;
		if (prev != null)
			prev.next = en1;
		en1.next = en2;
		en2.prev = en1;
		en2.next = next;
		if (next != null)
			next.prev = en2;
		en1.setRnodNumber(rnodNumber);
		en2.setRnodNumber(rnodNumber+1); 
		
		List<HousenumberMatch> left1 = new ArrayList<>();
		List<HousenumberMatch> left2 = new ArrayList<>();
		List<HousenumberMatch> right1= new ArrayList<>();
		List<HousenumberMatch> right2= new ArrayList<>();
		boolean separateOddEven = false;
		if ((getNumbers().getLeftNumberStyle() == NumberStyle.BOTH || getNumbers().getRightNumberStyle() == NumberStyle.BOTH ) &&
				getNumbers().getLeftNumberStyle() != getNumbers().getRightNumberStyle()) 
			separateOddEven = true;
		if (separateOddEven){
			for (HousenumberMatch hnm : leftHouses){
				if (hnm.getHousenumber() % 2 == 0)
					left1.add(hnm);
				else 
					left2.add(hnm);
				}
		} else {
			int mid = leftHouses.size() / 2;
			left1.addAll(leftHouses.subList(0, mid));
			left2.addAll(leftHouses.subList(mid, leftHouses.size()));
		}
		if (separateOddEven){
			for (HousenumberMatch hnm : rightHouses){
				if (hnm.getHousenumber() % 2 == 0)
					right1.add(hnm);
				else 
					right2.add(hnm);
			}
		} else {
			int mid = rightHouses.size() / 2;
			right1.addAll(rightHouses.subList(0, mid));
			right2.addAll(rightHouses.subList(mid, rightHouses.size()));
	
		}
		
		en1.setNumbers(left1, startInRoad, endInRoad, true);
		en1.setNumbers(right1, startInRoad, endInRoad, false);
		en2.setNumbers(left2, startInRoad, endInRoad, true);
		en2.setNumbers(right2, startInRoad, endInRoad, false);
		en2.startInRoad++;
		en2.endInRoad++;
		ExtNumbers work = en2.next;
		while (work != null){
			work.increaseNodeIndexes(en2.startInRoad, 0);
			work.setRnodNumber(work.rnodNumber+1);			
			work = work.next;
		}
		log.error("number node added in street",road,getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		return en1;
	}


	private ExtNumbers combineSides() {
		boolean left = false;
		if (leftHouses.isEmpty() || rightHouses.isEmpty())
			return this;
		if (leftHouses.size() > rightHouses.size())
			left = true;
		
		ExtNumbers en = new ExtNumbers(road);
		en.prev = prev;
		en.next = next;
		if (en.next != null)
			en.next.prev = en;
		if (en.prev != null)
			en.prev.next = en;
		en.setRnodNumber(rnodNumber);
		ArrayList<HousenumberMatch> combined = new ArrayList<>(leftHouses.size() + rightHouses.size());
		combined.addAll(leftHouses);
		combined.addAll(rightHouses);
		Collections.sort(combined, new HousenumberMatchComparator());
		en.setNumbers(combined, startInRoad, endInRoad, left);
		return en;
	}


	private void increaseNodeIndexes(int startPos, double fraction){
		if (endInRoad > startPos)
			endInRoad++;
		if (startInRoad > startPos)
			startInRoad++;
		
		for (List<HousenumberMatch> list: Arrays.asList(leftHouses, rightHouses)){
			for (HousenumberMatch hnm : list){
				int s = hnm.getSegment();
				if (s > startPos)
					hnm.setSegment(s+1);
				else if (s == startPos){
					if (hnm.getSegmentFrac() > fraction)
						hnm.setSegment(s+1);
				}
			}
		}
		
	}
	
	
	private void addToSorted(HousenumberMatch hnm){
		if (sortedNumbers == null)
			sortedNumbers = new TreeMap<>();
		List<HousenumberMatch> list = sortedNumbers.get(hnm.getHousenumber());
		if (list == null){
			list = new ArrayList<>();
			sortedNumbers.put(hnm.getHousenumber(), list);
		}
		list.add(hnm);
	}
	
	/**
	 * 
	 * @return true if a change was done
	 */
	private boolean tryToFindSimpleCorrection() {
		boolean leftIsWrong = false;
		if (next != null && prev != null)
			return false; 
		if (getNumbers().getLeftNumberStyle() != getNumbers().getRightNumberStyle()
				&& (getNumbers().getLeftNumberStyle() == NumberStyle.BOTH || getNumbers().getRightNumberStyle() == NumberStyle.BOTH)) {
			if (getNumbers().getLeftNumberStyle() == NumberStyle.BOTH)
				leftIsWrong = true;
			else if (getNumbers().getRightNumberStyle() == NumberStyle.BOTH)
				leftIsWrong = false;
			else 
				return false;
		} else if (getNumbers().getLeftNumberStyle() != NumberStyle.BOTH){
			if (leftHouses.size() < rightHouses.size() )
				leftIsWrong = true;
		} else
			return false;
		
		List<HousenumberMatch> wrongNumbers;
		List<HousenumberMatch> otherNumbers;
		NumberStyle otherNumberStyle;
		if (leftIsWrong){
			wrongNumbers = leftHouses;
			otherNumbers = rightHouses;
			otherNumberStyle = getNumbers().getRightNumberStyle();
		} else {
			wrongNumbers = rightHouses;
			otherNumbers = leftHouses;
			otherNumberStyle = getNumbers().getLeftNumberStyle();
		}
		int odd = countOdd(wrongNumbers);
		int even = wrongNumbers.size() - odd;
		int searchedRest = -1;
		if (even == 1 && odd > 1 )
			searchedRest = 0;
		else if (odd == 1 && even > 1 )
			searchedRest = 1;
		else 
			return false;
		
		for (int i = 0; i < wrongNumbers.size(); i++){
			HousenumberMatch hnm = wrongNumbers.get(i);
			if (hnm.getHousenumber() % 2 != searchedRest)
				continue;
			wrongNumbers.remove(i);
			reset();
			setNumbers(wrongNumbers, startInRoad, endInRoad, leftIsWrong);
			boolean move = false;
			if (hnm.getSegment() == 0 && prev == null || hnm.getSegment() == endInRoad-1 && next == null){
				// at start or end of road, check if number was placed on the wrong
				// side of the road
				if (otherNumberStyle == NumberStyle.EVEN && searchedRest == 0
						|| otherNumberStyle == NumberStyle.ODD && searchedRest == 1 || 
						otherNumberStyle == NumberStyle.NONE){
					if (hnm.getSegmentFrac() < 0 || hnm.getSegmentFrac() > 1)
						move = true;
					else if (otherNumbers.size() > 0){
						int lastOtherSegment = otherNumbers.get(otherNumbers.size()-1).getSegment();
						if (hnm.getSegment() > lastOtherSegment)
							move = true;
					}
					if (move){
						if (otherNumbers.isEmpty())
							otherNumbers = new ArrayList<>();
						otherNumbers.add(hnm);
						Collections.sort(otherNumbers, new HousenumberMatchComparator());
					}
				}
				if (!move){
					log.error(hnm.getRoad(),"house number element",hnm,hnm.getElement().toBrowseURL(), "looks wrong, is ignored");
					hnm.setIgnored(true);
				}
			}
			setNumbers(otherNumbers, startInRoad, endInRoad, !leftIsWrong);
			return true;
		}
		return false;
	}
	
	private static int countOdd(List<HousenumberMatch> houses) {
		int odd = 0;
		for (HousenumberMatch hnm : houses){
			if (hnm.getHousenumber() % 2 != 0)
				++odd;
		}
		return odd;
	}

	private static int countDistinctOddEven(List<HousenumberMatch> houses, int [] counters) {
		Int2IntOpenHashMap tested = new Int2IntOpenHashMap();
		for (HousenumberMatch hnm : houses){
			int num = hnm.getHousenumber();
			if (tested.put(num,1) == 1)
				continue;
			if (hnm.getHousenumber() % 2 == 0)
				++counters[1];
			else 
				++counters[0];
		}
		return tested.size();
	}


	/**
	 * Test if each number that was used to build the intervals is 
	 * found exactly once, try to repair wrong intervals.
	 * @param streetName 
	 * @param potentialNumbersThisRoad
	 * @param badMatches
	 * @return pointer to the head of the chain
	 */
	public ExtNumbers checkChainAsRoad(
			String streetName, List<HousenumberMatch> potentialNumbersThisRoad,
			MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		int lastCheckedNum = -1;
		int lastCheckRes = -1;
		
		for (HousenumberMatch hnm : potentialNumbersThisRoad){
			int countMmatches = 0;
			List<ExtNumbers> segments = new ArrayList<>();	
			int n = hnm.getHousenumber();
			ExtNumbers curr = this;
			
			if (lastCheckedNum == n)
				countMmatches = lastCheckRes;
			else {
				while (curr != null){
					int matchesCurr = curr.getNumbers().countMatches(n);
					if (matchesCurr > 0){
						segments.add(curr);
						if (matchesCurr == 1 && countMmatches > 0 ){
							if (curr.prev.getNumbers().getLeftEnd() == n && curr.getNumbers().getLeftStart() == n 
									||curr.prev.getNumbers().getRightEnd() == n && curr.getNumbers().getRightStart() == n){
								// intervals are overlapping, probably two houses (e.g. 2a,2b) at a T junction
								matchesCurr = 0;
							}
						}
					}
					if (matchesCurr > 0){
						countMmatches += matchesCurr;
						if (curr.startInRoad > hnm.getSegment() || curr.endInRoad < hnm.getSegment()){
							log.error("better place?",curr.road,curr.sortedNumbers,hnm,hnm.getElement().toBrowseURL());
						}
					}
					curr = curr.next;
				}
			} 
			if (countMmatches != 1){
				if (countMmatches == 0){
					log.error("number not found in road",road,hnm,hnm.getElement().toBrowseURL());
				} else {
					log.error("number found at different places in same road, device will show first hit for road",road,hnm,hnm.getElement().toBrowseURL());
					for (ExtNumbers en : segments){
						log.error(en.getNumbers(), en.sortedNumbers);
					}
				}
			}
			lastCheckedNum = n;
			lastCheckRes = countMmatches;
		}
		return this;
	}
	
	public ExtNumbers checkChainPlausibility(String streetName,
			List<HousenumberMatch> potentialNumbersThisRoad, MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		// we try to repair up to 10 times
		for (int loop = 0; loop < 10; loop++){
			ExtNumbers en1 = this;
			boolean anyChanges = false;
			while (en1 != null){
				if (en1.hasNumbers()){
					ExtNumbers en2 = en1.next;
					while (en2 != null){
						if (en2.hasNumbers()){
//							if ("Am Hexendeich".equals(streetName)){
//								log.error("checking",streetName,en1.getNumbers(), en2.getNumbers());
//							}
							boolean changed = checkIntervals(streetName, en1, en2, badMatches);
							if (changed){
								anyChanges = true;
								break;
							}
						}
						en2 = en2.next;
					}
					if (anyChanges)
						break;
				}
				en1 = en1.next;
			}
			if (!anyChanges)
				break;
		}
		return this;
	}
	
	/**
	 * Check if two intervals are overlapping  (all combinations of left + right)
	 * @param streetName
	 * @param en1
	 * @param en2
	 * @param badMatches
	 * @return true if something was changed
	 */
	public static boolean checkIntervals(String streetName, ExtNumbers en1,
			ExtNumbers en2, MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		Numbers ivl1 = en1.getNumbers();
		Numbers ivl2 = en2.getNumbers();
		
		for (int i = 0; i < 2; i++){
			boolean left1 = i == 0;
			NumberStyle style1 = left1 ? ivl1.getLeftNumberStyle() : ivl1.getRightNumberStyle();
			if (style1 == NumberStyle.NONE)
				continue;
			int s1 = left1 ?  ivl1.getLeftStart() : ivl1.getRightStart();
			int e1 = left1 ? ivl1.getLeftEnd() : ivl1.getRightEnd();
			
			for (int j = 0; j < 2; j++){
				boolean left2 = j == 0;
				NumberStyle style2 = left2 ? ivl2.getLeftNumberStyle() : ivl2.getRightNumberStyle();
				if (style2 == NumberStyle.NONE)
					continue;
				int s2 = left2 ? ivl2.getLeftStart() : ivl2.getRightStart();
				int e2 = left2 ? ivl2.getLeftEnd() : ivl2.getRightEnd();
				boolean ok = true;
				if (style1 == style2 || style1 == NumberStyle.BOTH || style2 == NumberStyle.BOTH)
					ok = checkIntervalBoundaries(s1, e1, s2, e2, left1 == left2 && en1.road == en2.road);
				if (ok) 
					continue;
				List<HousenumberMatch> houses1 = left1 ? en1.leftHouses : en1.rightHouses;
				List<HousenumberMatch> houses2 = left2 ? en2.leftHouses : en2.rightHouses;
				log.error("trying to fix unplausible combination of intervals in road",en1.road, (left1 ? "left:" : "right"),ivl1,(left2 ? "left:" : "right"),ivl2,houses1, houses2);
				double smallestDelta = Double.POSITIVE_INFINITY;
				HousenumberMatch bestMoveOrig = null;
				HousenumberMatch bestMoveMod = null;
				ExtNumbers bestRemove = null; 
				// check if we can move a house from en1 to en2
				for (HousenumberMatch hnm : houses1){
					int n = hnm.getHousenumber();
					if (en1.sortedNumbers.get(n).size() > 1)
						continue;
					if (n == s1 || n == e1) {
						Numbers modNumbers = en1.removeHouseNumber(n, left1);
						int s1Mod = left1 ? modNumbers.getLeftStart() : modNumbers.getRightStart();
						int e1Mod = left1 ? modNumbers.getLeftEnd() : modNumbers.getRightEnd();
						boolean ok2 = checkIntervalBoundaries(s1Mod, e1Mod, s2, e2, left1 == left2 && en1.road == en2.road);	
						if (ok2){
							// the modified intervals don't overlap if hnm is removed from en1
							// check if it fits into en2
							BitSet toTest = new BitSet();
							toTest.set(en2.startInRoad, en2.endInRoad);
							HousenumberMatch test = new HousenumberMatch(hnm.getElement(), hnm.getHousenumber(), hnm.getSign());
							HousenumberGenerator.findClosestRoadSegment(test, en2.road, toTest);
							if (test.getDistance() < HousenumberGenerator.MAX_DISTANCE_TO_ROAD){
								double deltaDist = test.getDistance() - hnm.getDistance(); 
								if (deltaDist < smallestDelta){
									Coord c1 = en2.road.getPoints().get(test.getSegment());
									Coord c2 = en2.road.getPoints().get(test.getSegment() + 1);
									if (left2 == HousenumberGenerator.isLeft(c1, c2, hnm.getLocation())){
										bestMoveMod = test;
										bestMoveOrig = hnm;
										smallestDelta = deltaDist;
										bestRemove = en1;
									}
								}
							}
						}
					}
				}
				for (HousenumberMatch hnm : houses2){
					int n = hnm.getHousenumber();
					if (en2.sortedNumbers.get(n).size() > 1)
						continue;
					if (n == s2 || n == e2) {
						Numbers modNumbers = en2.removeHouseNumber(n, left2);
						int s2Mod = left2 ? modNumbers.getLeftStart() : modNumbers.getRightStart();
						int e2Mod = left2 ? modNumbers.getLeftEnd() : modNumbers.getRightEnd();
						boolean ok2 = checkIntervalBoundaries(s1, e1, s2Mod, e2Mod, left1 == left2);	
						if (ok2){
							// the intervals don't overlap if hnm is removed from en2
							// check if it fits into en1
							BitSet toTest = new BitSet();
							toTest.set(en1.startInRoad, en1.endInRoad);
							HousenumberMatch test = new HousenumberMatch(hnm.getElement(), hnm.getHousenumber(), hnm.getSign());
							HousenumberGenerator.findClosestRoadSegment(test, en1.road, toTest);
							if (test.getDistance() < HousenumberGenerator.MAX_DISTANCE_TO_ROAD){
								double deltaDist = test.getDistance() - hnm.getDistance(); 
								if (deltaDist < smallestDelta){
									Coord c1 = en1.road.getPoints().get(test.getSegment());
									Coord c2 = en1.road.getPoints().get(test.getSegment() + 1);
									if (left1 == HousenumberGenerator.isLeft(c1, c2, hnm.getLocation())){
										bestMoveMod = test;
										bestMoveOrig = hnm;
										smallestDelta = deltaDist;
										bestRemove = en2;
									}
								}
							}
						}
					}
				}
				if (bestMoveMod != null){
					if (bestMoveOrig.isDuplicate()){
						log.error("duplicate number",streetName,bestMoveOrig.getSign(),bestMoveOrig.getElement().toBrowseURL() );
					}
					List<HousenumberMatch> fromHouses, toHouses;
					MapRoad fromRoad, toRoad;
					if (bestRemove == en1){
						fromHouses = houses1;
						toHouses = houses2;
						fromRoad = en1.road;
						toRoad = en2.road;
						bestMoveOrig.setLeft(left2);
						
					} else {
						fromHouses = houses2;
						toHouses = houses1;
						fromRoad = en2.road;
						toRoad = en1.road;
						bestMoveOrig.setLeft(left1);
					}
					if (toRoad == fromRoad)
						log.error("moving",streetName,bestMoveOrig.getSign(),bestMoveOrig.getElement().toBrowseURL(),"from",fromHouses,"to",toHouses,"in road",toRoad);
					else 
						log.error("moving",streetName,bestMoveOrig.getSign(),bestMoveOrig.getElement().toBrowseURL(),"from",fromHouses,"in road",fromRoad,"to",toHouses,"in road",toRoad);
					bestMoveOrig.setRoad(toRoad);
					bestMoveOrig.setSegment(bestMoveMod.getSegment());
					bestMoveOrig.setDistance(bestMoveMod.getDistance());
					bestMoveOrig.setSegmentFrac(bestMoveMod.getSegmentFrac());
					fromHouses.remove(bestMoveOrig);
					toHouses.add(bestMoveOrig);
					Collections.sort(toHouses, new HousenumberMatchComparator());
					en1.reset();
					en2.reset();
					en1.setNumbers(houses1, en1.startInRoad, en1.endInRoad, left1);
					en2.setNumbers(houses2, en2.startInRoad, en2.endInRoad, left2);
					return true;
				} else {
					// TODO: maybe split one of the intervals?
					log.error("found no correction");
				}
			}
		}

		

		return false;
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
		if (s1 == 31 && e1 == 33 && s2 == 27 && e2 == 39){
			long dd = 4;
		}
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
				else if (e1 > s2 && e1 > s2) ok = true;  // 8 6 2 4 down up, no overlap,2nd is lower 
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
				else if (e1 > s2 && e1 > s2) ok = true;  // 8 6 2 4 down up, no overlap,2nd is lower 
				else if (s1 < s2 && s1 < e2) ok = true;  // 8 6 10 10, 8 6 10 12, 8 6 12 10, 1st down, no overlap,2nd is higher  
			}
		}
		if (!ok){
//			log.error("interval check not ok: ", s1,e1,s2,e2,"same side:",sameSide);
		}
		return ok;
	}
	
	private Numbers removeHouseNumber(int hn, boolean left){
		ExtNumbers help = new ExtNumbers(road);
		help.prev = prev;
		help.next = next;
		
		List<HousenumberMatch> temp = new ArrayList<>(left ? leftHouses : rightHouses);
		Iterator<HousenumberMatch> iter = temp.iterator();
		while (iter.hasNext()){
			HousenumberMatch hnm = iter.next();
			if (hnm.getHousenumber() == hn)
				iter.remove();
		}
		help.setNumbers(temp, startInRoad, endInRoad, left);
		help.setNumbers(left ? rightHouses : leftHouses, startInRoad, endInRoad, !left);
		return help.getNumbers();
	}

	private double getRoadPartLength(){
		double len = 0;
		for (int i = startInRoad; i < endInRoad; i++){
			len += road.getPoints().get(i).distance(road.getPoints().get(i+1));
		}
		return len ;
	}

	public boolean hasNumbers(){
		getNumbers();
		return sortedNumbers != null && sortedNumbers.isEmpty() == false;
	}
}

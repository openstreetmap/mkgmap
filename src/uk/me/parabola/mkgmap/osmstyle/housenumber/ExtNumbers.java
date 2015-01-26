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
import uk.me.parabola.util.MultiHashMap;

/**
 * Helper class to allow easy corrections like splitting.
 * @author GerdP
 *
 */
public class ExtNumbers {
	private static final Logger log = Logger.getLogger(ExtNumbers.class);
	private final MapRoad r;
	ExtNumbers prev,next;
	private List<HousenumberMatch> leftHouses = Collections.emptyList();
	private List<HousenumberMatch> rightHouses = Collections.emptyList();
	private TreeMap<Integer,List<HousenumberMatch>> sortedNumbers; 
	private Numbers numbers = null;
	private int startInRoad, endInRoad;
	private int rnodNumber;
	
	public ExtNumbers(MapRoad r) {
		super();
		this.r = r;
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
				if (r.getPoints().get(startInRoad).isNumberNode() == false || r.getPoints().get(endInRoad).isNumberNode() == false){
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
					if (diff != 0 && lastDiff != 0){
						if(lastDiff * diff < 0){
							inOrder = false; // sign changed
							if (hnm.getAltSegments() != null)
								hnm.incDubious(1);
						}
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
		while (curr != null){
			list.add(curr.getNumbers());
			if (log.isInfoEnabled()) {
				Numbers cn = curr.getNumbers();
				log.info("Left: ",cn.getLeftNumberStyle(),cn.getRnodNumber(),"Start:",cn.getLeftStart(),"End:",cn.getLeftEnd(), "numbers "+curr.leftHouses);
				log.info("Right:",cn.getRightNumberStyle(),cn.getRnodNumber(),"Start:",cn.getRightStart(),"End:",cn.getRightEnd(), "numbers "+curr.rightHouses);
			}
			
			curr = curr.next;
		}
		return list;
	}
	

	public ExtNumbers checkChainSegments(MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
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
							return test.checkChainSegments(badMatches);
						curr = test;
					}
					else {
						log.error("can't fix unplausible numbers interaval for road",curr.r, curr.getNumbers(),"left",curr.leftHouses,"right",curr.rightHouses);
						checkCounts = false;
						break;
					}
				}
			}
			if (checkCounts && curr.sortedNumbers != null){
				for (Integer n : curr.sortedNumbers.keySet()){
					int matchesThis = curr.getNumbers().countMatches(n);
					
					if (matchesThis == 1)
						continue;
					if (matchesThis == 0){
						long dd = 4; // TODO: store error
					} else {
						long dd = 4; // TODO: store error
					}
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
			if (r.getPoints().size() + 1 > LineSplitterFilter.MAX_POINTS_IN_LINE)
				return this; // can't add a node
			Coord c1 = r.getPoints().get(startInRoad);
			Coord c2 = r.getPoints().get(startInRoad+1);
			double segmentLength = c1.distance(c2);

//			if (segmentLength < MIN_SEGMENT_LENGTH)
//				return this; // don't create short segments
			
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
			for (int i = 1; i < n; i++){
				double workFraction;
				workFraction = minFraction0To1 + (double) i / n * (maxFraction0To1 - minFraction0To1);
				Coord test = c1.makeBetweenPoint(c2, workFraction);
				
				if (tested.add(test) == false)
					continue;
				double angle = Math.abs(Utils.getDisplayedAngle(c1, test, c2));
				if (angle < bestAngle){
					bestFraction = workFraction;
					bestAngle = angle;
					toAdd = test;
					if (angle < 0.1)
						break;
				}
			}
			double usedFraction = bestFraction;
//			GpxCreator.createGpx("e:/ld/s"+r.getRoadDef().getId() + "_"+startInRoad, Arrays.asList(c1,toAdd,c2), new ArrayList<>(tested));
			if (c1.equals(toAdd) || c2.equals(toAdd))
				return combineSides();
			if (bestAngle > 3){
				log.debug("segment too short to split");
				return this;
			}
			toAdd.incHighwayCount();
			r.getPoints().add(startInRoad+1, toAdd);

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
		ExtNumbers en1 = new ExtNumbers(r);
		ExtNumbers en2 = new ExtNumbers(r);
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

		
		r.getPoints().get(splitSegment).setNumberNode(true);
		r.setInternalNodes(true);
		int leftUsed = en1.setNumbers(leftHouses, startInRoad, splitSegment, true);
		int rightUsed = en1.setNumbers(rightHouses, startInRoad, splitSegment, false);
		leftHouses.subList(0, leftUsed).clear();
		rightHouses.subList(0, rightUsed).clear(); 				
		leftUsed = en2.setNumbers(leftHouses, splitSegment, endInRoad, true);
		rightUsed = en2.setNumbers(rightHouses, splitSegment, endInRoad, false);
		if ("add".equals(action))
			log.error("number node added in street",r,getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		else 
			log.error("point changed to number node in street",r,getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		return en1;
	}


	private ExtNumbers combineSides() {
		boolean left = false;
		if (leftHouses.isEmpty() || rightHouses.isEmpty())
			return this;
		if (leftHouses.size() > rightHouses.size())
			left = true;
		
		ExtNumbers en = new ExtNumbers(r);
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
				if (hnm.getAltSegments() == null)
					continue;
				for (int i = 0; i < hnm.getAltSegments().size(); i++){
					int n = hnm.getAltSegments().get(i) ;
					if (n > startPos)
						hnm.getAltSegments().set(i, n+1);
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
						|| otherNumberStyle == NumberStyle.ODD && searchedRest == 1){
					if (hnm.getSegmentFrac() < 0 || hnm.getSegmentFrac() > 1)
						move = true;
					else if (otherNumbers.size() > 0){
						int lastOtherSegment = otherNumbers.get(otherNumbers.size()-1).getSegment();
						if (hnm.getSegment() > lastOtherSegment)
							move = true;
					}
					if (move){
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


	/**
	 * Test if each number that was used to build the intervals is 
	 * found exactly once.
	 * @param streetName 
	 * @param potentialNumbersThisRoad
	 * @param badMatches
	 */
	public int checkChainAsRoad(
			String streetName, List<HousenumberMatch> potentialNumbersThisRoad,
			MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		int numErrors = 0;
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
							log.error("better place?",curr.r,curr.sortedNumbers,n);
						}
					}
					curr = curr.next;
				}
			} 
			if (countMmatches != 1){
				numErrors++;
				if (countMmatches == 0){
					log.error("number not found in road",r,n,hnm.getElement().toBrowseURL());
				} else {
					log.error("number found at different places in same road, device will show first hit for road",r,n,hnm.getElement().toBrowseURL());
					for (ExtNumbers en : segments){
						log.error(en.getNumbers(), en.sortedNumbers);
					}
				}
			}
			lastCheckedNum = n;
			lastCheckRes = countMmatches;
		}
		return numErrors;
	}
	
	public ExtNumbers checkChainPlausibility(int depth,
			String streetName,
			MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		ExtNumbers curr = this;
		if (depth > 10){
			// TODO should we stop here ?
		}
		for (int i = 0; i < 2; i++){
			curr = this;
			boolean left = (i == 0);
			while (curr != null){
				Numbers cn = curr.getNumbers();
				NumberStyle style1 = left ? cn.getLeftNumberStyle() : cn.getRightNumberStyle();
				if (style1 == NumberStyle.NONE)
					curr = curr.next;
				else {
					int cs = left ? cn.getLeftStart() : cn.getRightStart();
					int ce = left ? cn.getLeftEnd() : cn.getRightEnd();
					ExtNumbers work = curr.next;
					while (work != null){
						Numbers wn  = work.getNumbers();
						NumberStyle style2 = left ? wn.getLeftNumberStyle() : wn.getRightNumberStyle();
						if (style2 == NumberStyle.NONE){
							work = work.next;
							continue;
						} else {
							if (style1 == style2 || style1 == NumberStyle.BOTH || style2==NumberStyle.BOTH){
								// check if intervals are overlapping
								int ws = left ? wn.getLeftStart() : wn.getRightStart();
								int we = left ? wn.getLeftEnd() : wn.getRightEnd();
								
								boolean ok = checkIntervalBoundaries(cs, ce, ws, we);
								if (!ok){
									
									List<HousenumberMatch> cHhouses = left ? curr.leftHouses : curr.rightHouses;
									List<HousenumberMatch> wHhouses = left ? work.leftHouses : work.rightHouses;
									log.error("checking unplausible combination of intervals",r, (left ? "left:" : "right"),cn,wn,cHhouses, wHhouses);
									
//									int oldBad = badMatches.size();
									ArrayList<HousenumberMatch> toIgnore = new ArrayList<>();
									for (HousenumberMatch hnm : cHhouses){
										if (hnm.getAltSegments() != null){
											log.error("special case L-shape ?", streetName,hnm,hnm.getElement().toBrowseURL(), hnm.getAltSegments());
										}
										int n = hnm.getHousenumber();
										if (hnm.getDubious() > 0 || hnm.hasAlternativeRoad()){
											if (n == cs || n == ce) {
												Numbers modNumbers = curr.removeHouseNumber(n, left);
												int cs2 = left ? modNumbers.getLeftStart() : modNumbers.getRightStart();
												int ce2 = left ? modNumbers.getLeftEnd() : modNumbers.getRightEnd();
												boolean ok2 = checkIntervalBoundaries(cs2, ce2, ws, we);	
												if (ok2){
													toIgnore.add(hnm);
												}
											}
										}
									}
									for (HousenumberMatch hnm : wHhouses){
										if (hnm.getAltSegments() != null){
											log.error("special case L-shape ?", streetName,hnm,hnm.getElement().toBrowseURL(), hnm.getAltSegments());
										}
										int n = hnm.getHousenumber();
										if (hnm.getDubious() > 0 || hnm.hasAlternativeRoad()){
											if (n == ws || n == we) {
												Numbers modNumbers = work.removeHouseNumber(n, left);
												int ws2 = left ? modNumbers.getLeftStart() : modNumbers.getRightStart();
												int we2 = left ? modNumbers.getLeftEnd() : modNumbers.getRightEnd();
												boolean ok2 = checkIntervalBoundaries(cs, ce, ws2, we2);	
												if (ok2){
													toIgnore.add(hnm);
												}
											}
										}
									}
									if (toIgnore.size() == 1){
										HousenumberMatch hnm = toIgnore.get(0);
										log.error("adding to exclude list: combination of",streetName,hnm,hnm.getElement().toBrowseURL(),"and road",hnm.getRoad());
										badMatches.add(toIgnore.get(0), toIgnore.get(0).getRoad());
									} else {
										if (work != curr.next){
											// TODO next is empty, better move number to next instead of splitting work or curr ?
											return this;
										}
										ExtNumbers toSplit = null;
										if(toIgnore.size() > 1){
											if (curr.endInRoad - curr.startInRoad > work.endInRoad - work.startInRoad)
												toSplit = curr;
											else 
												toSplit = work;
										}
										else if (Math.abs(ce-cs) > Math.abs(we-ws)){
											toSplit = curr;

										} else {
											toSplit = work;
										}
										if (toSplit != null){
											ExtNumbers test = toSplit.tryAddNumberNode();
											if (test != toSplit){
												if (test.prev == null)
													return test.checkChainPlausibility(depth+1,streetName, badMatches);
												else 
													return this.checkChainPlausibility(depth+1,streetName, badMatches);
											} else {
												long dd = 4;
											}
											
										}
									}
									return this; // no need to continue;
								}
							}
						}
						break;
					}
					curr = work;
				} 
			}
		}
		return this;
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
	private static boolean checkIntervalBoundaries(int s1, int e1, int s2,int e2){
		boolean ok = false;
		// many cases, maybe someone finds simpler code?
		if (s1 == e1) {
			if (e1 == s2) ok = true; // 6 6 4 4 , 6 6 6 6 , 6 6 8 8 
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
		return ok;
	}
	
	private Numbers removeHouseNumber(int hn, boolean left){
		ExtNumbers help = new ExtNumbers(r);
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
	
}

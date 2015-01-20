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
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.app.net.NumberStyle;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;
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
			int highestNum = 0;
			int lowestNum = Integer.MAX_VALUE;
			int numHouses = housenumbers.size();
			for (int i = 0; i< numHouses; i++) {
				HousenumberMatch hnm = housenumbers.get(i);
				int num = hnm.getHousenumber();
				addToSorted(hnm);
				if (num > highestNum)
					highestNum = num;
				if (num < lowestNum)
					lowestNum = num;
				if (lastNum > 0){
					int diff = num - lastNum;
					if (diff != 0 && lastDiff != 0){
						if(lastDiff * diff < 0){
							inOrder = false; // sign changed
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
				end = highestNum;
			} else if (start < end)
				increasing = true;
				
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
				log.info("Left: ",getNumbers().getLeftNumberStyle(),getNumbers().getRnodNumber(),"Start:",getNumbers().getLeftStart(),"End:",getNumbers().getLeftEnd(), "numbers "+leftHouses);
				log.info("Right:",getNumbers().getRightNumberStyle(),getNumbers().getRnodNumber(),"Start:",getNumbers().getRightStart(),"End:",getNumbers().getRightEnd(), "numbers "+rightHouses);
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
						curr.prev.next = test;
						curr = test;
					}
					else {
						log.error("can't fix unplausible numbers interaval", curr.getNumbers() );
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
	 * If the numbers interval is not plausible, try to add a number node
	 * in the middle. This helps when numbers like 1,3,5,8,10,12 appear
	 * on one side of the road.  
	 * @return
	 */
	private ExtNumbers tryAddNumberNode() {
		if (endInRoad - startInRoad <= 1){
			return this;
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
		// TODO: find best split position(s) so that we don't
		// have long empty segments as start or end 
		ExtNumbers en1 = new ExtNumbers(r);
		ExtNumbers en2 = new ExtNumbers(r);
		en1.prev = prev;
		en1.next = en2;
		en2.prev = en1;
		en2.next = next;
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
		log.error("node added",r,getNumbers(),"==>",en1.getNumbers(),"+",en2.getNumbers());
		return en1;
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
	 * @param wrongNumbers
	 * @param wrongUsed
	 * @param otherNumbers
	 * @param otherNumberStyle
	 * @param lastSegment
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
	
	public ExtNumbers checkChainPlausibility(String streetName, MultiHashMap<HousenumberMatch, MapRoad> badMatches){
		ExtNumbers curr = this;
		for (int i = 0; i < 2; i++){
			curr = this;
			while (curr != null){
				Numbers cn = curr.getNumbers();
				NumberStyle style1 = (i == 0) ? cn.getLeftNumberStyle() : cn.getRightNumberStyle();
				if (style1 == NumberStyle.NONE)
					curr = curr.next;
				else {
					int cs = (i == 0) ? cn.getLeftStart() : cn.getRightStart();
					int ce = (i == 0) ? cn.getLeftEnd() : cn.getRightEnd();
					ExtNumbers work = curr.next;
					while (work != null){
						Numbers wn  = work.getNumbers();
						NumberStyle style2 = (i == 0) ? wn.getLeftNumberStyle() : wn.getRightNumberStyle();
						if (style2 == NumberStyle.NONE){
							work = work.next;
							continue;
						} else {
							if (style1 == style2 || style1 == NumberStyle.BOTH || style2==NumberStyle.BOTH){
								// check if intervals are overlapping
								int ws = (i == 0) ? wn.getLeftStart() : wn.getRightStart();
								int we = (i == 0) ? wn.getLeftEnd() : wn.getRightEnd();
								boolean ok = false;
								// many cases, maybe someone finds simpler code?
								if (cs == ce) {
									if (ce == ws) ok = true;
									else if (ce < ws && we > ce) ok = true;
									else if (ce > ws && ce > we) ok = true;
								} else if (cs < ce){
									if (ce <= ws && ce <= we) ok = true;
									else if (ce > ws && cs > we) ok = true;
								} else {
									if (ce >= ws && ws >= we) ok = true;
									else if (cs < ws && ws <= ws) ok = true;
								}
								if (!ok){
									
									List<HousenumberMatch> cHhouses = (i == 0) ? curr.leftHouses : curr.rightHouses;
									List<HousenumberMatch> wHhouses = (i == 0) ? work.leftHouses : work.rightHouses;
									log.error("check", ((i == 0) ? "left:" : "right"),cn,wn,cHhouses, wHhouses);
									
									int oldBad = badMatches.size();
									
//									for (HousenumberMatch hnm : cHhouses){
//										int n = hnm.getHousenumber();
//										if (hnm.getDubious() > 0 || hnm.hasAlternativeRoad()){
//											if (n == ce && cs > ce && ce < ws) {
//												badMatches.add(hnm, hnm.getRoad());
//											}
//											else if (n == ce && cs < ce && ce > ws) {
//												badMatches.add(hnm, hnm.getRoad());
//											}
//										}
//									}
//									if (oldBad != badMatches.size())
//										return this;
//									for (HousenumberMatch hnm : wHhouses){
//										int n = hnm.getHousenumber();
//										if (hnm.getDubious() > 0 || hnm.hasAlternativeRoad()){
//											if (n == ws && cs > ce && ws > ce) {
//												badMatches.add(hnm, hnm.getRoad());
//											}
//											else if (n == ws && cs < ce && ws < ce) {
//												badMatches.add(hnm, hnm.getRoad());
//											}
//										}
//									}
//									return this; // no need to continue;
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
}

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
import java.util.Comparator;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.osmstyle.housenumber.HousenumberGenerator.HousenumberMatchComparator;
import uk.me.parabola.util.MultiHashMap;

/**
 * Helper class to combine house numbers with MapRoad instances
 * @author Gerd Petermann
 *
 */
public class HousenumberRoad {
	private static final Logger log = Logger.getLogger(HousenumberRoad.class);
	
	private final String streetName;
	private final MapRoad road;
	private ExtNumbers extNumbersHead;
	private final List<HousenumberMatch> houseNumbers;
	private boolean changed;

	public HousenumberRoad(String streetName, MapRoad r, List<HousenumberMatch> potentialNumbersThisRoad) {
		this.streetName = streetName;
		this.road = r;
		this.houseNumbers = new ArrayList<>(potentialNumbersThisRoad);
		
	}

	public void buildIntervals(MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		Collections.sort(houseNumbers, new Comparator<HousenumberMatch>() {
			public int compare(HousenumberMatch o1, HousenumberMatch o2) {
				if (o1 == o2)
					return 0;
				int d = o1.getHousenumber() - o2.getHousenumber();
				if (d != 0)
					return d;
				d = o1.getSign().compareTo(o2.getSign());
				if (d != 0)
					return d;
				d = o1.getSegment() - o2.getSegment();
				return d;
			}
		});
		if (log.isInfoEnabled())
			log.info("Initial housenumbers for",road,road.getCity(),houseNumbers);
		
		filterRealDuplicates();
		filterGroups();
		
		List<HousenumberMatch> leftNumbers = new ArrayList<HousenumberMatch>();
		List<HousenumberMatch> rightNumbers = new ArrayList<HousenumberMatch>();
		
		for (HousenumberMatch hr : houseNumbers) {
			if (hr.getRoad() == null || hr.isIgnored()){
				continue;
			}
			if (hr.isLeft()) {
				leftNumbers.add(hr);
			} else {
				rightNumbers.add(hr);
			}
		}

		Collections.sort(leftNumbers, new HousenumberMatchComparator());
		Collections.sort(rightNumbers, new HousenumberMatchComparator());
		
		optimizeNumberIntervalLengths();
		int currNodePos = 0;
		int nodeIndex = 0;
		int prevNumberNodeIndex = 0;
		int prevNodePos = 0;
		extNumbersHead = null;
		ExtNumbers currNumbers = null;
		for (Coord p : road.getPoints()) {
			if (currNodePos == 0) {
				assert p instanceof CoordNode; 
			}

			// An ordinary point in the road.
			if (p.isNumberNode() == false) {
				currNodePos++;
				continue;
			}

			// The first time round, this is guaranteed to be a CoordNode
			if (currNodePos == 0) {
				nodeIndex++;
				currNodePos++;
				continue;
			}

			// Now we have a CoordNode and it is not the first one.
			ExtNumbers numbers = new ExtNumbers(this);
			numbers.setRnodNumber(prevNumberNodeIndex);
			int leftUsed = numbers.setNumbers(leftNumbers, prevNodePos, currNodePos, true);
			int rightUsed = numbers.setNumbers(rightNumbers, prevNodePos, currNodePos, false);
			prevNodePos = currNodePos;
			// maintain chain
			numbers.prev = currNumbers;
			if (currNumbers != null)
				currNumbers.next = numbers;
			else {
				extNumbersHead = numbers;
			}
			currNumbers = numbers;
			leftNumbers.subList(0, leftUsed).clear();
			rightNumbers.subList(0, rightUsed).clear(); 				

			prevNumberNodeIndex = nodeIndex;
			nodeIndex++;
			currNodePos++;
		}
	}

	/**
	 * @param badMatches
	 */
	public void checkIntervals(MultiHashMap<HousenumberMatch, MapRoad> badMatches){
		int oldBad = badMatches.size();
		if (extNumbersHead == null)
			return;
		boolean anyChanges = false;
		for (int loop = 0; loop < 10; loop++){
			setChanged(false);
			extNumbersHead = extNumbersHead.checkSingleChainSegments(streetName, badMatches);
			if (oldBad != badMatches.size())
				return;
			extNumbersHead = extNumbersHead.checkChainPlausibility(streetName, houseNumbers, badMatches);
			if (oldBad != badMatches.size())
				return;
			if (isChanged())
				anyChanges = true;
			else 
				break;
		}
		setChanged(anyChanges);
	}
	
	
	/**
	 * Identify duplicate numbers and ignore those which are close together
	 * and those which are probably wrong. 
	 */
	private void filterRealDuplicates() {
		List<HousenumberMatch> toIgnore = new ArrayList<>();
		final int TO_SEARCH = 6;
		int oddLeft = 0, oddRight = 0, evenLeft = 0, evenRight = 0;
		for (HousenumberMatch  hnm: houseNumbers){
			if (hnm.isIgnored())
				continue;
			if (hnm.isLeft()){
				if (hnm.getHousenumber() % 2 == 0) 
					evenLeft++;
				else 
					oddLeft++;
			} else {
				if (hnm.getHousenumber() % 2 == 0)
					evenRight++;
				else
					oddRight++;
			}
		}
		HousenumberMatch usedForCalc = null;
		for (int i = 1; i < houseNumbers.size(); i++){
			HousenumberMatch hnm1 = houseNumbers.get(i - 1);
			HousenumberMatch hnm2 = houseNumbers.get(i);
			if (hnm1.getSign().equals(hnm2.getSign()) == false){
				usedForCalc = null;
			} else {
				// found a duplicate house number (e.g. 2 and 2 or 1b and 1b)
				double distBetweenHouses = hnm2.getLocation().distance(hnm1.getLocation());
				double distToUsed = (usedForCalc == null) ? distBetweenHouses : hnm2.getLocation().distance(usedForCalc.getLocation()); 
				if (usedForCalc == null)
					usedForCalc = (hnm1.getDistance() < hnm2.getDistance()) ? hnm1 : hnm2;
				else {
					hnm1 = usedForCalc;
				}
				boolean sameSide = (hnm2.isLeft() == hnm1.isLeft());
				if (log.isDebugEnabled())
					log.debug("analysing duplicate address",streetName,hnm1.getSign(),"for road with id",getRoad().getRoadDef().getId());
				if (sameSide && (distBetweenHouses < 100 || distToUsed < 100)){
					HousenumberMatch obsolete = hnm1 == usedForCalc ? hnm2 : hnm1;
					if (log.isDebugEnabled())
						log.debug("house",obsolete,obsolete.getElement().toBrowseURL(),"is close to other element and on the same road side, is ignored");
					toIgnore.add(obsolete);
					continue;
				}
				
				if (!sameSide){
					if (log.isDebugEnabled())
						log.debug("oddLeft, oddRight, evenLeft, evenRight:",oddLeft, oddRight, evenLeft, evenRight);
					HousenumberMatch wrongSide = null;
					if (hnm2.getHousenumber() % 2 == 0){
						if (evenLeft == 1 && (oddLeft > 1 || evenRight > 0 && oddRight == 0)){
							wrongSide = hnm2.isLeft() ? hnm2: hnm1;
						}
						if (evenRight == 1 && (oddRight > 1 || evenLeft > 0 && oddLeft == 0)){
							wrongSide = !hnm2.isLeft() ? hnm2: hnm1;
						}
					} else {
						if (oddLeft == 1 && (evenLeft > 1 || oddRight > 0 && evenRight == 0)){
							wrongSide = hnm2.isLeft() ? hnm2: hnm1;
						}
						if (oddRight == 1 && (evenRight > 1 || oddLeft > 0 && evenLeft == 0)){
							wrongSide = !hnm2.isLeft() ? hnm2: hnm1;
						}
					}
					if (wrongSide != null){
						if (log.isDebugEnabled())
							log.debug("house",streetName,wrongSide.getSign(),"from",wrongSide.getElement().toBrowseURL(),"seems to be wrong, is ignored");
						toIgnore.add(wrongSide);
						continue;
					}
				}
				
//				String[] locTags = {"mkgmap:postal_code", "mkgmap:city"};
//				for (String locTag : locTags){
//					String loc1 = hnm1.getElement().getTag(locTag);
//					String loc2 = hnm2.getElement().getTag(locTag);
//					if (loc1 != null && loc2 != null && loc1.equals(loc2) == false){
//						if (log.isDebugEnabled())
//							log.debug("different",locTag,"values:",loc1,loc2);
//					}
//				}
				double[] sumDist = new double[2];
				double[] sumDistSameSide = new double[2];
				int[] confirmed = new int[2];
				int[] falsified = new int[2];
				int[] found = new int[2];
				List<HousenumberMatch> dups = Arrays.asList(hnm2, hnm1);
				for (int k = 0; k < dups.size(); k++){
					HousenumberMatch other, curr;
					if (k == 0){
						curr = dups.get(0);
						other = dups.get(1);
					} else {
						curr = dups.get(1);
						other = dups.get(0);
					}
					int pos = houseNumbers.indexOf(curr);

					int left = pos - 1;
					int right = pos + 1;
					HousenumberMatch nearHouse;
					int stillToFind = TO_SEARCH;
					while (stillToFind > 0){
						int oldDone = stillToFind;
						if (left >= 0){
							nearHouse = houseNumbers.get(left);
							if (nearHouse != other){
								double dist = curr.getLocation().distance(nearHouse.getLocation());
								sumDist[k] += dist;
								if (nearHouse.isLeft() == curr.isLeft()){
									sumDistSameSide[k] += dist;
								}
								if (curr.getHousenumber() == nearHouse.getHousenumber()){
									if (dist < 20)
										confirmed[k]++;
								} else {
									if (dist < 10 )
										falsified[k]++;
								}
							}
							--left;
							stillToFind--;
							if (stillToFind == 0)
								break;
						}
						if (right < houseNumbers.size()){
							nearHouse = houseNumbers.get(right);
							if (nearHouse != other){
								double dist = curr.getLocation().distance(nearHouse.getLocation());
								sumDist[k] += dist;
								if (nearHouse.isLeft() == curr.isLeft()){
									sumDistSameSide[k] += dist;
								}
								if (curr.getHousenumber() == nearHouse.getHousenumber()){
									if (dist < 40)
										confirmed[k]++;
								} else {
									if (dist < 10 )
										falsified[k]++;
								}
							}
							stillToFind--;
							right++;
						}
						if (oldDone == stillToFind)
							break;
					}
					found[k] = TO_SEARCH - 1 - stillToFind; 
				}
				if (log.isDebugEnabled()){
					log.debug("dup check 1:", streetName, hnm1, hnm1.getElement().toBrowseURL());
					log.debug("dup check 2:", streetName, hnm2, hnm2.getElement().toBrowseURL());
					log.debug("confirmed",Arrays.toString(confirmed),"falsified",Arrays.toString(falsified),"sum-dist",Arrays.toString(sumDist),"sum-dist-same-side",Arrays.toString(sumDistSameSide));
				}
				HousenumberMatch bad = null;
				if (confirmed[1] > 0 && confirmed[0] == 0  && falsified[1] == 0)
					bad = dups.get(0);
				else if (confirmed[0] > 0 && confirmed[1] == 0  && falsified[0] == 0)
					bad = dups.get(1);
				else if (found[0] > 3 && sumDist[0] > sumDist[1] && sumDistSameSide[0] > sumDistSameSide[1])
					bad = dups.get(0);
				else if (found[1] > 3 && sumDist[1] > sumDist[0] && sumDistSameSide[1] > sumDistSameSide[0])
					bad = dups.get(1);
				if (bad != null){
					toIgnore.add(bad);
				} else {
					if (log.isDebugEnabled())
						log.debug("duplicate house number, don't know which one to use, ignoring both");
					toIgnore.add(hnm1);
					toIgnore.add(hnm2);
					hnm2.setIgnored(true);
					hnm1.setIgnored(true);
				}
			}
		} 
		for (HousenumberMatch hnm : toIgnore){
			if (log.isInfoEnabled())
				log.info("duplicate housenumber",streetName,hnm.getSign(),"is ignored for road with id",hnm.getRoad().getRoadDef().getId(),",house:",hnm.getElement().toBrowseURL());
			houseNumbers.remove(hnm);
		}
	}

	/**
	 * Identify groups of buildings with numbers like 1a,1b,1c.
	 * The list in housenumbers is sorted so that 2 appears before 2a and
	 * 2b appears before 2c. 
	 * XXX This is quite aggressive, maybe we have to add more logic here.  
	 */
	private void filterGroups() {
		if (houseNumbers.size() <= 1)
			return;
		HousenumberMatch prev = houseNumbers.get(0);
		HousenumberMatch used = null;
		for (int i = 1; i < houseNumbers.size(); i++){
			HousenumberMatch hnm = houseNumbers.get(i);
			if (hnm.getHousenumber() != prev.getHousenumber())
				used = null;
			else {
				if (used == null)
					used = prev;
				hnm.setIgnored(true);
				if (log.isInfoEnabled())
					log.info("using",streetName,used.getSign(), "in favor of",hnm.getSign(),"as target for address search");
			}
			prev = hnm;
		}
	}
	

	/**
	 * Detect parts of roads without house numbers and change start
	 * and end of the part to house number nodes (if not already)
	 * This increases the precision of the address search
	 * and costs only a few bytes. 
	 */
	private void optimizeNumberIntervalLengths() {
		BitSet segmentsWithNumbers = new BitSet();
		for (HousenumberMatch  hnm: houseNumbers){
			segmentsWithNumbers.set(hnm.getSegment());
		}
		
		boolean searched = segmentsWithNumbers.get(0);
		int numPoints = road.getPoints().size();
		for (int i = 0; i < numPoints; i++){
			if (segmentsWithNumbers.get(i) != searched){
				changePointToNumberNode(road,i);
				searched = !searched;
			}
		}
	}

	
	private static void changePointToNumberNode(MapRoad r, int pos) {
		Coord co = r.getPoints().get(pos);
		if (co.isNumberNode() == false){
			if (log.isInfoEnabled())
				log.info("road",r,"changing point",pos,"to number node at",co.toDegreeString(),"to increase precision for house number search");
			co.setNumberNode(true);
			r.setInternalNodes(true);
		}
	}

	public void checkWrongRoadAssignmments(HousenumberRoad other,
			MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		if (this.extNumbersHead == null || other.extNumbersHead == null)
			return;
		
		for (int loop = 0; loop < 10; loop++){
			boolean changed = false;
			ExtNumbers head1 = this.extNumbersHead;
			for (ExtNumbers en1 = head1; en1 != null; en1 = en1.next){
				if (changed)
					break;
				if (en1.hasNumbers() == false)
					continue;
				ExtNumbers head2 = other.extNumbersHead;
				for (ExtNumbers en2 = head2; en2 != null; en2 = en2.next){
					if (changed)
						break;
					if (en2.hasNumbers() == false)
						continue;
					int res = ExtNumbers.checkIntervals(streetName, en1, en2, badMatches);
					switch (res) {
					case ExtNumbers.OK_NO_CHANGES:
					case ExtNumbers.NOT_OK_KEEP:
						break;
					case ExtNumbers.OK_AFTER_CHANGES:
						changed = true;
						this.setChanged(true);
						other.setChanged(true);
						break;
					case ExtNumbers.NOT_OK_TRY_SPLIT:
						if (en1.needsSplit()){
							ExtNumbers test = en1.tryAddNumberNode(ExtNumbers.SR_FIX_ERROR);
							if (test != en1){
								changed = true;
								if (test.prev == null){
									this.extNumbersHead = test;
								}
							}
						}
						if (en2.needsSplit()){
							ExtNumbers test = en2.tryAddNumberNode(ExtNumbers.SR_FIX_ERROR);
							if (test != en2){
								changed = true;
								if (test.prev == null){
									other.extNumbersHead = test;
								}
							}
						}
						break;
					case ExtNumbers.NOT_OK_STOP:
						return;
					default:
						log.error("can't fix",en1,en2);
					}
				}
			}
			if (!changed)
				break;
		}
	}
	
	public void setNumbers() {
		if (extNumbersHead == null)
			return;
		log.debug("numbers for road",road);		
		road.setNumbers(extNumbersHead.getNumberList());
	}
	
	public MapRoad getRoad(){
		return road;
	}

	public boolean isChanged() {
		return changed;
	}

	public void setChanged(boolean changed) {
		this.changed = changed;
	}

	/**
	 * 
	 */
	public void improveSearchResults() {
		ExtNumbers curr = extNumbersHead;
		while (curr != null) {
			ExtNumbers test = curr.splitLargeGaps();
			if (test != curr) {
				setChanged(true);
				if (curr.prev == null)
					extNumbersHead = test;
				curr = test;
				continue;
			}
			curr = curr.next;
		}
		
	}
	
}



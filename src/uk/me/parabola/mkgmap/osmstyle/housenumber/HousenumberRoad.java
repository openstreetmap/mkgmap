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
import java.util.HashMap;
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
				d = o1.getSegment() - o2.getSegment();
				if (d != 0)
					return d;
				d = o1.getSign().compareTo(o2.getSign());
				return d;
			}
		});
		
//		int oddLeft = 0, oddRight = 0, evenLeft = 0, evenRight = 0;
//		for (HousenumberMatch  hnm: potentialNumbersThisRoad){
//			if (hnm.isLeft()){
//				if (hnm.getHousenumber() % 2 == 0) 
//					evenLeft++;
//				else 
//					oddLeft++;
//			} else {
//				if (hnm.getHousenumber() % 2 == 0)
//					evenRight++;
//				else
//					oddRight++;
//			}
//		}
//		NumberStyle expectedLeftStyle = NumberStyle.BOTH, expectedRightStyle = NumberStyle.BOTH;
//		if (evenLeft + oddLeft == 0)
//			expectedLeftStyle = NumberStyle.NONE;
//		else if (evenLeft == 0 && oddLeft > 0 || evenLeft + oddLeft > 3 && evenLeft <= 1)
//			expectedLeftStyle = NumberStyle.ODD;
//		else if (evenLeft > 0 && oddLeft == 0 || evenLeft + oddLeft > 3 && oddLeft <= 1)
//			expectedLeftStyle = NumberStyle.EVEN;
//		if (evenRight + oddRight == 0)
//			expectedRightStyle = NumberStyle.NONE;
//		else if (evenRight == 0 && oddRight > 0 || evenRight + oddRight > 3 && evenRight <= 1)
//			expectedRightStyle = NumberStyle.ODD;
//		else if (evenRight > 0 && oddRight == 0 || evenRight + oddRight > 3 && oddRight <= 1)
//			expectedRightStyle = NumberStyle.EVEN;
		int oldBad = badMatches.size();
		filterWrongDuplicates(badMatches);
		if (oldBad != badMatches.size())
			return;
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
//		checkLShapes(streetName, leftNumbers);
//		checkLShapes(streetName, rightNumbers);

		Collections.sort(leftNumbers, new HousenumberMatchComparator());
		Collections.sort(rightNumbers, new HousenumberMatchComparator());
//		System.out.println(r + " sorted " + potentialNumbersThisRoad);
//		System.out.println(r + " left   " + expectedLeftStyle + leftNumbers);
//		System.out.println(r + " right  " + expectedRightStyle + rightNumbers);
//		System.out.println(r + " " + oddLeft + " " + oddRight + " " + evenLeft +" " +  evenRight);
		log.info("Initial housenumbers for",road,road.getCity());
		log.info("Numbers:",houseNumbers);
		
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
	 * Identify duplicate numbers and remove those which are very far from
	 * other houses with same or near numbers.
	 * @param streetName
	 * @param sortedHouseNumbers
	 * @param badMatches
	 */
	private void filterWrongDuplicates(MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		List<HousenumberMatch> toIgnore = new ArrayList<>();
		HashMap<String,HousenumberMatch> signs = new HashMap<>();
		final int TO_SEARCH = 6;
		for (HousenumberMatch hnm : houseNumbers){
			if (hnm.isDuplicate() == false)
				continue;
			HousenumberMatch old = signs.put(hnm.getSign(),hnm);
			if (old != null){
				// found a duplicate house number
				boolean sameSide = (hnm.isLeft() == old.isLeft());
				int pos1 = Math.min(old.getSegment(), hnm.getSegment());
				int pos2 = Math.max(old.getSegment(), hnm.getSegment());
				double distBetweenHouses = old.getLocation().distance(hnm.getLocation());
				if (distBetweenHouses < 25 && sameSide && pos1 != pos2){
					useMidPos(streetName, hnm,old);
					continue;
				}
				if (distBetweenHouses > 100 || pos2 - pos1 > 0){
					List<HousenumberMatch> betweeen = new ArrayList<>();
					for (HousenumberMatch hnm2 : houseNumbers){
						if (hnm.getHousenumber() == hnm.getHousenumber())
							continue;
						if (hnm2.getSegment() < pos1 || hnm2.getSegment() > pos2 || hnm2 == old || hnm2 == hnm)
							continue;
						if (!sameSide || sameSide && hnm2.isLeft() == hnm.isLeft())
							betweeen.add(hnm2);
					}
					if (betweeen.isEmpty() && distBetweenHouses < 100){
						if (sameSide)
							useMidPos(streetName, hnm,old);
						continue;
					}
//					int oldPos = sortedHouseNumbers.indexOf(old);
//					int currPos = sortedHouseNumbers.indexOf(hnm);
//					int foundOld = 0, foundCurr = 0;
					double[] sumDist = new double[2];
					double[] sumDistSameSide = new double[2];
					int[] confirmed = new int[2];
					int[] falsified = new int[2];
					int[] found = new int[2];
					List<HousenumberMatch> dups = Arrays.asList(hnm, old);
					for (int i = 0; i < dups.size(); i++){
						HousenumberMatch other, curr;
						if (i == 0){
							curr = dups.get(0);
							other = dups.get(1);
						} else {
							curr = dups.get(1);
							other = dups.get(0);
						}
						int pos = houseNumbers.indexOf(curr);
						
						int left = pos - 1;
						int right = pos + 1;
						HousenumberMatch hnm2;
						int stillToFind = TO_SEARCH;
						while (stillToFind > 0){
							int oldDone = stillToFind;
							if (left >= 0){
								hnm2 = houseNumbers.get(left);
								if (hnm2 != other){
									double dist = curr.getLocation().distance(hnm2.getLocation());
									sumDist[i] += dist;
									if (hnm2.isLeft() == curr.isLeft()){
										sumDistSameSide[i] += dist;
									}
									if (curr.getHousenumber() == hnm2.getHousenumber()){
										if (dist < 20)
											confirmed[i]++;
									} else {
										if (dist < 10 )
											falsified[i]++;
									}
								}
								--left;
								stillToFind--;
								if (stillToFind == 0)
									break;
							}
							if (right < houseNumbers.size()){
								hnm2 = houseNumbers.get(right);
								if (hnm2 != other){
									double dist = curr.getLocation().distance(hnm2.getLocation());
									sumDist[i] += dist;
									if (hnm2.isLeft() == curr.isLeft()){
										sumDistSameSide[i] += dist;
									}
									if (curr.getHousenumber() == hnm2.getHousenumber()){
										if (dist < 40)
											confirmed[i]++;
									} else {
										if (dist < 10 )
											falsified[i]++;
									}
								}
								stillToFind--;
								right++;
							}
							if (oldDone == stillToFind)
								break;
						}
						found[i] = TO_SEARCH - 1 - stillToFind; 
					}
					log.debug("dup check 1:", streetName, old, old.getElement().toBrowseURL());
					log.debug("dup check 2:", streetName, hnm, hnm.getElement().toBrowseURL());
					log.debug("confirmed",Arrays.toString(confirmed),"falsified",Arrays.toString(falsified),"sum-dist",Arrays.toString(sumDist),"sum-dist-same-side",Arrays.toString(sumDistSameSide));
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
						if (old.isLeft() == hnm.isLeft() && distBetweenHouses < 100){
							useMidPos(streetName, hnm, old);
						}
						else {
							log.debug("duplicate house number, don't know which one to use, ignoring both");
							toIgnore.add(old);
							toIgnore.add(hnm);
							hnm.setIgnored(true);
							old.setIgnored(true);
						}
					}
				}
			} 
		}
		for (HousenumberMatch hnm : toIgnore){
			badMatches.add(hnm, hnm.getRoad());
			log.warn("duplicate housenumber",streetName,hnm.getSign(),"is ignored for road with id",hnm.getRoad().getRoadDef().getId(),",house:",hnm.getElement().toBrowseURL());
			houseNumbers.remove(hnm);
		}
	}
	
	private static void useMidPos(String streetName, HousenumberMatch hnm1, HousenumberMatch hnm2) {
		int avgSegment = (hnm1.getSegment() + hnm2.getSegment()) / 2;
		BitSet toTest = new BitSet();
		toTest.set(avgSegment);
		if (avgSegment != hnm1.getSegment()){
			HousenumberGenerator.findClosestRoadSegment(hnm1, hnm1.getRoad(), toTest);
		}
		if (avgSegment != hnm2.getSegment()){
			HousenumberGenerator.findClosestRoadSegment(hnm2, hnm2.getRoad(), toTest);
		} 						
		log.info("using same segment for duplicate housenumbers", streetName, hnm2, hnm2.getElement().toBrowseURL(), hnm1.getElement().toBrowseURL());
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

	public void improveSearchResults(
			MultiHashMap<HousenumberMatch, MapRoad> badMatches) {
		extNumbersHead = extNumbersHead.improveDistances(badMatches);
		
	}
}



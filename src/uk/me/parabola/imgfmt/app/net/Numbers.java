/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.CityInfo;
import uk.me.parabola.mkgmap.general.ZipCodeInfo;

/**
 * Describes the house numbering from a node in the road.
 * @author Steve Ratcliffe
 */
public class Numbers {
	private static final Logger log = Logger.getLogger(Numbers.class);
	public static final boolean LEFT = true;
	public static final boolean RIGHT = false;

	private static final int MAX_DELTA = 131071; // see NumberPreparer

	// The node in the road where these numbers apply.  In the polish notation it is the
	// node in the road, whereas in the NET file it is the index of the number node.
	private int nodeNumber; // node in road index
	private Integer indexNumber; // the position in the list of Numbers (starting with 0)
	// the data on side of the road
	private RoadSide leftSide,rightSide;

	private class RoadSide {
		NumDesc numbers;
		// to be added
		CityInfo cityInfo;
		ZipCodeInfo zipCode;
		boolean isEmpty(){
			return cityInfo == null && zipCode == null && numbers == null;
		}
	}

	private class NumDesc{
		NumberStyle numberStyle;
		int start,end;

		public NumDesc(NumberStyle numberStyle, int start, int end) {
			this.numberStyle = numberStyle;
			this.start = start;
			this.end = end;
		}
		public boolean contained(int hn){
			boolean isEven = (hn % 2 == 0);
			if (numberStyle == NumberStyle.BOTH
					|| numberStyle == NumberStyle.EVEN && isEven
					|| numberStyle == NumberStyle.ODD && !isEven){
				if (start <= end) {
					if (start <= hn && hn <= end)
						return true;
				}
				else { 
					if (end <= hn && hn <= start)
						return true;
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return String.format("%s,%d,%d", numberStyle, start,end);
		}

	}

	public Numbers() {
	}

	/**
	 * This constructor takes a comma separated list as in the polish format. Also used in testing as
	 * it is an easy way to set all common parameters at once.
	 *
	 * @param spec Node number, followed by left and then right parameters as in the polish format.
	 */
	public Numbers(String spec) {
		String[] strings = spec.split(",");
		nodeNumber = Integer.valueOf(strings[0]);
		NumberStyle numberStyle = NumberStyle.fromChar(strings[1]);
		int start = Integer.valueOf(strings[2]);
		int end = Integer.valueOf(strings[3]);
		setNumbers(LEFT, numberStyle, start, end);
		numberStyle = NumberStyle.fromChar(strings[4]);
		start = Integer.valueOf(strings[5]);
		end = Integer.valueOf(strings[6]);
		setNumbers(RIGHT, numberStyle, start, end);

		if (strings.length > 8){
			// zip codes 
			String zip = strings[7];
			if ("-1".equals(zip) == false)
				setZipCode(LEFT, new ZipCodeInfo(zip));
			zip = strings[8];
			if ("-1".equals(zip) == false)
				setZipCode(RIGHT, new ZipCodeInfo(zip));
		}
		if (strings.length > 9){
			String city,region,country;
			int nextPos = 9;
			city = strings[nextPos];
			if ("-1".equals(city) == false){
				region = strings[nextPos + 1];
				country = strings[nextPos + 2];
				setCityInfo(LEFT, new CityInfo(city, region, country));
				nextPos = 12;
			} else 
				nextPos = 10;
			city = strings[nextPos];
			if ("-1".equals(city) == false){
				region = strings[nextPos + 1];
				country = strings[nextPos + 2];
				setCityInfo(RIGHT, new CityInfo(city, region, country));
			} 			
		} 		
	}

	public void setNumbers(boolean left, NumberStyle numberStyle, int start, int end){
		if (numberStyle != NumberStyle.NONE || start != -1 || end != -1){
			RoadSide rs = assureSideIsAllocated(left);
			rs.numbers = new NumDesc(numberStyle, start, end);
		} else {
			RoadSide rs = (left) ? leftSide : rightSide;
			if (rs != null)
				rs.numbers = null;
			removeIfEmpty(left);
		}
	}

	public void setCityInfo(boolean left, CityInfo ci){
		if (ci != null){
			RoadSide rs = assureSideIsAllocated(left);
			rs.cityInfo = ci;
		} else {
			RoadSide rs = (left) ? leftSide : rightSide;
			if (rs != null)
				rs.cityInfo = null;
			removeIfEmpty(left);
		}
	}

	public CityInfo getCityInfo(boolean left){
		RoadSide rs = (left) ? leftSide : rightSide;
		return (rs != null) ? rs.cityInfo : null;
	}

	public void setZipCode(boolean left, ZipCodeInfo zipCode){
		if (zipCode != null){
			RoadSide rs = assureSideIsAllocated(left);
			rs.zipCode = zipCode;
		} else {
			RoadSide rs = (left) ? leftSide : rightSide;
			if (rs != null)
				rs.zipCode= null;
			removeIfEmpty(left);
		}
	}


	public ZipCodeInfo getZipCodeInfo (boolean left){
		RoadSide rs = (left) ? leftSide : rightSide;
		return (rs != null) ? rs.zipCode: null;
	}

	private void removeIfEmpty(boolean left){
		if (left && leftSide != null && leftSide.isEmpty())
			leftSide = null;
		if (!left && rightSide != null && rightSide.isEmpty())
			rightSide = null;
	}

	// allocate or return allocated RoadSide instance for the given road side
	private RoadSide assureSideIsAllocated(boolean left){
		if (left && leftSide == null)
			leftSide = new RoadSide();
		if (!left && rightSide == null)
			rightSide = new RoadSide();
		return (left) ? leftSide : rightSide;
	}

	public int getNodeNumber() {
		return nodeNumber;
	}

	public void setNodeNumber(int nodeNumber) {
		this.nodeNumber = nodeNumber;
	}

	public int getIndex() {
		if (indexNumber == null) {
			log.error("WARNING: index not set!!");
			return nodeNumber;
		}
		return indexNumber;
	}

	public boolean hasIndex() {
		return indexNumber != null;
	}

	/**
	 * @param index the nth number node 
	 */
	public void setIndex(int index) {
		this.indexNumber = index;
	}

	private NumDesc getNumbers(boolean left) {
		RoadSide rs = (left) ? leftSide : rightSide;
		return (rs != null) ? rs.numbers : null;  
	}

	public NumberStyle getNumberStyle(boolean left) {
		NumDesc n = getNumbers(left);
		return (n == null) ? NumberStyle.NONE : n.numberStyle;
	}

	public int getStart(boolean left) {
		NumDesc n = getNumbers(left);
		return (n == null) ? -1 : n.start; // -1 is the default in the polish format
	}

	public int getEnd(boolean left) {
		NumDesc n = getNumbers(left);
		return (n == null) ? -1 : n.end; // -1 is the default in the polish format
	}

	public String toString() {
		String nodeStr = "0";
		if (nodeNumber > 0)
			nodeStr = String.valueOf(nodeNumber);
		else if (getIndex() > 0)
			nodeStr = String.format("(n%d)", getIndex());

		nodeStr = String.format("%s,%s,%d,%d,%s,%d,%d",
				nodeStr,
				getNumberStyle(LEFT),
				getStart(LEFT),
				getEnd(LEFT),
				getNumberStyle(RIGHT),
				getStart(RIGHT),
				getEnd(RIGHT));

		if (getCityInfo(LEFT) != null || getCityInfo(RIGHT) != null
				|| getZipCodeInfo(LEFT) != null || getZipCodeInfo(RIGHT) != null) {
			nodeStr = String.format("%s,%s,%s", nodeStr,
					getPolishZipCode(LEFT), getPolishZipCode(RIGHT));
			if (getCityInfo(LEFT) != null || getCityInfo(RIGHT) != null) {
				nodeStr = String.format("%s,%s,%s",nodeStr,
						getPolishCityInfo(LEFT),getPolishCityInfo(RIGHT)); 
			}
		}
		return nodeStr; 		
	}

	public NumberStyle getLeftNumberStyle() {
		return getNumberStyle(LEFT);
	}
	public NumberStyle getRightNumberStyle() {
		return getNumberStyle(RIGHT);
	}
	public int getLeftStart(){
		return getStart(LEFT);
	}
	public int getRightStart(){
		return getStart(RIGHT);
	}
	public int getLeftEnd(){
		return getEnd(LEFT);
	}
	public int getRightEnd(){
		return getEnd(RIGHT);
	} 

	public boolean equals(Object obj) {
		if (!(obj instanceof Numbers))
			return false;

		Numbers other = (Numbers) obj;
		return toString().equals(other.toString());
	}

	public int hashCode() {
		return toString().hashCode();
	} 

	public boolean isPlausible(){
		if (!isPlausible(getLeftNumberStyle(), getLeftStart(), getLeftEnd()))
			return false;
		if (!isPlausible(getRightNumberStyle(), getRightStart(), getRightEnd()))
			return false;
		if (getLeftNumberStyle() == NumberStyle.NONE
				|| getRightNumberStyle() == NumberStyle.NONE)
			return true;
		if (getCityInfo(LEFT) != null){
			if (getCityInfo(LEFT).equals(getCityInfo(RIGHT)) == false)
				return true;
		} else if (getCityInfo(RIGHT) != null)
			return true;
		if (getZipCodeInfo(LEFT) != null){
			if (getZipCodeInfo(LEFT).equals(getZipCodeInfo(RIGHT)) == false)
				return true;
		} else if (getCityInfo(RIGHT) != null)
			return true;
		if (getLeftNumberStyle() == getRightNumberStyle() || getLeftNumberStyle() == NumberStyle.BOTH || getRightNumberStyle()==NumberStyle.BOTH){
			// check if intervals are overlapping
			int start1, start2,end1,end2;
			if (getLeftStart() < getLeftEnd()){
				start1 = getLeftStart();
				end1 = getLeftEnd();
			} else {
				start1 = getLeftEnd();
				end1 = getLeftStart();
			}
			if (getRightStart() < getRightEnd()){
				start2 = getRightStart();
				end2 = getRightEnd();
			} else {
				start2 = getRightEnd();
				end2 = getRightStart();
			}
			if (start2 > end1 || end2 < start1)
				return true;
			if (getLeftStart() == getLeftEnd() && getRightStart() == getRightEnd() && getLeftStart() == getRightStart())
				return true; // single number on both sides of the road 

			return false;
		}

		return true;
	}

	private static boolean isPlausible(NumberStyle style, int start, int end){
		if (Math.abs(start - end) > MAX_DELTA)
			return false;
		if (style == NumberStyle.EVEN)
			return start % 2 == 0 && end % 2 == 0;
		if (style == NumberStyle.ODD)
			return start % 2 != 0 && end % 2 != 0;
		return true;
	}

	public boolean isContained(int hn, boolean left){
		RoadSide rs = left ? leftSide : rightSide;
		if (rs == null || rs.numbers == null)
			return false;
		return rs.numbers.contained(hn);
	}
	
	/**
	 * @param hn a house number
	 * @param left left or right side
	 * @return 0 if the number is not within the intervals, 1 if it is on one side, 2 if it on both sides 
	 */
	public int countMatches(int hn) {
		int matches = 0;
		if (isContained(hn, LEFT))
			matches++;
		if (isContained(hn, RIGHT))
			matches++;
		if (matches > 1){
			if (getLeftStart() == getLeftEnd() && getRightStart() == getRightEnd())
				matches = 1; // single number on both sides of the road 
		}
		return matches;
	}

	/** 
	 * Compare all fields that describe the interval, but not the position
	 * @param other
	 * @return true if these fields are equal
	 */
	public boolean isSimilar(Numbers other){
		if (other == null)
			return false;
		if (getLeftNumberStyle() != other.getLeftNumberStyle()
				|| getLeftStart() != other.getLeftStart() || getLeftEnd() != other.getLeftEnd()
				|| getRightNumberStyle() != other.getRightNumberStyle()
				|| getRightStart() != other.getRightStart() || getRightEnd() != other.getRightEnd())
			return false;
		return true;

	}

	public boolean isEmpty(){
		return getLeftNumberStyle() == NumberStyle.NONE && getRightNumberStyle() == NumberStyle.NONE;
	}


	private String getPolishCityInfo (boolean left){
		CityInfo ci = getCityInfo(left);
		if (ci == null)
			return "-1";
		StringBuilder sb = new StringBuilder();
		if (ci.getCity() != null)
			sb.append(ci.getCity());
		sb.append(",");
		if (ci.getRegion() != null)
			sb.append(ci.getRegion());
		sb.append(",");
		if (ci.getCountry() != null)
			sb.append(ci.getCountry());
		return sb.toString();
	}

	private String getPolishZipCode (boolean left){
		ZipCodeInfo zip = getZipCodeInfo(left);
		return (zip != null && zip.getZipCode() != null ) ? zip.getZipCode() : "-1";
	}


}

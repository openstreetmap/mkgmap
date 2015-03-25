/*
 * Copyright (C) 2013.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Stores the matching data between a housenumber and its road.
 * @author WanMil
 */
public class HousenumberMatch {

	private final Element element;
	private Coord location;
	private MapRoad road;
	
	private double distance = Double.POSITIVE_INFINITY;
	private int segment = -1;
	private boolean left;
	
	private double segmentFrac;
	
	private int housenumber;
	private String sign; 
	private boolean ignored;
	private boolean isDuplicate;
	private boolean interpolated;
	private int moved;
	// distance in m between closest point on road and the point that is found in the address search
	private double searchDist = Double.NaN;
	private boolean isFarDuplicate;
	private HousenumberGroup group;
	private List<MapRoad> alternativeRoads;
	private int intervalInfoRefs; // counter
	
	/**
	 * Instantiates a new housenumber match element.
	 * @param element the OSM element tagged with mkgmap:housenumber
	 * @throws IllegalArgumentException if the housenumber cannot be parsed
	 */
	public HousenumberMatch(Element element) {
		this.element = element;
		parseHousenumber();
	}
	
	public HousenumberMatch(Element element, int hn, String sign){
		this.element = element;
		this.housenumber = hn;
		this.sign = sign;
	}

	/**
	 * Retrieves the location of the housenumber.
	 * @return location of housenumber
	 */
	public Coord getLocation() {
		if (location == null)
		  location = (element instanceof Node ? ((Node)element).getLocation() : ((Way)element).getCofG());
		return location;
	}
	
	/**
	 * Retrieves the house number of this element.
	 * @param e an OSM element
	 * @return the house number (or {@code null} if no house number set)
	 */
	private static final short housenumberTagKey1 =  TagDict.getInstance().xlate("mkgmap:housenumber");
	private static final short housenumberTagKey2 =  TagDict.getInstance().xlate("addr:housenumber");
	public static String getHousenumber(Element e) {
		String res = e.getTag(housenumberTagKey1); 
		if (res != null)
			return res;
		return e.getTag(housenumberTagKey2);
	}
	
	/**
	 * Parses the house number string. It accepts the first positive number part
	 * of a string. So all leading and preceding non number parts are ignored.
	 * So the following strings are accepted:
	 * <table>
	 * <tr>
	 * <th>Input</th>
	 * <th>Output</th>
	 * </tr>
	 * <tr>
	 * <td>23</td>
	 * <td>23</td>
	 * </tr>
	 * <tr>
	 * <td>-23</td>
	 * <td>23</td>
	 * </tr>
	 * <tr>
	 * <td>21-23</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>Abc 21</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>Abc 21.45</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>21 Main Street</td>
	 * <td>21</td>
	 * </tr>
	 * <tr>
	 * <td>Main Street</td>
	 * <td><i>IllegalArgumentException</i></td>
	 * </tr>
	 * </table>
	 * @throws IllegalArgumentException if parsing fails
	 */
	private void parseHousenumber() {
		String housenumberString = getHousenumber(element);
		sign = housenumberString;
		if (housenumberString == null) {
			throw new IllegalArgumentException("No housenumber found in "+element.toBrowseURL());
		}
		
		// the housenumber must match against the pattern <anything>number<notnumber><anything>
		Pattern p = Pattern.compile("\\D*(\\d+)\\D?.*");
		Matcher m = p.matcher(housenumberString);
		if (m.matches() == false) {
			throw new IllegalArgumentException("No housenumber ("+element.toBrowseURL()+"): "+housenumberString);
		}
		try {
			// get the number part and parse it
			housenumber = Integer.parseInt(m.group(1));
		} catch (NumberFormatException exp) {
			throw new IllegalArgumentException("No housenumber ("+element.toBrowseURL()+"): "+housenumberString);
		}

		// a housenumber must be > 0
		if (housenumber <= 0) {
			throw new IllegalArgumentException("No housenumber ("+element.toBrowseURL()+"): "+housenumberString);
		}
	}

	public MapRoad getRoad() {
		return road;
	}

	public void setRoad(MapRoad road) {
		this.road = road;
	}

	/**
	 * Retrieves the distance to the road.
	 * @return distance in m
	 */
	public double getDistance() {
		return distance;
	}

	/**
	 * Sets the distance to the road
	 * @param distance distance in m
	 */
	public void setDistance(double distance) {
		this.distance = distance;
	}

	/**
	 * Retrieves the segment number the house number belongs to.
	 * @return the segment number
	 */
	public int getSegment() {
		return segment;
	}

	/**
	 * Sets the segment number the house number belongs to.
	 * @param segment the segment number
	 */
	public void setSegment(int segment) {
		this.segment = segment;
	}

	public boolean isLeft() {
		return left;
	}

	/**
	 * Sets if the house number is on the left or right side of the street.
	 * @param left {@code true} left side; {@code false} right side
	 */
	public void setLeft(boolean left) {
		this.left = left;
	}

	/**
	 * Retrieve the relative position of this house number within the segement
	 * of the related road.
	 * @return the relative position within the roads segment
	 */
	public double getSegmentFrac() {
		return segmentFrac;
	}

	/**
	 * Sets the relative position of this house number within its segment
	 * of the related road.
	 * @param segmentFrac relative position within the segment
	 */
	public void setSegmentFrac(double segmentFrac) {
		this.segmentFrac = segmentFrac;
	}

	/**
	 * Retrieve the house number
	 * @return the house number
	 */
	public int getHousenumber() {
		return housenumber;
	}

	/**
	 * Set the house number.
	 * @param housenumber house number
	 */
	public void setHousenumber(int housenumber) {
		this.housenumber = housenumber;
	}

	/**
	 * Retrieve the OSM element that defines the house number.
	 * @return the OSM element
	 */
	public Element getElement() {
		return element;
	}

	/** 
	 * @return the house number as coded in the tag
	 */
	public String getSign(){
		return sign;
	}
	
	public boolean hasAlternativeRoad() {
		return alternativeRoads != null && alternativeRoads.isEmpty() == false;
	}

	public boolean isIgnored() {
		return ignored;
	}

	public void setIgnored(boolean ignored) {
		this.ignored = ignored;
	}

	public boolean isDuplicate() {
		return isDuplicate;
	}

	public void setDuplicate(boolean isDuplicate) {
		this.isDuplicate = isDuplicate;
	}

	public boolean isInterpolated() {
		return interpolated;
	}

	public void setInterpolated(boolean interpolated) {
		this.interpolated = interpolated;
	}

	public int getMoved() {
		return moved;
	}

	public void incMoved() {
		this.moved++;
	}

	public double getSearchDist() {
		return searchDist;
	}

	public void setSearchDist(double searchDist) {
		this.searchDist = searchDist;
	}

	public String toString() {
		String s1 = String.valueOf(housenumber);
		if (sign.length() > 2 + s1.length())
			return s1 + "("+segment+")";
		return sign + "("+segment+")";
	}

	public void setFarDuplicate(boolean b) {
		this.isFarDuplicate = b;
		
	}

	public boolean isFarDuplicate() {
		return isFarDuplicate;
	}

	/**
	 * @return either an existing point on the road
	 * or the calculated perpendicular. In the latter case
	 * the highway count is zero.
	 *   
	 */
	public Coord getClosestPointOnRoad(){
		if (segmentFrac <= 0)
			return getRoad().getPoints().get(segment);
		if (segmentFrac >= 1)
			return getRoad().getPoints().get(segment+1);
		Coord c1 = getRoad().getPoints().get(segment);
		Coord c2 = getRoad().getPoints().get(segment+1);
		return c1.makeBetweenPoint(c2, segmentFrac);
	}

	/**
	 * @param other a different house on the same road
	 * @return  the distance in m between the perpendiculars on the road
	 * of two houses.
	 */
	public double getDistOnRoad(HousenumberMatch other) {
		if (getRoad() != other.getRoad()){
			assert false : "cannot compute distance on road for different roads"; 
		}
		List<Coord> points = getRoad().getPoints();
		HousenumberMatch hnm1 = this;
		HousenumberMatch hnm2 = other;
		if (hnm1.segment > hnm2.segment || hnm1.segment == hnm2.segment && hnm1.segmentFrac > hnm2.segmentFrac){
			hnm1 = other;
			hnm2 = this;
		}
		int s1 = hnm1.segment;
		int s2 = hnm2.segment;
		double distOnRoad = 0;
		while (s1 < s2){
			double segLen = points.get(s1).distance(points.get(s1 + 1));
			if (s1 == hnm1.getSegment() && hnm1.getSegmentFrac() > 0){
				// rest of first segment
				distOnRoad += Math.max(0, 1-hnm1.getSegmentFrac()) * segLen;
			} else
				distOnRoad += segLen;
			s1++;
		}
		double segLen = points.get(s1).distance(points.get(s1 + 1));
		if (hnm2.getSegmentFrac() > 0)
			distOnRoad += Math.min(1, hnm2.getSegmentFrac()) * segLen;
		if (hnm1.getSegmentFrac() > 0 && s1 == hnm1.segment)
			distOnRoad -= Math.min(1, hnm1.getSegmentFrac()) * segLen;
		return distOnRoad;
	}

	public void setGroup(HousenumberGroup housenumberBlock) {
		this.group = housenumberBlock;
	}

	public HousenumberGroup getGroup() {
		return group;
	}

	public void addAlternativeRoad(MapRoad road2) {
		if (alternativeRoads == null){
			alternativeRoads = new ArrayList<>();
		}
		alternativeRoads.add(road2);
	}
	public List<MapRoad> getAlternativeRoads() {
		if (alternativeRoads == null)
			return Collections.emptyList();
		return alternativeRoads;
	}
	public void forgetAlternativeRoads(){
		alternativeRoads = null;
	}

	public int getIntervalInfoRefs() {
		return intervalInfoRefs;
	}

	public void incIntervalInfoRefs() {
		intervalInfoRefs++;
	}

	public void decIntervalInfoRefs() {
		if (intervalInfoRefs > 0)
			--intervalInfoRefs;
	}

	public boolean isDirectlyConnected(HousenumberMatch other){
		if (getElement() instanceof Way && other.getElement() instanceof Way){
			List<Coord> s1 = ((Way) getElement()).getPoints();
			List<Coord> s2 = ((Way) other.getElement()).getPoints();
			for (int i = 0; i+1 < s1.size(); i++){
			    Coord co = s1.get(i);
			    co.setPartOfShape2(false);
			}
			for (int i = 0; i+1 < s2.size(); i++){
			    Coord co = s2.get(i);
			    co.setPartOfShape2(true);
			}
			for (int i = 0; i+1 < s1.size(); i++){
			    Coord co = s1.get(i);
			    if (co.isPartOfShape2())
			    	return true;
			}
		}
		return false;
	}
}


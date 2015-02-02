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

import it.unimi.dsi.fastutil.ints.IntArrayList;

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
	private boolean hasAlternativeRoad;
	private IntArrayList altSegments;
	private boolean ignored;
	private int dubious; // increased when this element might be wrong
	
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
		return hasAlternativeRoad;
	}

	public void setHasAlternativeRoad(boolean hasAlternativeRoad) {
		this.hasAlternativeRoad = hasAlternativeRoad;
	}

	public void incDubious(int inc){
		dubious += inc;
	}
	
	public int getDubious(){
		return dubious;
	}
	
	
	public boolean isIgnored() {
		return ignored;
	}

	public void setIgnored(boolean ignored) {
		this.ignored = ignored;
	}

	public String toString() {
		String s1 = String.valueOf(housenumber);
		if (sign.length() > 2 + s1.length())
			return s1 + "("+segment+")";
		return sign + "("+segment+")";
	}

	public void setAltSegments(IntArrayList goodSegments) {
		altSegments = goodSegments;
	}
	
	public IntArrayList getAltSegments() {
		return altSegments;
	}

	public double getBearing(){
		Coord c1 = getRoad().getPoints().get(getSegment());
		Coord c2 = getRoad().getPoints().get(getSegment()+1);
		return c1.bearingTo(c2);
	}
	
}

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Stores the matching data between a housenumber and its road.
 * @author WanMil
 */
public class HousenumberMatch {

	private final Element element;
	
	private MapRoad road;
	
	private double distance = Double.POSITIVE_INFINITY;
	private int segment = -1;
	private boolean left;
	
	private double segmentFrac;
	
	private int housenumber;
	
	/**
	 * Instantiates a new housenumber match element.
	 * @param element the OSM element tagged with mkgmap:housenumber
	 * @throws IllegalArgumentException if the housenumber cannot be parsed
	 */
	public HousenumberMatch(Element element) {
		this.element = element;
		parseHousenumber();
	}
	
	/**
	 * Retrieves the location of the housenumber.
	 * @return location of housenumber
	 */
	public Coord getLocation() {
		return (element instanceof Node ? ((Node)element).getLocation() : ((Way)element).getCofG());
	}
	
	private void parseHousenumber() {
		String housenumberString = element.getTag("mkgmap:housenumber");
		
		// the housenumber must match against the pattern <anything>number<notnumber><anything>
		Pattern p = Pattern.compile("\\D*(\\d+)\\D?.*");
		Matcher m = p.matcher(housenumberString);
		if (m.matches() == false) {
			throw new IllegalArgumentException("No housenumber: "+housenumberString);
		}
		try {
			// get the number part and parse it
			housenumber = Integer.parseInt(m.group(1));
		} catch (NumberFormatException exp) {
			throw new IllegalArgumentException("No housenumber: "+housenumberString);
		}

		// a housenumber must be > 0
		if (housenumber <= 0) {
			throw new IllegalArgumentException("No housenumber: "+housenumberString);
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

	public void setDistance(double distance) {
		this.distance = distance;
	}

	public int getSegment() {
		return segment;
	}

	public void setSegment(int segment) {
		this.segment = segment;
	}

	public boolean isLeft() {
		return left;
	}

	public void setLeft(boolean left) {
		this.left = left;
	}

	public double getSegmentFrac() {
		return segmentFrac;
	}

	public void setSegmentFrac(double segmentFrac) {
		this.segmentFrac = segmentFrac;
	}

	public int getHousenumber() {
		return housenumber;
	}

	public void setHousenumber(int housenumber) {
		this.housenumber = housenumber;
	}

	public Element getElement() {
		return element;
	}
	
	public String toString() {
		return String.valueOf(housenumber)+"("+segment+")";
	}
}

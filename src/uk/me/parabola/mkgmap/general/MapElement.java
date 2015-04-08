/**
 * Copyright (C) 2006 Steve Ratcliffe
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * Author: steve
 * Date: 26-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import java.util.Arrays;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.ExtTypeAttributes;
import uk.me.parabola.imgfmt.app.trergn.MapObject;

/**
 * A map element is a point, line or shape that appears on the map.  This
 * class holds all the common routines that are shared across all elements.
 * 
 * @author Steve Ratcliffe.
 */
public abstract class MapElement {
	protected String[] labels;
	private int type;

	private int minResolution = 24;
	private int maxResolution = 24;

	private ExtTypeAttributes extTypeAttributes;
	// other attributes
	private String zip,country,region,city,street,phone,houseNumber,isIn;

	protected MapElement() {
		labels = new String[4];
	}

	protected MapElement(MapElement orig) {
		labels = Arrays.copyOf(orig.labels, 4);
		type = orig.type;
		minResolution = orig.minResolution;
		maxResolution = orig.maxResolution;
		extTypeAttributes = orig.extTypeAttributes;
		zip = orig.zip;
		country = orig.country;
		region = orig.region;
		city = orig.city;
		street = orig.street;
		phone = orig.phone;
		houseNumber = orig.houseNumber;
		isIn = orig.isIn;
	}

	/**
	 * Provide a copy of this MapElement without geometry. This is used
	 * when filtering and clipping to create modified versions.
	 *
	 * @return the copy;
	 */
	public abstract MapElement copy();

	public String getName() {
		return labels[0];
	}
	
	public String[] getLabels() {
		return this.labels;
	}
	
	public void setName(String name) {
		this.labels[0] = name;
	}

	public void add2Name(String name) {
		for (int i = 1; i < 4; i++) {
			if (this.labels[i] == null) {
				this.labels[i] = name;
				break;
			}
		}
	}

	public void setLabels(String[] labels) {
		this.labels = Arrays.copyOf(labels, 4);
	}

	public ExtTypeAttributes getExtTypeAttributes() {
		return extTypeAttributes;
	}

	public void setExtTypeAttributes(ExtTypeAttributes eta) {
		extTypeAttributes = eta;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}
	
	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}
	
	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region= region;
	}	
	
	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;	
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
	
		if(phone.startsWith("00")) {
			phone = phone.replaceFirst("00","+");
		}
		this.phone = phone;	
	}

	public String getHouseNumber() {
		return houseNumber;
	}

	public void setHouseNumber(String houseNumber) {
		this.houseNumber = houseNumber;		
	}
	
	public String getIsIn() {
		return isIn;
	}

	public void setIsIn(String isIn) {
	  if(isIn != null)
		this.isIn = isIn.toUpperCase();
	}	


	/**
	 * This is the type code that goes in the .img file so that the GPS device
	 * knows what to display.
	 *
	 * @return the type.
	 */
	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public boolean isSimilar(MapElement other) {
		if (this.minResolution != other.minResolution)
			return false;
		if (this.maxResolution != other.maxResolution)
			return false;
		if (this.type != other.type)
			return false;

		String thisName = getName();
		String otherName = other.getName();

		if (thisName == null && otherName == null)	
			return true;
		if (thisName!=null && otherName!=null && thisName.equals(otherName))
			return true;
		return false;
	}

	public boolean hasExtendedType() {
		return MapObject.hasExtendedType(type);
	}

	/**
	 * Get the 'location' of the element.  This is the mid point of the bounding
	 * box for the element.  For a point, this will be the coordinates of the
	 * point itself of course.
	 *
	 * @return Co-ordinate of the mid-point of the bounding box of the element.
	 */
	public abstract Coord getLocation();

	/**
	 * Get the resolutions that an element should be displayed at.
	 * It will return the minimum resolution at which this element should be
	 * displayed at.
	 *
	 * @return The lowest resolution at which the element will be visible.
	 */
	public int getMinResolution() {
		return minResolution;
	}

	public void setMinResolution(int minResolution) {
		this.minResolution = minResolution;
	}

	/**
	 * The maximum resolution at which the element will be visible. This is normally
	 * 24, in other words the element is visible at all resolutions above the minimum.
	 * You can however set this lower, so that it will disappear as you zoom in, presumably to be
	 * replaced by another element.
	 * 
	 * @return The max resolution (<= 24), default is 24.
	 */
	public int getMaxResolution() {
		return maxResolution;
	}

	public void setMaxResolution(int maxResolution) {
		this.maxResolution = maxResolution;
	}
}

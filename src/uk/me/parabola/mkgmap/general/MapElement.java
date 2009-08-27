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

import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.ExtTypeAttributes;

/**
 * A map element is a point, line or shape that appears on the map.  This
 * class holds all the common routines that are shared across all elements.
 * 
 * @author Steve Ratcliffe.
 */
public abstract class MapElement {
	private String name;
	private String ref;
	private int type;

	private int minResolution = 24;
	private int maxResolution = 24;

	private ExtTypeAttributes extTypeAttributes;
	
	private final Map<String, String> attributes = new HashMap<String, String>();

	protected MapElement() {
	}

	protected MapElement(MapElement orig) {
		name = orig.name;
		ref = orig.ref;
		type = orig.type;
		minResolution = orig.minResolution;
		maxResolution = orig.maxResolution;
		extTypeAttributes = orig.extTypeAttributes;
	}

	/**
	 * Provide a copy of this MapElement without geometry. This is used
	 * when filtering and clipping to create modified versions.
	 *
	 * @return the copy;
	 */
	public abstract MapElement copy();

	public String getName() {
		return name;
	}

	public String getRef() {
		return ref;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public ExtTypeAttributes getExtTypeAttributes() {
		return extTypeAttributes;
	}

	public void setExtTypeAttributes(ExtTypeAttributes eta) {
		extTypeAttributes = eta;
	}

	public String getCity() {
		return attributes.get("city");
	}

	public void setCity(String city) {
		attributes.put("city", city);
	}
	
	public String getZip() {
		return attributes.get("zip");
	}

	public void setZip(String zip) {
		attributes.put("zip", zip);
	}

	public String getCountry() {
		return attributes.get("country");
	}

	public void setCountry(String country) {
		attributes.put("country", country);
	}
	
	public String getRegion() {
		return attributes.get("region");
	}

	public void setRegion(String region) {
		attributes.put("region", region);
	}	
	
	public String getStreet() {
		return attributes.get("street");
	}

	public void setStreet(String street) {
		attributes.put("street", street);	
	}

	public String getPhone() {
		return attributes.get("phone");
	}

	public void setPhone(String phone) {
	
		if(phone.startsWith("00")) {
			phone = phone.replaceFirst("00","+");
		}
		attributes.put("phone", phone);	
	}

	public String getHouseNumber() {
		return attributes.get("houseNumber");
	}

	public void setHouseNumber(String houseNumber) {
		attributes.put("houseNumber", houseNumber);		
	}
	
	public String getIsIn() {
		return attributes.get("isIn");
	}

	public void setIsIn(String isIn) {
	  if(isIn != null)
		attributes.put("isIn", isIn.toUpperCase());
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

	public boolean hasExtendedType() {
		return hasExtendedType(type);
	}

	public final static boolean hasExtendedType(int type) {
		return type >= 0x010000;
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

	public int getMaxResolution() {
		return maxResolution;
	}

	public void setMaxResolution(int maxResolution) {
		this.maxResolution = maxResolution;
	}
}

/*
 * Copyright (C) 2009.
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
package uk.me.parabola.imgfmt.app.mdr;

/**
 * Holds information about a city that will make its way into mdr 5.
 * This class is used in several places as the information has to be gathered
 * from the cities section of LBL and the points in RGN.
 * 
 * @author Steve Ratcliffe
 */
public class Mdr5Record extends RecordBase implements NamedRecord {
	/** The city index within its own map */
	private int cityIndex;

	/** The index across all maps */
	private int globalCityIndex;

	private int regionIndex;
	private int lblOffset;
	private int stringOffset;
	private String name;
	private Mdr13Record region;
	private Mdr14Record country;
	private int[] mdr20;

	public int getCityIndex() {
		return cityIndex;
	}

	public void setCityIndex(int cityIndex) {
		this.cityIndex = cityIndex;
	}

	public int getGlobalCityIndex() {
		return globalCityIndex;
	}

	public void setGlobalCityIndex(int globalCityIndex) {
		this.globalCityIndex = globalCityIndex;
	}

	public int getRegionIndex() {
		return regionIndex;
	}

	public void setRegionIndex(int regionIndex) {
		this.regionIndex = regionIndex;
	}

	public int getLblOffset() {
		return lblOffset;
	}

	public void setLblOffset(int lblOffset) {
		this.lblOffset = lblOffset;
	}

	public int getStringOffset() {
		return stringOffset;
	}

	public void setStringOffset(int stringOffset) {
		this.stringOffset = stringOffset;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public Mdr13Record getMdrRegion() {
		return region;
	}

	public void setMdrRegion(Mdr13Record region) {
		this.region = region;
	}

	public Mdr14Record getMdrCountry() {
		return country;
	}

	public void setMdrCountry(Mdr14Record country) {
		this.country = country;
	}

	public int getMdr20() {
		return mdr20[globalCityIndex];
	}

	/**
	 * Every mdr5 record contains the same array of values. It is only
	 * allowed to access the one at the index globalCityIndex. Since
	 * the array is shared, every record with the same global city index
	 * knows the correct mdr20 value, regardless of where it was set.
	 * 
	 * @param mdr20 An array large enough to hold all the cities (one based index).
	 * This must be the same array for all mdr5records (in the same map set).
	 */
	public void setMdr20set(int[] mdr20) {
		this.mdr20 = mdr20;
	}

	public void setMdr20(int n) {
		int prev = mdr20[globalCityIndex];
		assert prev == 0 || prev == n : "mdr20 value changed";

		mdr20[globalCityIndex] = n;
	}

	/**
	 * Is this the same city, by the rules segregating the cities in mdr5 and 20.
	 */
	public boolean isSameCity(Mdr5Record other) {
		if (other == null)
			return false;

		return getMapIndex() == other.getMapIndex() && getName().equals(other.getName())
				&& getRegionName().equals(other.getRegionName())
				&& getCountryName().equals(other.getCountryName());
	}

	public String toString() {
		return String.format("%d: %s r=%s c=%s", globalCityIndex, name, getRegionName(), country.getName());
	}

	public String getRegionName() {
		if (region == null)
			return "";
		else
			return region.getName();
	}

	public String getCountryName() {
		return country.getName();
	}
}

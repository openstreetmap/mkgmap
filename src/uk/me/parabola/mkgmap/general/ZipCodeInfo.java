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
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.lbl.Zip;

public class ZipCodeInfo implements Comparable<ZipCodeInfo> {
	private static final String UNKNOWN = "?";
	private final String zipCode;
	private Zip imgZip;

	public ZipCodeInfo (String zipCode){
		this.zipCode = (zipCode != null) ? zipCode: UNKNOWN;
	}

	public String getZipCode() {
		if (zipCode == UNKNOWN)
			return null;
		return zipCode;
	}

	public void setImgZip(Zip zip){
		imgZip = zip;
	}
	
	
	public Zip getImgZip() {
		return imgZip;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((zipCode == null) ? 0 : zipCode.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof ZipCodeInfo))
			return false;
		ZipCodeInfo other = (ZipCodeInfo) obj;
		
		if (zipCode == null && other.zipCode != null )
			return false;
		return zipCode.equals(other.zipCode);
	}

	@Override
	public int compareTo(ZipCodeInfo o) {
		if (this == o)
			return 0;
		return zipCode.compareTo(o.zipCode);
	}

	@Override
	public String toString() {
		return zipCode; 
	}
}

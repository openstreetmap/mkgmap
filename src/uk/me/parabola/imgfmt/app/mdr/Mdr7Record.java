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
 * Holds details of a single street.
 * @author Steve Ratcliffe
 */
public class Mdr7Record extends RecordBase implements NamedRecord {
	private int labelOffset;
	private int stringOffset;
	private String name;
	private int index;
	private Mdr5Record city;

	// For searching on partial names
	private byte nameOffset; // offset into the name where matching should start
	private byte outNameOffset; // offset into the encoded output name
	private byte prefixOffset;  // offset after 0x1e prefix
	private byte suffixOffset;  // offset just before 0x1f suffix

	public int getLabelOffset() {
		return labelOffset;
	}

	public void setLabelOffset(int labelOffset) {
		this.labelOffset = labelOffset;
	}

	public int getStringOffset() {
		return stringOffset;
	}

	public void setStringOffset(int stringOffset) {
		this.stringOffset = stringOffset;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int getIndex() {
		return index;
	}

	public void setCity(Mdr5Record city) {
		this.city = city;
	}

	public Mdr5Record getCity() {
		return city;
	}

	public int getNameOffset() {
		return nameOffset & 0xff;
	}

	public void setNameOffset(byte nameOffset) {
		this.nameOffset = nameOffset;
	}

	public byte getOutNameOffset() {
		return outNameOffset;
	}

	public void setOutNameOffset(byte outNameOffset) {
		this.outNameOffset = outNameOffset;
	}

	public void setPrefixOffset(byte prefixOffset) {
		this.prefixOffset = prefixOffset;
	}

	public void setSuffixOffset(byte suffixOffset) {
		this.suffixOffset = suffixOffset;
	}

	/**
	 * Get the name starting at the given nameOffset.
	 *
	 * To avoid creating unnecessary objects, a check is made for an offset of zero
	 * and the original name string is returned.
	 *
	 * @return A substring of name, starting at the nameOffset value.
	 */
	public String getPartialName() {
		if (nameOffset == 0 && prefixOffset == 0 && suffixOffset == 0)
			return name;
		else if ((suffixOffset & 0xff) > 0)
			return name.substring((nameOffset & 0xff) + (prefixOffset & 0xff), (suffixOffset & 0xff));
		else
			return name.substring((nameOffset & 0xff) + (prefixOffset & 0xff));
	}

	public String toString() {
		return name + " in " + city.getName();
	}

	public String getInitialPart() {
		return name.substring(0, (nameOffset & 0xff));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
//		result = prime * result + ((city == null) ? 0 : city.hashCode());
		result = prime * result + labelOffset;
//		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + nameOffset;
		result = prime * result + outNameOffset;
		result = prime * result + prefixOffset;
		result = prime * result + stringOffset;
		result = prime * result + suffixOffset;
		result = prime * result + getMapIndex();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Mdr7Record other = (Mdr7Record) obj;
		if (labelOffset != other.labelOffset)
			return false;
		if (getMapIndex() != other.getMapIndex())
			return false;
		if (nameOffset != other.nameOffset)
			return false;
		if (outNameOffset != other.outNameOffset)
			return false;
		if (prefixOffset != other.prefixOffset)
			return false;
		if (stringOffset != other.stringOffset)
			return false;
		if (suffixOffset != other.suffixOffset)
			return false;
		if (city != other.getCity()) 
			return false;
//		if (name == null) {
//			if (other.name != null)
//				return false;
//		} else if (!name.equals(other.name))
//			return false;
		return true;
	}
}

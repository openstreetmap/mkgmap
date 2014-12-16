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

	public byte getNameOffset() {
		return nameOffset;
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

	public byte getPrefixOffset() {
		return prefixOffset;
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
		else if (suffixOffset > 0)
			return name.substring(nameOffset + prefixOffset, suffixOffset);
		else
			return name.substring(nameOffset + prefixOffset);
	}

	public String toString() {
		return name + " in " + city.getName();
	}
}

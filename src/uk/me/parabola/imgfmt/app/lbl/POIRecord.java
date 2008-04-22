/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * 
 * Author: Steve Ratcliffe
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.WriteStrategy;

/**
 * @author Steve Ratcliffe
 */
public class POIRecord {
	public static final byte HAS_STREET_NUM = 0x01;
	public static final byte HAS_STREET     = 0x02;
	public static final byte HAS_CITY       = 0x04;
	public static final byte HAS_ZIP        = 0x08;
	public static final byte HAS_PHONE      = 0x10;
	public static final byte HAS_EXIT       = 0x20;
	public static final byte HAS_TIDE_PREDICTION = 0x40;

	private static final AddrAbbr ABBR_HASH = new AddrAbbr(' ', "#");
	private static final AddrAbbr ABBR_APARTMENT = new AddrAbbr('1', "APT");
	private static final AddrAbbr ABBR_BUILDING = new AddrAbbr('2', "BLDG");
	private static final AddrAbbr ABBR_DEPT = new AddrAbbr('3', "DEPT");
	private static final AddrAbbr ABBR_FLAT = new AddrAbbr('4', "FL");
	private static final AddrAbbr ABBR_ROOM = new AddrAbbr('5', "RM");
	private static final AddrAbbr ABBR_STE = new AddrAbbr('6', "STE");  // don't know what this is?
	private static final AddrAbbr ABBR_UNIT = new AddrAbbr('7', "UNIT");

	private int offset = -1;
	private Label poiName;

	private int streetNumber;
	private Label streetName;
	private Label streetNumberName; // Used for numbers such as 221b

	private char cityIndex;
	private char zipIndex;

	private String phoneNumber;

	public void setLabel(Label label) {
		this.poiName = label;
	}

	void write(WriteStrategy writer, int realofs) {
		assert offset == realofs;
		writer.put3(poiName.getOffset());
		// not implemented yet
	}

	byte getPOIFlags() {
		byte b = 0;
		if (streetName != null)
			b |= HAS_STREET;
		return 0;
	}

	/**
	 * Sets the start offset of this POIRecord
	 *
	 * \return Number of bytes needed by this entry
	 */
	int calcOffset(int ofs) {
		offset = ofs;
		return 3;
	}

	public int getOffset() {
		if (offset == -1)
			throw new IllegalStateException("Offset not known yet.");
		return offset;
	}

	/**
	 * Address abbreviations.
	 */
	static class AddrAbbr {
		private final char code;
		private final String value;

		AddrAbbr(char code, String value) {
			this.code = code;
			this.value = value;
		}

		public String toString() {
			return value;
		}

		public char getCode() {
			return code;
		}
	}
}

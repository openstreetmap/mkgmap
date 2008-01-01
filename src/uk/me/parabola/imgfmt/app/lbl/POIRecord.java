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

/**
 * @author Steve Ratcliffe
 */
public class POIRecord {
	private static final AddrAbbr ABBR_HASH = new AddrAbbr(' ', "#");
	private static final AddrAbbr ABBR_APARTMENT = new AddrAbbr('1', "APT");
	private static final AddrAbbr ABBR_BUILDING = new AddrAbbr('2', "BLDG");
	private static final AddrAbbr ABBR_DEPT = new AddrAbbr('3', "DEPT");
	private static final AddrAbbr ABBR_FLAT = new AddrAbbr('4', "FL");
	private static final AddrAbbr ABBR_ROOM = new AddrAbbr('5', "RM");
	private static final AddrAbbr ABBR_STE = new AddrAbbr('6', "STE");  // don't know what this is?
	private static final AddrAbbr ABBR_UNIT = new AddrAbbr('7', "UNIT");

	private Label poiName;
	private byte propertyMask;

	private int streetNumber;
	private Label streetName;
	private Label streetNumberName; // Used for numbers such as 221b

	private char cityIndex;
	private char zipIndex;

	private String phoneNumber;

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

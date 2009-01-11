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
import uk.me.parabola.imgfmt.app.ImgFileWriter;

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

	private char cityIndex = 0;
	private char zipIndex  = 0;

	private String phoneNumber;

	public void setLabel(Label label) {
		this.poiName = label;
	}

	public void setStreetName(Label label) {
		this.streetName = label;
	}
	
	public void setZipIndex(int zipIndex)
	{
		this.zipIndex =  (char) zipIndex;
	}
	
	public void setCityIndex(int cityIndex)
	{
		this.cityIndex  = (char) cityIndex;
	}

	void write(ImgFileWriter writer, byte POIGlobalFlags, int realofs,
		   long numCities, long numZips) {
		assert offset == realofs;

		int ptr = poiName.getOffset();
		if (POIGlobalFlags != getPOIFlags())
			ptr |= 0x800000;
		writer.put3(ptr);

		if (POIGlobalFlags != getPOIFlags())
			writer.put(getWrittenPOIFlags(POIGlobalFlags));

		if (streetName != null)
			writer.put3(streetName.getOffset());
			
		if (cityIndex > 0)
		{
			if(numCities > 255)
 			  writer.putChar(cityIndex);
			else
			  writer.put((byte)cityIndex);
		}
			
		if (zipIndex > 0)
		{
			if(numZips > 255)
 			  writer.putChar(zipIndex);			
			else
			 writer.put((byte)zipIndex);
		}
	}

	byte getPOIFlags() {
		byte b = 0;
		if (streetName != null)
			b |= HAS_STREET;
		if (cityIndex > 0)
		        b |= HAS_CITY;
		if (zipIndex > 0)
		        b |= HAS_ZIP;			
		return b;
	}
	
	byte getWrittenPOIFlags(byte POIGlobalFlags) 
	{
	    int mask;
	    int flag = 0;
	    int j = 0;
	
	    int usedFields = getPOIFlags();
	
	    /* the local POI flag is really tricky
	       if a bit is not set in the global mask
	       we have to skip this bit in the local mask.
	       In other words the meaning of the local bits
	       change influenced by the global bits */
	
	    for(byte i = 0; i < 6; i++)
	    {
	    	mask =  1 << i;
		
		if((mask & POIGlobalFlags) == mask)
		{
		   if((mask & usedFields) == mask)
		     flag = flag | (1 << j);
		   j++;
		}
		
	    }
	    return (byte) flag;
	}

	/**
	 * Sets the start offset of this POIRecord
	 *
	 * \return Number of bytes needed by this entry
	 */
	int calcOffset(int ofs, byte POIGlobalFlags, long numCities, long numZips) {
		offset = ofs;
		int size = 3;
		if (POIGlobalFlags != getPOIFlags())
			size += 1;
		if (streetName != null)
			size += 3;
		if (cityIndex > 0) 
		{
			/*
			  depending on how many cities are in the LBL block we have
			  to write one or two bytes 
			*/
		
			if(numCities > 255)
	  		  size += 2;
			else
			  size += 1;
		}
		if (zipIndex > 0)
		{
			/*
			  depending on how many zips are in the LBL block we have
			  to write one or two bytes 
			*/
		
			if(numZips > 255)
			   size += 2;						
			else
			   size += 1;
		}
		return size;
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

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

import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Exit;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;

/**
 * @author Steve Ratcliffe
 */
public class POIRecord {

	static final int HAS_STREET_NUM = 0x01;
	static final int HAS_STREET     = 0x02;
	static final int HAS_CITY       = 0x04;
	static final int HAS_ZIP        = 0x08;
	static final int HAS_PHONE      = 0x10;
	static final int HAS_EXIT       = 0x20;
	static final int HAS_TIDE_PREDICTION = 0x40;

	/* Not used yet
	private static final AddrAbbr ABBR_HASH = new AddrAbbr(' ', "#");
	private static final AddrAbbr ABBR_APARTMENT = new AddrAbbr('1', "APT");
	private static final AddrAbbr ABBR_BUILDING = new AddrAbbr('2', "BLDG");
	private static final AddrAbbr ABBR_DEPT = new AddrAbbr('3', "DEPT");
	private static final AddrAbbr ABBR_FLAT = new AddrAbbr('4', "FL");
	private static final AddrAbbr ABBR_ROOM = new AddrAbbr('5', "RM");
	private static final AddrAbbr ABBR_STE = new AddrAbbr('6', "STE");  // don't know what this is?
	private static final AddrAbbr ABBR_UNIT = new AddrAbbr('7', "UNIT");
	*/

	private int offset = -1;
	private Label poiName;

	private final SimpleStreetPhoneNumber simpleStreetNumber = new SimpleStreetPhoneNumber();
	private final SimpleStreetPhoneNumber simplePhoneNumber = new SimpleStreetPhoneNumber();

	private Label streetName;
	private Label streetNumberName; // Used for numbers such as 221b
	private Label complexPhoneNumber; // Used for numbers such as 221b
	
	private City city;
	private Zip zip;
	private Exit exit;

	//private String phoneNumber;

	public void setLabel(Label label) {
		this.poiName = label;
	}

	public void setStreetName(Label label) {
		this.streetName = label;
	}
	
	public boolean setSimpleStreetNumber(String streetNumber) 
	{
		return simpleStreetNumber.set(streetNumber);
	}
	
	public void setComplexStreetNumber(Label label) 
	{
		streetNumberName = label;
	}
	
	public boolean setSimplePhoneNumber(String phone) 
	{
		return simplePhoneNumber.set(phone);
	}
	
	public void setComplexPhoneNumber(Label label) 
	{
		complexPhoneNumber = label;
	}
	
	
	public void setZip(Zip zip) {
		this.zip = zip;
	}

	public void setCity(City city)
	{
		this.city = city;
	}

	public void setExit(Exit exit) {
		this.exit = exit;
	}

	void write(ImgFileWriter writer, int POIGlobalFlags, int realofs,
		   long numCities, long numZips, long numHighways, long numExitFacilities) {
		assert offset == realofs : "offset = " + offset + " realofs = " + realofs;
		int ptr = poiName.getOffset();
		if (POIGlobalFlags != getPOIFlags())
			ptr |= 0x800000;
		writer.put3u(ptr);

		if (POIGlobalFlags != getPOIFlags())
			writer.put1u(getWrittenPOIFlags(POIGlobalFlags));

		if (streetNumberName != null)
		{
			int labOff = streetNumberName.getOffset();
			// !!! seems to write as hybrid endian
			writer.put1u(labOff >> 16);
			writer.put2u(labOff & 0xFFFF);
		}
		else if (simpleStreetNumber.isUsed())
			simpleStreetNumber.write(writer);

		if (streetName != null)
			writer.put3u(streetName.getOffset());

		if (city != null)
			writer.putNu(Utils.numberToPointerSize((int)numCities), city.getIndex());

		if (zip != null)
			writer.putNu(Utils.numberToPointerSize((int)numZips), zip.getIndex());

		if (complexPhoneNumber != null)
		{
			int labOff = complexPhoneNumber.getOffset();
			// !!! seems to write as hybrid endian
			writer.put1u(labOff >> 16);
			writer.put2u(labOff & 0xFFFF);
		}
		else if (simplePhoneNumber.isUsed())
			simplePhoneNumber.write(writer);

		if(exit != null) {
			Label description = exit.getDescription();
			int val = 0;
			if(description != null) {
				val = description.getOffset();
				assert val < 0x400000 : "Exit description label offset too large";
			}
			if(exit.getOvernightParking())
				val |= 0x400000;
			List<ExitFacility> facilites = exit.getFacilities();
			ExitFacility ef = null;
			if(!facilites.isEmpty())
				ef = facilites.get(0);
			if(ef != null)
				val |= 0x800000; // exit facilities defined
			writer.put3u(val);

			int highwayIndex = exit.getHighway().getIndex();
			writer.putNu(Utils.numberToPointerSize((int)numHighways), highwayIndex);

			if(ef != null) {
				int exitFacilityIndex = ef.getIndex();
				writer.putNu(Utils.numberToPointerSize((int)numExitFacilities), exitFacilityIndex);
			}
		}
	}

	int getPOIFlags() {
		int b = 0;
		if (streetName != null)
			b |= HAS_STREET;
		if (simpleStreetNumber.isUsed() || streetNumberName != null)
			b |= HAS_STREET_NUM;
		if (city != null)
		        b |= HAS_CITY;
		if (zip != null)
		        b |= HAS_ZIP;
		if (simplePhoneNumber.isUsed() || complexPhoneNumber != null)
			b |= HAS_PHONE;
		if (exit != null)
			b |= HAS_EXIT;
		return b;
	}
	
	int getWrittenPOIFlags(int POIGlobalFlags)
	{
		int flag = 0;
		int j = 0;
	
		int usedFields = getPOIFlags();
	
		/* the local POI flag is really tricky if a bit is not set in the global mask
					we have to skip this bit in the local mask. In other words the meaning of the local bits
					change influenced by the global bits */
	
		for(byte i = 0; i < 6; i++)
		{
			int mask = 1 << i;

			if((mask & POIGlobalFlags) == mask)
			{
				if((mask & usedFields) == mask)
					flag |= (1 << j);
				j++;
			}
		
		}

		flag |= 0x80; // gpsmapedit asserts for this bit set
	    
		return flag;
	}

	/**
	 * Sets the start offset of this POIRecord
	 *
	 * @return Number of bytes needed by this entry
	 */
	int calcOffset(int ofs, int POIGlobalFlags, long numCities, long numZips, long numHighways, long numExitFacilities) {
		offset = ofs;
		int size = 3;
		if (exit != null) {
			size += 3;
			size += Utils.numberToPointerSize((int)numHighways);
			if(!exit.getFacilities().isEmpty())
				size += Utils.numberToPointerSize((int)numExitFacilities);
		}
		if (POIGlobalFlags != getPOIFlags())
			size += 1;
		if (simpleStreetNumber.isUsed())		
			size += simpleStreetNumber.getSize();
		if (streetNumberName != null)
			size += 3;
		if (simplePhoneNumber.isUsed())		
			size += simplePhoneNumber.getSize();			
		if (complexPhoneNumber != null)
			size += 3;			
		if (streetName != null)
			size += 3;	
		if (city != null) 
		{
			/*
			  depending on how many cities are in the LBL block we have
			  to write one to three bytes 
			*/
			size += Utils.numberToPointerSize((int)numCities);
		}
		if (zip != null) {
			// depending on how many zips are in the LBL block we have to write one to three bytes
			size += Utils.numberToPointerSize((int)numZips);
		}
		return size;
	}

	public int getOffset() {
		if (offset == -1)
			throw new IllegalStateException("Offset not known yet.");
		return offset;
	}

	public Label getNameLabel() {
		return poiName;
	}

	public City getCity() {
		return city;
	}

	/**
	 * Street and Phone numbers can be stored in two different ways in the poi record
	 * Simple Number that only contain digits are coded in base 11 coding.
	 * This helper class tries to code the given number. If the number contains other
	 * chars like in 4a the coding fails and the caller has to use a Label instead
	 */
	class SimpleStreetPhoneNumber {

		private byte[] encodedNumber;
		private int  encodedSize;

		/**
		 * Encode a string as base 11.
		 * @param str The input string.
		 * @return If the string is not all numeric (or A) then false is returned
		 * and this object is invalid.
		 */
		public boolean set(String str) {

			// remove surrounding whitespace to increase chance for simple encoding
			String number = str.trim();

			encodedNumber  = new byte[(number.length()/2)+2];

			int i = 0;
			int j = 0;
			while (i < number.length()) {

				int c1 = decodeChar(number.charAt(i++));

				int c2;
				if (i < number.length()) {
					c2 = decodeChar(number.charAt(i++));
				} else
					c2 = 10;

				// Only 0-9 and - allowed
				if (c1 < 0 || c1 > 10 || c2 < 0 || c2 > 10)
					return false;

				// Encode as base 11
				int val = c1 * 11 + c2;

				// first byte needs special marking with 0x80
				// If this is not set would be treated as label pointer
				if (j == 0)
					val |= 0x80;

				encodedNumber[j++] = (byte)val;
			}
			if (j == 0)
				return false;

			if (j == 1)
				encodedNumber[j++] = (byte) 0xf8;
			else
				encodedNumber[j-1] |= 0x80;
			encodedSize  = j;

			return true;
		}

		public void write(ImgFileWriter writer)
		{
//			for(int i = 0; i < encodedSize; i++)
//				writer.put1u(encodedNumber[i]);
			writer.put(encodedNumber, 0, encodedSize);
		}

		public boolean isUsed()
		{
			return (encodedSize > 0);
		}

		public int getSize()
		{
			return encodedSize;
		}

		/**
		 * Convert the characters '0' to '9' and '-' to a number 0-10 (base 11).
		 * @param ch The character to convert.
		 * @return A number between 0 and 10 or -1 if the character is not valid.
		 */
		private int decodeChar(char ch) {
			if (ch == '-')
				return 10;
			else if (ch >= '0' && ch <= '9')
				return (ch - '0');
			else
				return -1;
		}
	}	
}

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

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.Exit;

/**
 * @author Steve Ratcliffe
 */
public class POIRecord {

	class SimpleStreetPhoneNumber // Helper Class to encode Street Phone Numbers
	{
		/**
			Street and Phone numbers can be stored in two different ways in the poi record
			Simple Number that only contain digits are coded in base 11 coding. 
			This helper	class tries to code the given number. If the number contains other
			chars like in 4a the coding fails and the caller has to use a Label instead
		*/
	
		private byte[] encodedNumber;
		private int  encodedSize;
		
		public boolean set(String number)
		{
			int i = 0;
			int j = 0;
			int val = 0;

			// remove sourounding whitespaces to increase chance for simple encoding

			number = number.trim();  

			encodedNumber  = new byte[(number.length()/2)+2];
				
			while(i < number.length())
			{
				int c1;
				int c2;

				c1 = decodeChar(number.charAt(i));
				i++;
 
				if(i < number.length())
				{
					c2 = decodeChar(number.charAt(i));
					i++;
				}
				else
					c2 = 10;
	
				if(c1 < 0 || c1 > 10 || c2 < 0 || c2 > 10) // Only 0-9 and - allowed
				{
					return false;
				}

				val = c1 * 11 + c2;  							// Encode as base 11
 
				if(i < 3 || i >= number.length())  // first byte needs special marking with 0x80
					val |= 0x80;							 // If this is not set would be treated as label pointer

				encodedNumber[j++] = (byte)val;	
			}
			
			if((val & 0x80) == 0 || i < 3)  // terminate string with 0x80 if not done 
			{
				val = 0xF8;
				encodedNumber[j++] = (byte)val;
			}
			
			encodedSize  = j;
			
			return true;
		}
		
		public void write(ImgFileWriter writer)
		{
			for(int i = 0; i < encodedSize; i++)
				writer.put(encodedNumber[i]);
		}
		
		public boolean isUsed()
		{
			return (encodedSize > 0);
		}
		
		public int getSize()	
		{
			return encodedSize;
		}
		
		private int decodeChar(char ch)
		{		
			return (ch - '0');
		}
		
	}

	private static final byte HAS_STREET_NUM = 0x01;
	private static final byte HAS_STREET     = 0x02;
	private static final byte HAS_CITY       = 0x04;
	private static final byte HAS_ZIP        = 0x08;
	private static final byte HAS_PHONE      = 0x10;
	private static final byte HAS_EXIT       = 0x20;
	private static final byte HAS_TIDE_PREDICTION = 0x40;

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
	private char zipIndex;
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
	
	
	public void setZipIndex(int zipIndex)
	{
		this.zipIndex =  (char) zipIndex;
	}
	
	public void setCity(City city)
	{
		this.city = city;
	}

	public void setExit(Exit exit) {
		this.exit = exit;
	}

	void write(ImgFileWriter writer, byte POIGlobalFlags, int realofs,
		   long numCities, long numZips, long numHighways, long numExitFacilities) {
		assert offset == realofs : "offset = " + offset + " realofs = " + realofs;
		int ptr = poiName.getOffset();
		if (POIGlobalFlags != getPOIFlags())
			ptr |= 0x800000;
		writer.put3(ptr);

		if (POIGlobalFlags != getPOIFlags())
			writer.put(getWrittenPOIFlags(POIGlobalFlags));

		if (streetNumberName != null)
		{
			int labOff = streetNumberName.getOffset();
			writer.put((byte)((labOff & 0x7F0000) >> 16));
			writer.putChar((char)(labOff & 0xFFFF));
		}
		else if (simpleStreetNumber.isUsed())
			simpleStreetNumber.write(writer);

		if (streetName != null)
			writer.put3(streetName.getOffset());

		if (city != null)
		{
			char cityIndex = (char) city.getIndex();
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

		if (complexPhoneNumber != null)
		{
			int labOff = complexPhoneNumber.getOffset();
			writer.put((byte)((labOff & 0x7F0000) >> 16));
			writer.putChar((char)(labOff & 0xFFFF));
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
			if(facilites.size() > 0)
				ef = facilites.get(0);
			if(ef != null)
				val |= 0x800000; // exit facilites defined
			writer.put3(val);
			char highwayIndex = (char)exit.getHighway().getIndex();
			if(numHighways > 255)
				writer.putChar(highwayIndex);
			else
				writer.put((byte)highwayIndex);
			if(ef != null) {
				char exitFacilityIndex = (char)ef.getIndex();
				if(numExitFacilities > 255)
					writer.putChar(exitFacilityIndex);
				else
					writer.put((byte)exitFacilityIndex);
			}
		}
	}

	byte getPOIFlags() {
		byte b = 0;
		if (streetName != null)
			b |= HAS_STREET;
		if (simpleStreetNumber.isUsed() || streetNumberName != null)
			b |= HAS_STREET_NUM;
		if (city != null)
		        b |= HAS_CITY;
		if (zipIndex > 0)
		        b |= HAS_ZIP;
		if (simplePhoneNumber.isUsed() || complexPhoneNumber != null)
			b |= HAS_PHONE;
		if (exit != null)
			b |= HAS_EXIT;
		return b;
	}
	
	byte getWrittenPOIFlags(byte POIGlobalFlags) 
	{
			int mask;
			int flag = 0;
			int j = 0;
	
			int usedFields = getPOIFlags();
	
			/* the local POI flag is really tricky if a bit is not set in the global mask
					we have to skip this bit in the local mask. In other words the meaning of the local bits
					change influenced by the global bits */
	
			for(byte i = 0; i < 6; i++)
			{
				mask =  1 << i;

				if((mask & POIGlobalFlags) == mask)
				{
					if((mask & usedFields) == mask)
						flag |= (1 << j);
					j++;
				}
		
			}

		flag |= 0x80; // gpsmapedit asserts for this bit set
	    
		return (byte) flag;
	}

	/**
	 * Sets the start offset of this POIRecord
	 *
	 * \return Number of bytes needed by this entry
	 */
	int calcOffset(int ofs, byte POIGlobalFlags, long numCities, long numZips, long numHighways, long numExitFacilities) {
		offset = ofs;
		int size = 3;
		if (exit != null) {
			size += 3;
			size += (numHighways > 255)? 2 : 1;
			if(exit.getFacilities().size() > 0)
				size += (numExitFacilities > 255)? 2 : 1;
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

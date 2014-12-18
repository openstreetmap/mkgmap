/*
 * Copyright (c) 2013.
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
package uk.me.parabola.mkgmap.osmstyle.actions;

import java.util.regex.Pattern;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * Split a value in parts and returns one or more part(s) of a value.
 * value is split at a separator that defaults to semicolon ';'
 * by default the first part is returned
 *
 * if the optional second parameter 'partnumber' is out of
 * range then null is returned
 *
 * if the optional second parameter is negative
 * the part returned is counted from the end of the split
 *
 * if the the split operator is a > or < and the
 * the correspondent number of parts are returned
 *
 * Example: if the value is "Aa#Bb#Cc#Dd#Ee"
 * part:#:1  returns Aa
 * part:#:-1 returns Ee
 * part:#:2  returns Bb
 * part:#:-2 returns Dd
 * part:#>1  returns Bb#Cc#Dd#Ee#
 * part:#<5  returns Aa#Bb#Cc#Dd#
 * part:#<-1 returns Aa#Bb#Cc#Dd#
 *
 * @author Franco Bez
 * @author Enrico Liboni
 */
public class PartFilter extends ValueFilter {
	private String separator;
	private int partnumber;
	private boolean isLt=false; // less than  <
	private boolean isGt=false; // great than >

	public PartFilter(String arg) {
		String[] temp;

		// detect which operator is used
		if (arg.contains(":")) {
			temp = arg.split(":");
		} else if (arg.contains(">")) {
			temp = arg.split(">");
			isGt = true;
		} else if (arg.contains("<")) {
			temp = arg.split("<");
			isLt = true;
		} else { // no operators default to arg
			temp =  new String[] { arg };
		}

		partnumber = 1;
		try {
			// set the part number (default is 1)
			if( temp.length > 1 ) {
				partnumber = Integer.parseInt(temp[1]);
			}
			// set the separator (default to ;)
			if(temp[0].length() > 0 ){
				separator = temp[0];
			}
			else{
				separator = ";";
			}
		} catch (NumberFormatException e) {
			throw new SyntaxException("Not valid numbers in style part command: " + arg);
		}
		if (partnumber == 0)
			throw new SyntaxException("Not valid numbers in style part command: " + arg);
	}

	public String doFilter(String value, Element el) {
		if (value == null || partnumber == 0) return null;
		// split uses a regex we need to replace special characters
		String[] temp = value.split(Pattern.quote(separator));

		// check if partnumber is in range, if not return null
		if (temp.length < Math.abs(partnumber) ) return null;

		// get the index of the partnumber
		// if the partnumber is negative the part is counted from the end of the split
		int idx=(partnumber > 0)?(partnumber-1):(temp.length+partnumber);

		// default operator ":": return the part
		if ( !isLt && !isGt ) {
			return temp[idx].trim();
		} else {
			StringBuffer returnValue= new StringBuffer();

			// operator "<": collate all the parts before the partnumber
			if ( isLt ) {
				for (int i=0;i<idx;i++) {
					returnValue.append(temp[i]).append(separator);
				}
			}

			// operator ">": collate all the parts after the partnumber
			if ( isGt ) {
				for (int i=idx+1;i<temp.length;i++) {
					returnValue.append(temp[i]).append(separator);
				}
			}

			// return the result
			return returnValue.toString();
		}
	}
}

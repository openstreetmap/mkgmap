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
 * Create date: 04-Nov-2007
 */
package uk.me.parabola.imgfmt.app.labelenc;

import java.io.PrintStream;

/**
 * Call this with a unicode row number and it will produce an empty table
 * that can be modified.
 *
 * @author Steve Ratcliffe
 */
public class TableCreator {
	public static void main(String[] args) {
		int row = Integer.parseInt(args[0]);

		PrintStream out = System.out;

		out.println("");
		out.println("# This is table for transliterating characters in the range");
		out.format( "# from U+%02x00 to U+%02xff\n", row, row);
		out.println("#");
		out.println("# The first column is the unicode character and the second");
		out.println("# column is the transliteration of that character to ascii characters.");
		out.println("# One or more characters can be used, for example for a character æ which");
		out.println("# is a combined a and e you could write 'ae' (without the quotes) as the ");
		out.println("# transliteration.");
		out.println("#");
		out.println("# There are languages where this will not work very well, in case");
		out.println("# another approach should be tried.");
		out.println("#");
		out.println("# Any line can be deleted and will default to a '?' character");
		out.println("#");
		out.println("");

		for (int i = 0; i < 256; i++) {
			out.format("U+%02x%02x   ?        # character %c\n",
					row, i, (char) (row*256+i));
		}
	}
}

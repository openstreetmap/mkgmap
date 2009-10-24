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
package uk.me.parabola.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

import uk.me.parabola.imgfmt.Utils;

import com.ibm.icu.text.Transliterator;

/**
 * Call this with a unicode row number and it will produce an empty table
 * that can be modified.
 *
 * @author Steve Ratcliffe
 */
public class TableIcuCreator {
	private static Transliterator trans;
	private static Transliterator decomposed;

	public static void main(String[] args) {
		int count = 0;
		Enumeration<String> targets = Transliterator.getAvailableIDs();
		while (targets.hasMoreElements()) {
			String s = (String) targets.nextElement();
			System.out.println(s);
			count++;
		}
		
		System.out.println("number " + count);
		//System.exit(0);
		//trans = Transliterator.getInstance("Any-en_US; nfd; [\u0301\u0302\u0304\u0306\u0307\u0308\u030c\u0328] remove; nfc"); // [:nonspacing mark:] remove; nfc");

		trans = Transliterator.getInstance("Any-Latin"); // [:nonspacing mark:] remove; nfc");
		decomposed = Transliterator.getInstance("Any-Latin; nfd"); // [:nonspacing mark:] remove; nfc");

		for (int row = 0; row < 256; row++) {
			String name = String.format("row%02x.trans", row);
			PrintWriter out = null;
			try {
				out = new PrintWriter(new FileWriter(name));
				printRow(out, row);
			} catch (IOException e) {
				System.out.println("Could not open " + name + " for write");
			} catch (UselessException e) {
				//System.out.println("Deleting " + name);
				File f = new File(name);
				f.delete();
			} finally {
				Utils.closeFile(out);
			}
		}
	}

	private static void printRow(PrintWriter out, int row) throws UselessException {
		//out.println("");
		//out.println("# This is table for transliterating characters in the range");
		//out.format( "# from U+%02x00 to U+%02xff\n", row, row);
		//out.println("#");
		//out.println("# The first column is the unicode character and the second");
		//out.println("# column is the transliteration of that character to ascii characters.");
		//out.println("# One or more characters can be used, for example for a character æ which");
		//out.println("# is a combined a and e you could write 'ae' (without the quotes) as the ");
		//out.println("# transliteration.");
		//out.println("#");
		//out.println("# There are languages where this will not work very well, in case");
		//out.println("# another approach should be tried.");
		//out.println("#");
		//out.println("# Any line can be deleted and will default to a '?' character");
		//out.println("#");
		//out.println("");

		int count = 0;
		for (int i = 0; i < 256; i++) {
			char c = (char) ((row << 8) + i);
			String single = "" + c;
			String result = trans.transliterate(single);

			if (result.length() == 1 && result.charAt(0) == c)
				result = "?";
			else
				count++;

			boolean inRange = true;
			for (char rc : result.toCharArray()) {
				if (rc > 0xff) {
					//System.out.printf("out of range result %c for row %d\n", rc, row);
					inRange = false;
					break;
				}
			}

			if (!inRange) {
				count--;
				out.print("#");
			}

			out.format("U+%02x%02x %-12.12s # Character %s", row, i, result, single);

			if (!inRange) {
				String s = decomposed.transliterate(single);
				out.format(", %s", s);
				for (char rc : s.toCharArray()) {
					out.format(" %04x", (int) rc);
				}
			}
			out.println();
		}

		if (count == 0)
			throw new UselessException();
	}

	private static class UselessException extends Exception {
	}
}
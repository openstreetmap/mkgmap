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

package uk.me.parabola.imgfmt.app.labelenc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import uk.me.parabola.log.Logger;

/**
 * A simple transliterator that transliterates character by character based
 * on pre-prepared tables.  It is not context sensitive - the same input character
 * always produces the same output character(s), so the results are
 * not very good for languages where that is important.
 *
 * Tabels are only read when needed, so for a typical map only a small
 * number of files will actually be read.
 */
public class TableTransliterator implements Transliterator {
	private static final Logger log = Logger.getLogger(TableTransliterator.class);

	private final String[][] rows = new String[256][];
	private final boolean useLatin;

	public TableTransliterator(String targetCharset) {
		if (targetCharset.equals("latin1"))
			useLatin = true;
		else
			useLatin = false;
	}

	/**
	 * Convert a string into a string that uses only ascii characters.
	 *
	 * @param s The original string.  It can use any unicode character.
	 * @return A string that uses only ascii characters that is a transcription or
	 *         transliteration of the original string.
	 */
	public String transliterate(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 5);
		for (char c : s.toCharArray()) {
			if (c <= (useLatin? 0xff: 0x7f)) {
				sb.append(c);
			} else {
				int row = c >>> 8;

				String[] rowmap = rows[row];
				if (rowmap == null)
					rowmap = loadRow(row);
				sb.append(rowmap[c & 0xff]);
			}
		}

		return sb.toString();
	}

	/**
	 * Load one row of characters.  This means unicode characters that are of the
	 * form U+RRXX where RR is the row.
	 *
	 * @param row Row number 0-255.
	 * @return An array of strings, one for each character in the row.  If there is
	 *         no ascii representation then a '?' character will fill that
	 *         position.
	 */
	private String[] loadRow(int row) {
		if (rows[row] != null)
			return rows[row];

		String[] newRow = new String[256];
		rows[row] = newRow;

		// Default all to a question mark
		Arrays.fill(newRow, "?");

		// If we are doing latin1, see if there is a specific file for latin
		// characters first.
		if (useLatin) {
			String name = String.format("/chars/latin1/row%02x.trans", row);
			readCharFile(name, newRow);
		}

		// Fill in any remaining characters from the ascii mappings.
		String name = String.format("/chars/ascii/row%02x.trans", row);
		readCharFile(name, newRow);

		return newRow;
	}

	private void readCharFile(String name, String[] newRow) {
		InputStream is = getClass().getResourceAsStream(name);
		if (is == null)
			return;

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));

			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0 || line.charAt(0) == '#')
					continue;

				String[] fields = line.split("\\s+");
				String upoint = fields[0];
				String translation = fields[1];

				if ("?".equals(translation)) continue;
				if (upoint.length() != 6 || upoint.charAt(0) != 'U') continue;

				// The first field must look like 'U+RRXX', we extract the XX part
				int index = Integer.parseInt(upoint.substring(4), 16);
				if (newRow[index].equals("?"))
					newRow[index] = translation;
			}
		} catch (IOException e) {
			log.error("Could not read character translation table");
		}
	}

}

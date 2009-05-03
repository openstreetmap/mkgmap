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
 * Create date: 14-Jan-2007
 */
package uk.me.parabola.imgfmt.app.labelenc;

import uk.me.parabola.log.Logger;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Useful routines for the other encoders.
 * Provides some default behaviour when a conversion is not possible for
 * example.
 *
 * @author Steve Ratcliffe
 */
public class BaseEncoder {
	private static final Logger log = Logger.getLogger(BaseEncoder.class);

	protected static final EncodedText NO_TEXT = new EncodedText(null, 0);

	private boolean charsetSupported = true;

	// Whether to uppercase the labels or not.  Default is true because many
	// GPS devices do not display labels in lower case.
	private boolean upperCase;

	private String charset = "ascii";
	private static final String[][] rows = new String[256][];

	protected boolean isCharsetSupported() {
		return charsetSupported;
	}

	protected void prepareForCharacterSet(String name) {
		if (Charset.isSupported(name)) {
			charsetSupported = true;
		} else {
			charsetSupported = false;
			log.warn("requested character set not found " + name);
		}
	}

	protected EncodedText simpleEncode(String text) {
		if (text == null)
			return NO_TEXT;
		
		char[] in = text.toCharArray();
		byte[] out = new byte[in.length + 1];

		int off = 0;
		for (char c : in)
			out[off++] = (byte) (c & 0xff);

		return new EncodedText(out, out.length);
	}

	protected boolean isUpperCase() {
		return upperCase;
	}

	public void setUpperCase(boolean upperCase) {
		this.upperCase = upperCase;
	}

	/**
	 * Convert a string into a string that uses only ascii characters.
	 *
	 * @param s The original string.  It can use any unicode character.
	 * @return A string that uses only ascii characters that is a transcription
	 * or transliteration of the original string.
	 */
	protected char[] transliterate(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 5);
		for (char c : s.toCharArray()) {
			if (c < 0x80) {
				sb.append(c);
			} else {
				int row = c >>> 8;

				String[] rowmap = rows[row];
				if (rowmap == null)
					rowmap = loadRow(row);
				//log.debug("char", Integer.toHexString(c), rowmap[c & 0xff]);
				sb.append(rowmap[c & 0xff]);
			}
		}

		return sb.toString().toCharArray();
	}

	/**
	 * Load one row of characters.  This means unicode characters that are
	 * of the form U+RRXX where RR is the row.
	 * @param row Row number 0-255.
	 * @return An array of strings, one for each character in the row.  If there
	 * is no ascii representation then a '?' character will fill that
	 * position.
	 */
	private String[] loadRow(int row) {
		if (rows[row] != null)
			return rows[row];

		String[] newRow = new String[256];
		rows[row] = newRow;

		// Default all to a question mark
		Arrays.fill(newRow, "?");

		charset = "ascii";
		StringBuilder name = new StringBuilder();
		Formatter fmt = new Formatter(name);
		fmt.format("/chars/%s/row/%02d.trans", charset, row);
		log.debug("getting file name", name);
		InputStream is = getClass().getResourceAsStream(name.toString());

		try {
			readCharFile(is, newRow);
		} catch (IOException e) {
			log.error("Could not read character translation table");
		}

		return newRow;
	}

	/**
	 * Read in a character translit file.  Not all code points need to
	 * be defined inside the file.  Anything that is left out will become
	 * a question mark.
	 *
	 * @param is The open file to be read.
	 * @param newRow The row that we fill in with strings.
	 */
	private void readCharFile(InputStream is, String[] newRow) throws IOException {
		if (is == null)
			return;

		BufferedReader br = new BufferedReader(new InputStreamReader(is, "ascii"));

		String line;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0 || line.charAt(0) == '#')
				continue;

			String[] fields = line.split("\\s+");
			String upoint = fields[0];
			String translation = fields[1];
			if (upoint.length() != 6 || upoint.charAt(0) != 'U') continue;

			// The first field must look like 'U+RRXX', we extract the XX part
			int index = Integer.parseInt(upoint.substring(4), 16);
			newRow[index] = translation.toUpperCase(Locale.ENGLISH);
		}
	}
}

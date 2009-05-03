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

import java.util.Locale;

import uk.me.parabola.log.Logger;

/**
 * Format according to the '6 bit' .img format.  The text is first upper
 * cased.  Any letter with a diacritic or accent is replaced with its base
 * letter.
 *
 * For example Körnerstraße would become KORNERSTRASSE,
 * Řípovská would become RIPOVSKA etc.
 *
 * I believe that some Garmin units are only capable of showing uppercase
 * ascii characters, so this will be the default.
 *
 * @author Steve Ratcliffe
 * @see <a href="http://garmin-img.sf.net">Garmin IMG File Format</a>
 */
public class Format6Encoder extends BaseEncoder implements CharacterEncoder {
	private static final Logger log = Logger.getLogger(Format6Encoder.class);

	// This is 0x1b is the source document, but the accompianing code uses
	// the value 0x1c, which seems to work.
	private static final int SYMBOL_SHIFT = 0x1c;

	public static final String LETTERS =
		" ABCDEFGHIJKLMNO" +	// 0x00-0x0F
		"PQRSTUVWXYZxx   " +	// 0x10-0x1F
		"0123456789xxxxxx";	// 0x20-0x2F

	public static final String SYMBOLS =
		"@!\"#$%&'()*+,-./" +	// 0x00-0x0F
		"xxxxxxxxxx:;<=>?" +	// 0x10-0x1F
		"xxxxxxxxxxx[\\]^_";	// 0x20-0x2F


	/**
	 * Encode the text into the 6 bit format.  See the class level notes.
	 *
	 * @param text The original text, which can contain non-ascii characters.
	 * @return Encoded form of the text.  Only uppercase ascii characters and
	 * some escape sequences will be present.
	 */
	public EncodedText encodeText(String text) {
		if (text == null || text.length() == 0)
			return NO_TEXT;

		String s = text.toUpperCase(Locale.ENGLISH);

		byte[] buf = new byte[2 * s.length() + 1];
		int off = 0;
		for (char c : transliterate(s)) {

			if (c == ' ') {
				buf = put6(buf, off++, 0);
			} else if (c >= 'A' && c <= 'Z') {
				buf = put6(buf, off++, c - 'A' + 1);
			} else if (c >= '0' && c <= '9') {
				buf = put6(buf, off++, c - '0' + 0x20);
			} else if (c >= 0x1d && c <= 0x1f) {
				put6(buf, off++, c);
			} else {
				off = shiftedSymbol(buf, off, c);
			}
		}

		buf = put6(buf, off++, 0xff);

		int len = ((off - 1) * 6) / 8 + 1;

		return new EncodedText(buf, len);
	}

	/**
	 * Certain characters have to be represented by two 6byte quantities.  This
	 * routine sorts these out.
	 *
	 * @param buf The buffer to write into.
	 * @param startOffset The offset to start writing to in the output buffer.
	 * @param c The character that we are decoding.
	 * @return The final offset.  This will be unchanged if there was nothing
	 * writen because the character does not have any representation.
	 */
	private int shiftedSymbol(byte[] buf, int startOffset, char c) {
		int off = startOffset;
		int ind = SYMBOLS.indexOf(c);
		if (ind >= 0) {
			put6(buf, off++, SYMBOL_SHIFT);
			put6(buf, off++, ind);
		}
		return off;
	}

	/**
	 * Each character is packed into 6 bits.  This keeps track of everything so
	 * that the character can be put into the right place in the byte array.
	 *
	 * @param buf The buffer to populate.
	 * @param off The character offset, that is the number of the six bit
	 * character.
	 * @param c The character to place.
	 */
	private byte[] put6(byte[] buf, int off, int c) {
		int bitOff = off * 6;

		// The byte offset
		int byteOff = bitOff/8;

		// The offset within the byte
		int shift = bitOff - 8*byteOff;

		int mask = 0xfc >> shift;
		buf[byteOff] |= ((c << 2) >> shift) & mask;

		// IF the shift is greater than two we have to put the rest in the
		// next byte.
		if (shift > 2) {
			mask = 0xfc << (8 - shift);
			buf[byteOff + 1] = (byte) (((c << 2) << (8 - shift)) & mask);
		}

		return buf;
	}

}

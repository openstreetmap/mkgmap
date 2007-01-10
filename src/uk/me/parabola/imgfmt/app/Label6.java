/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 09-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import java.io.UnsupportedEncodingException;


/**
 * This is the 6 bit format for text labels.  You can cram 4 characters into
 * 3 bytes using this format and there is a flag consisting of the top two bits
 * that mark the end of a string, so that doesn't take up any extra space.
 *
 * On the downside its really only for uppercase ascii characters, although
 * there are escape sequences for lowercase letters.
 * 
 * @author Steve Ratcliffe
 */
class Label6 extends Label {

	// This is 0x1b is the source document, but the accompianing code uses
	// the value 0x1c, which seems to work.
	private static final int SYMBOL_SHIFT = 0x1c;

	/**
	 * Create a new label.
	 * @param text The normal text of the label.
	 */
	Label6(String text) {
		if (text != null)
			ctext = compressText6(text);
	}


	/**
	 * Compress the string to its 6 bit form.
	 *
	 * @param text The original text.
	 * @return The encoded text.  There may be extra bytes on the end so
	 * you have to look at the length field.
	 */
	private byte[] compressText6(String text) {
		String s = text.toUpperCase();

		byte[] buf = new byte[s.length()+1];
		int off = 0;
		for (char c : s.toCharArray()) {
			if (c == ' ') {
				put6(buf, off++, 0);
			} else if (c >= 'A' && c <= 'Z') {
				put6(buf, off++, c - 'A' + 1);
			} else if (c >= '0' && c <= '9') {
				put6(buf, off++, c - '0' + 0x20);
			} else {
				int ind = "@!\"#$%&'()*+,-./".indexOf(c);
				if (ind >= 0) {
					log.debug("putting " + ind);
					put6(buf, off++, SYMBOL_SHIFT);
					put6(buf, off++, ind);
				} else {
					ind = ":;<=>?".indexOf(c);
					if (ind >= 0) {
						put6(buf, off++, SYMBOL_SHIFT);
						put6(buf, off++, 0x1a + ind);
					} else {
						ind = "[\\]^_".indexOf(c);
						if (ind >= 0) {
							put6(buf, off++, SYMBOL_SHIFT);
							put6(buf, off++, 0x2b + ind);
						}
					}
				}
			}
			// else if ... more TODO: other characters
		}

		put6(buf, off++, 0xff);
		this.length = ((off-1) * 6)/8 + 1;
		dumpBuf(buf);
		return buf;
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
	private void put6(byte[] buf, int off, int c) {
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
	}

}

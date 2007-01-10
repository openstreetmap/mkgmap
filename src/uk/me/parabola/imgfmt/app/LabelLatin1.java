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
 * Create date: 10-Jan-2007
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
class LabelLatin1 extends Label {

	/**
	 * Create a new label.
	 * @param text The normal text of the label.
	 */
	LabelLatin1(String text) {
		if (text != null)
			ctext = encodeText(text);
	}

	/**
	 * 'Compress' the text to its 8 bit form.  Well there appears to be no
	 * complication to it.  Just output the bytes followed by a null byte.
	 *
	 * @param text The text to convert to 8bit format.
	 * @return A set of bytes representing the string.
	 */
	private byte[] encodeText(String text) {
		try {
			byte[] res = new byte[text.length()+1];

			// Guess that 8859-1 is used in the Garmin.
			byte[] bytes = text.toUpperCase().getBytes("iso-8859-1");
			System.arraycopy(bytes, 0, res, 0, text.length());
			this.length = res.length;
			return res;
		} catch (UnsupportedEncodingException e) {
			// TODO: don't do this everytime.
			log.error("could not convert the character set");
			// Just return truncated values.  This may, or may not, be better
			// than nothing.
			byte[] res = new byte[text.length() + 1];
			int off = 0;
			for (char c : text.toCharArray()) {
				res[off++] = (byte) c;
			}
			this.length = res.length;
			return res;
		}
	}
}

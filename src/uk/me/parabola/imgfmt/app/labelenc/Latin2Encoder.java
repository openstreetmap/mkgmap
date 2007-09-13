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

import java.io.UnsupportedEncodingException;

/**
 * Saving some old code, not in use at present.
 *
 * @author Steve Ratcliffe
 */
public class Latin2Encoder implements CharacterEncoder {
	private static final Logger log = Logger.getLogger(Latin2Encoder.class);

	private static boolean latin2supported;

	static {
		// Find out if 8859-2 is supported (it almost surely is).
		try {
			byte[] res = "É".getBytes("iso-8859-2");
			latin2supported = true;
			log.debug("latin2 supported " + res.length);
		} catch (UnsupportedEncodingException e) {
			log.warn("latin2 charset is not supported");
			latin2supported = false;
		}
	}

	/*
	 * This table can be used to look up the non-diacritic form of a letter.
	 * Where there is no base letter, then the result will be the same as the
	 * input.
	 */
	private static final char[] codeTable = new char[] {

		0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
		0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f,
		0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97,
		0x98, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f,

		0xa0 /*NBSP*/, 'A'   /*Ą*/,   0xa2 /*˘*/, 'L'  /*Ł*/,
		0xa4 /*¤*/,    'L'   /*Ľ*/,   'S'  /*Ś*/, 0xa7 /*§*/,
		0xa8 /*¨*/,    'S'   /*Š*/,   'S'  /*Ş*/, 'T'  /*Ť*/,
		'Z'  /*Ź*/,     0xad /*SHY*/, 'Z'  /*Ž*/, 'Z'  /*Ż*/,
		0xb0 /*°*/,    'a'   /*ą*/,   0xb2 /*˛*/, 'l'  /*ł*/,
		0xb4 /*´*/,    'l'   /*ľ*/,   's'  /*ś*/, 0xb7 /*ˇ*/,
		0xb8 /*¸*/,    's'   /*š*/,   's'  /*ş*/, 't'  /*ť*/,
		'z'  /*ź*/,    0xbd  /*˝*/,   'z'  /*ž*/, 'z'  /*ż*/,
		'R'  /*Ŕ*/,    'A'   /*Á*/,   'A'  /*Â*/, 'A'  /*Ă*/,
		'A'  /*Ä*/,    'L'   /*Ĺ*/,   'C'  /*Ć*/, 'C'  /*Ç*/,
		'C'  /*Č*/,    'E'   /*É*/,   'E'  /*Ę*/, 'E'  /*Ë*/,
		'E'  /*Ě*/,    'I'   /*Í*/,   'I'  /*Î*/, 'D'  /*Ď*/,
		'D'  /*Đ*/,    'N'   /*Ń*/,   'N'  /*Ň*/, 'O'  /*Ó*/,
		'O'  /*Ô*/,    'O'   /*Ő*/,   'O'  /*Ö*/, 'x'  /*×*/,
		'R'  /*Ř*/,    'U'   /*Ů*/,   'U'  /*Ú*/, 'U'  /*Ű*/,
		'U'  /*Ü*/,    'Y'   /*Ý*/,   'T'  /*Ţ*/, 0xdf /*ß*/,
		'r'  /*ŕ*/,    'a'   /*á*/,   'a'  /*â*/, 'a'  /*ă*/,
		'a'  /*ä*/,    'l'   /*ĺ*/,   'c'  /*ć*/, 'c'  /*ç*/,
		'c'  /*č*/,    'e'   /*é*/,   'e'  /*ę*/, 'e'  /*ë*/,
		'e'  /*ě*/,    'i'   /*í*/,   'i'  /*î*/, 'd'  /*ď*/,
		'd'  /*đ*/,    'n'   /*ń*/,   'n'  /*ň*/, 'o'  /*ó*/,
		'o'  /*ô*/,    'o'   /*ő*/,   'o'  /*ö*/, 0xf7 /*÷*/,
		'r'  /*ř*/,    'u'   /*ů*/,   'u'  /*ú*/, 'u'  /*ű*/,
		'u'  /*ü*/,    'y'   /*ý*/,   't'  /*ţ*/, 0xff /*˙*/,
	};

	/**
	 * Convert to the 8 bit form.
	 *
	 * @param text The text to convert to 8bit format.
	 */
	public EncodedText encodeText(String text) {
		if (!latin2supported)
			simpleEncode(text);

		try {
			// Convert to bytes.  Use latin 2 encoding.
			byte[] bytes = text.toUpperCase().getBytes("iso-8859-2");

			// Allocate result, leave extra space for the null terminator.
			byte[] res = new byte[bytes.length + 1];

			// Transliterate characters to remove all the diacritics.
			for (int i = 0; i < bytes.length; i++) {
				int b = (int) bytes[i] & 0xff;
				if (b < 128) {
					res[i] = bytes[i];
				} else {
					res[i] = (byte) codeTable[b - 128];
				}
			}

			// Null terminate the string.
			res[res.length - 1] = 0;

			return new EncodedText(res, res.length);

		} catch (UnsupportedEncodingException e) {
			// This should never happen because we have already checked that
			// it is supported.
			return simpleEncode(text);
		}
	}

	/**
	 * Just return truncated values.  This may, or may not, be better
	 * than nothing.  Only called if the proper conversion cannot be made.
	 *
	 * @param text The text to encode.
	 * @return The encoding.
	 */
	private EncodedText simpleEncode(String text) {
		log.error("could not convert the character set");
		// Just return truncated values.  This may, or may not, be better
		// than nothing.
		byte[] res = new byte[text.length() + 1];
		int off = 0;
		for (char c : text.toCharArray()) {
			res[off++] = (byte) c;
		}

		return new EncodedText(res, res.length);
	}

}

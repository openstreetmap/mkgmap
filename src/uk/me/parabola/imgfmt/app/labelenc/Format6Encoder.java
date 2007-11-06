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

	// Latin-1 suppliment characters.  And their mappings to unaccented characters.
	private static final char[] latin1tab = new char[] {
			0x80 /*<80>*/,  0x81 /*<81>*/,  0x82 /*<82>*/,  0x83 /*<83>*/,
			0x84 /*<84>*/,  0x85 /*<85>*/,  0x86 /*<86>*/,  0x87 /*<87>*/,
			0x88 /*<88>*/,  0x89 /*<89>*/,  0x8a /*<8a>*/,  0x8b /*<8b>*/,
			0x8c /*<8c>*/,  0x8d /*<8d>*/,  0x8e /*<8e>*/,  0x8f /*<8f>*/,
			0x90 /*<90>*/,  0x91 /*<91>*/,  0x92 /*<92>*/,  0x93 /*<93>*/,
			0x94 /*<94>*/,  0x95 /*<95>*/,  0x96 /*<96>*/,  0x97 /*<97>*/,
			0x98 /*<98>*/,  0x99 /*<99>*/,  0x9a /*<9a>*/,  0x9b /*<9b>*/,
			0x9c /*<9c>*/,  0x9d /*<9d>*/,  0x9e /*<9e>*/,  0x9f /*<9f>*/,
			0xa0 /* */,  'i'  /*¡*/,  0xa2 /*¢*/,  0xa3 /*£*/,
			0xa4 /*¤*/,  0xa5 /*¥*/,  0xa6 /*¦*/,  0xa7 /*§*/,
			0xa8 /*¨*/,  0xa9 /*©*/,  0xaa /*ª*/,  0xab /*«*/,
			0xac /*¬*/,  0xad /*­*/,  0xae /*®*/,  0xaf /*¯*/,
			0xb0 /*°*/,  0xb1 /*±*/,  0xb2 /*²*/,  0xb3 /*³*/,
			0xb4 /*´*/,  0xb5 /*µ*/,  0xb6 /*¶*/,  0xb7 /*·*/,
			0xb8 /*¸*/,  0xb9 /*¹*/,  0xba /*º*/,  0xbb /*»*/,
			0xbc /*¼*/,  0xbd /*½*/,  0xbe /*¾*/,  0xbf /*¿*/,
			'A'  /*À*/,  'A'  /*Á*/,  'A'  /*Â*/,  'A'  /*Ã*/,
			'A'  /*Ä*/,  'A'  /*Å*/,  0xc6 /*Æ*/,  'C'  /*Ç*/,
			'E'  /*È*/,  'E'  /*É*/,  'E'  /*Ê*/,  'E'  /*Ë*/,
			'I'  /*Ì*/,  'I'  /*Í*/,  'I'  /*Î*/,  'I'  /*Ï*/,
			'D'  /*Ð*/,  'N'  /*Ñ*/,  'O'  /*Ò*/,  'O'  /*Ó*/,
			'O'  /*Ô*/,  'O'  /*Õ*/,  'O'  /*Ö*/,  'x'  /*×*/,
			'O'  /*Ø*/,  'U'  /*Ù*/,  'U'  /*Ú*/,  'U'  /*Û*/,
			'U'  /*Ü*/,  'Y'  /*Ý*/,  0xde /*Þ*/,  0xdf /*ß*/,
			'a'  /*à*/,  'a'  /*á*/,  'a'  /*â*/,  'a'  /*ã*/,
			'a'  /*ä*/,  'a'  /*å*/,  0xe6 /*æ*/,  'c'  /*ç*/,
			'e'  /*è*/,  'e'  /*é*/,  'e'  /*ê*/,  'e'  /*ë*/,
			'i'  /*ì*/,  'i'  /*í*/,  'i'  /*î*/,  'i'  /*ï*/,
			0xf0 /*ð*/,  'n'  /*ñ*/,  'o'  /*ò*/,  'o'  /*ó*/,
			'o'  /*ô*/,  'o'  /*õ*/,  'o'  /*ö*/,  0xf7 /*÷*/,
			'o'  /*ø*/,  'u'  /*ù*/,  'u'  /*ú*/,  'u'  /*û*/,
			'u'  /*ü*/,  'y'  /*ý*/,  0xfe /*þ*/,  'y'  /*ÿ*/,
	};

	// Latin extended-A characters. With mappings to the unaccented characters.
	private static final char[] latin2tab = new char[] {
			'A'   /*Ā*/,  'a'   /*ā*/,  'A'   /*Ă*/,  'a'   /*ă*/,
			'A'   /*Ą*/,  'a'   /*ą*/,  'C'   /*Ć*/,  'c'   /*ć*/,
			'C'   /*Ĉ*/,  'c'   /*ĉ*/,  'C'   /*Ċ*/,  'c'   /*ċ*/,
			'C'   /*Č*/,  'c'   /*č*/,  'D'   /*Ď*/,  'd'   /*ď*/,
			'D'   /*Đ*/,  'd'   /*đ*/,  'E'   /*Ē*/,  'e'   /*ē*/,
			'E'   /*Ĕ*/,  'e'   /*ĕ*/,  'E'   /*Ė*/,  'e'   /*ė*/,
			'E'   /*Ę*/,  'e'   /*ę*/,  'E'   /*Ě*/,  'e'   /*ě*/,
			'G'   /*Ĝ*/,  'g'   /*ĝ*/,  'G'   /*Ğ*/,  'g'   /*ğ*/,
			'G'   /*Ġ*/,  'g'   /*ġ*/,  'G'   /*Ģ*/,  'g'   /*ģ*/,
			'H'   /*Ĥ*/,  'h'   /*ĥ*/,  'H'   /*Ħ*/,  'h'   /*ħ*/,
			'I'   /*Ĩ*/,  'i'   /*ĩ*/,  'I'   /*Ī*/,  'i'   /*ī*/,
			'I'   /*Ĭ*/,  'i'   /*ĭ*/,  'I'   /*Į*/,  'i'   /*į*/,
			'I'   /*İ*/,  'i'   /*ı*/,  0x132 /*Ĳ*/,  0x133 /*ĳ*/,
			'J'   /*Ĵ*/,  'j'   /*ĵ*/,  'K'   /*Ķ*/,  'k'   /*ķ*/,
			'k'   /*ĸ*/,  'L'   /*Ĺ*/,  'l'   /*ĺ*/,  'L'   /*Ļ*/,
			'l'   /*ļ*/,  'L'   /*Ľ*/,  'l'   /*ľ*/,  'L'   /*Ŀ*/,
			'l'   /*ŀ*/,  'L'   /*Ł*/,  'l'   /*ł*/,  'N'   /*Ń*/,
			'n'   /*ń*/,  'N'   /*Ņ*/,  'n'   /*ņ*/,  'N'   /*Ň*/,
			'n'   /*ň*/,  'n'   /*ŉ*/,  'N'   /*Ŋ*/,  'n'   /*ŋ*/,
			'O'   /*Ō*/,  'o'   /*ō*/,  'O'   /*Ŏ*/,  'o'   /*ŏ*/,
			'O'   /*Ő*/,  'o'   /*ő*/,  0x152 /*Œ*/,  0x153 /*œ*/,
			'R'   /*Ŕ*/,  'r'   /*ŕ*/,  'R'   /*Ŗ*/,  'r'   /*ŗ*/,
			'R'   /*Ř*/,  'r'   /*ř*/,  'S'   /*Ś*/,  's'   /*ś*/,
			'S'   /*Ŝ*/,  's'   /*ŝ*/,  'S'   /*Ş*/,  's'   /*ş*/,
			'S'   /*Š*/,  's'   /*š*/,  'T'   /*Ţ*/,  't'   /*ţ*/,
			'T'   /*Ť*/,  't'   /*ť*/,  'T'   /*Ŧ*/,  't'   /*ŧ*/,
			'U'   /*Ũ*/,  'u'   /*ũ*/,  'U'   /*Ū*/,  'u'   /*ū*/,
			'U'   /*Ŭ*/,  'u'   /*ŭ*/,  'U'   /*Ů*/,  'u'   /*ů*/,
			'U'   /*Ű*/,  'u'   /*ű*/,  'U'   /*Ų*/,  'u'   /*ų*/,
			'W'   /*Ŵ*/,  'w'   /*ŵ*/,  'Y'   /*Ŷ*/,  'y'   /*ŷ*/,
			'Y'   /*Ÿ*/,  'Z'   /*Ź*/,  'z'   /*ź*/,  'Z'   /*Ż*/,
			'z'   /*ż*/,  'Z'   /*Ž*/,  'z'   /*ž*/,  'f'   /*ſ*/,
	};
	
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

		String s = text.toUpperCase();

		byte[] buf = new byte[2 * s.length() + 1];
		int off = 0;
		for (char oc : s.toCharArray()) {
			char c;
			if (oc > 0x80 && oc < 0x100) {
				c = latin1tab[oc - 0x80];
			} else if (oc > 0x100 && oc < 0x180) {
				c = latin2tab[oc - 0x100];
			} else {
				c = oc;
			}
			
			if (c == ' ') {
				put6(buf, off++, 0);
			} else if (c >= 'A' && c <= 'Z') {
				put6(buf, off++, c - 'A' + 1);
			} else if (c >= '0' && c <= '9') {
				put6(buf, off++, c - '0' + 0x20);
			} else if (c >= 0x1d && c <= 0x1f) {
				put6(buf, off++, c);
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
		}

		put6(buf, off++, 0xff);

		int len = ((off - 1) * 6) / 8 + 1;
		EncodedText etext = new EncodedText(buf, len);

		return etext;
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

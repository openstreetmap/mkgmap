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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.labelenc;

/**
 * @author Steve Ratcliffe
 */
public class CodeFunctions {
	// Label encoding length
	public static final int ENCODING_FORMAT6 = 6;
	private static final int ENCODING_FORMAT9 = 9;
	private static final int ENCODING_FORMAT10 = 10;

	private int codepage;
	private int encodingType;
	private CharacterEncoder encoder;
	private CharacterDecoder decoder;

	protected void setEncoder(CharacterEncoder encoder) {
		this.encoder = encoder;
	}

	public CharacterEncoder getEncoder() {
		return encoder;
	}

	protected void setDecoder(CharacterDecoder decoder) {
		this.decoder = decoder;
	}

	public CharacterDecoder getDecoder() {
		return decoder;
	}

	public int getEncodingType() {
		return encodingType;
	}

	protected void setEncodingType(int encodingType) {
		this.encodingType = encodingType;
	}

	public int getCodepage() {
		return codepage;
	}

	public void setCodepage(int codepage) {
		this.codepage = codepage;
	}

	/**
	 * Create a CharacterEncoder for the given charset option.  Note that this
	 * routine also writes to the lblHeader parameter to set the encoding type.
	 * @param charset The mkgmap command line option to be interpreted.
	 * @return The various character set parameters that will be needed.
	 */
	public static CodeFunctions createEncoderForLBL(String charset) {
		CodeFunctions funcs = new CodeFunctions();

		if ("ascii".equals(charset)) {
			funcs.setEncodingType(ENCODING_FORMAT6);
			funcs.setEncoder(new Format6Encoder());
			funcs.setDecoder(new Format6Decoder());
		} else if ("latin1".equals(charset)) {
			funcs.setEncodingType(ENCODING_FORMAT9);
			funcs.setEncoder(new LatinEncoder());
			funcs.setDecoder(new AnyCharsetDecoder("cp1252"));
			funcs.setCodepage(1252);
		} else if ("unicode".equals(charset)) {
			funcs.setEncodingType(ENCODING_FORMAT10);
			funcs.setEncoder(new Utf8Encoder());
			funcs.setDecoder(new Utf8Decoder());
		} else if ("simple8".equals(charset)) {
			funcs.setEncodingType(ENCODING_FORMAT9);
			funcs.setEncoder(new Simple8Encoder());
		} else {
			funcs.setEncodingType(ENCODING_FORMAT9);
			funcs.setEncoder(new AnyCharsetEncoder(charset));
			guessCodepage(funcs, charset);
		}

		return funcs;
	}

	/**
	 * Guess the code page from the given charset.  Only works with things
	 * like cp1252, windows-1252 and some well known ones.
	 * @param funcs The code page functions.
	 * @param charset The charset that was given.
	 */
	private static void guessCodepage(CodeFunctions funcs, String charset) {
		String cs = charset.toLowerCase();
		if (cs.startsWith("cp")) {
			try {
				funcs.setCodepage(Integer.parseInt(charset.substring(2)));
			} catch (NumberFormatException e) {
				// wasn't in the right form
			}
		} else if (cs.startsWith("windows-")) {
			try {
				funcs.setCodepage(Integer.parseInt(charset.substring(8)));
			} catch (NumberFormatException e) {
				// wasn't in the right form to guess
			}
		} else if (cs.equals("latin1")) {
			funcs.setCodepage(1252);
		}
	}

	/**
	 * Sets encoding functions for a given format and code page.  This is used
	 * when reading from an existing file.
	 *
	 * @param format The format from the lbl header.
	 * @return The various character set parameters that will be needed.
	 */
	public static CodeFunctions createEncoderForLBL(int format) {
		CodeFunctions funcs = new CodeFunctions();

		if (format == ENCODING_FORMAT6) {
			funcs.setEncodingType(ENCODING_FORMAT6);
			funcs.setEncoder(new Format6Encoder());
			funcs.setDecoder(new Format6Decoder());
		} else {
			// TODO TEMP...
			funcs.setEncodingType(ENCODING_FORMAT9);
			funcs.setEncoder(new AnyCharsetEncoder("cp1252"));
			funcs.setDecoder(new AnyCharsetDecoder("cp1252"));
		}

		return funcs;
	}

	public static CharacterEncoder getDefaultEncoder() {
		return new Format6Encoder();
	}

	public static CharacterDecoder getDefaultDecoder() {
		return new Format6Decoder();
	}
}

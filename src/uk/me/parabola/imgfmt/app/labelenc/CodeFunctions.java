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
	public static final int ENCODING_6BIT = 6;
	public static final int ENCODING_8BIT = 9;

	private int encodingType;
	private CharacterEncoder encoder;
	private CharacterDecoder decoder;

	public CharacterEncoder getEncoder() {
		return encoder;
	}

	public void setEncoder(CharacterEncoder encoder) {
		this.encoder = encoder;
	}

	public int getEncodingType() {
		return encodingType;
	}

	public void setEncodingType(int encodingType) {
		this.encodingType = encodingType;
	}

	public void setDecoder(CharacterDecoder decoder) {
		this.decoder = decoder;
	}

	public CharacterDecoder getDecoder() {
		return decoder;
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
			funcs.setEncoder(new Format6Encoder());
			funcs.setEncodingType(ENCODING_6BIT);
		} else if ("latin1".equals(charset)) {
			funcs.setEncodingType(ENCODING_8BIT);
			funcs.setEncoder(new Latin1Encoder());
		} else if ("latin2".equals(charset)) {
			funcs.setEncodingType(ENCODING_8BIT);
			funcs.setEncoder(new Format6Encoder());
		} else if ("simple8".equals(charset)) {
			funcs.setEncodingType(ENCODING_8BIT);
			funcs.setEncoder(new Simple8Encoder());
		} else {
			funcs.setEncodingType(ENCODING_8BIT);
			funcs.setEncoder(new AnyCharsetEncoder(charset));
		}

		return funcs;
	}
}

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
 * Create date: 23-Sep-2007
 */
package uk.me.parabola.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.app.labelenc.CharacterEncoder;
import uk.me.parabola.imgfmt.app.labelenc.EncodedText;

import static uk.me.parabola.imgfmt.app.labelenc.BaseEncoder.NO_TEXT;

/**
 * An output stream that has methods for writing strings and little endian
 * integers.  Its a bit like DataOutput, but for little endian.
 *
 * @author Steve Ratcliffe
 */
public class StructuredOutputStream extends FilterOutputStream {

	private final CharacterEncoder encoder;

	public StructuredOutputStream(OutputStream out, CharacterEncoder encoder) {
		super(out);
		this.encoder = encoder;
	}

	public void write(int b) throws IOException {
		out.write(b);
	}

	public void write2(int b) throws IOException {
		out.write(b);
		out.write(b >> 8);
	}

	public void write3(int i) throws IOException {
		out.write(i);
		out.write(i >> 8);
		out.write(i >> 16);
	}

	public void write4(int b) throws IOException {
		out.write(b);
		out.write(b >> 8);
		out.write(b >> 16);
		out.write(b >> 24);
	}

	/**
	 * Writes a string including a terminating null byte.
	 *
	 * For each character in the string the low-order byte is written.
	 *
	 * @param s The string to write.
	 * @throws IOException If the write fails.
	 */
	public void writeString(String s) throws IOException {
		if (encoder == null)
			throw new ExitException("tdbfile: character encoding is null");

		EncodedText encodedText = encoder.encodeText(s);
		if (encodedText == NO_TEXT)
			return;

		out.write(encodedText.getCtext(), 0, encodedText.getLength());
	}
}

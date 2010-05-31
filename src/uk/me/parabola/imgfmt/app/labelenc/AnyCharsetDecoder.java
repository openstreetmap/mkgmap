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

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Decodes strings from format 9 and a given character set to java strings.
 */
public class AnyCharsetDecoder implements CharacterDecoder {
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private boolean needReset;
	private final Charset charSet;

	public AnyCharsetDecoder(String charsetName) {
		charSet = Charset.forName(charsetName);
	}

	public boolean addByte(int b) {
		if (b == 0) {
			needReset = true;
			return true;
		}

		if (needReset) {
			needReset = false;
			out.reset();
		}

		out.write(b);
		return false;
	}

	public DecodedText getText() {
		byte[] ba = out.toByteArray();
		return new DecodedText(ba, charSet);
	}
}
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

	public boolean isCharsetSupported() {
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
}

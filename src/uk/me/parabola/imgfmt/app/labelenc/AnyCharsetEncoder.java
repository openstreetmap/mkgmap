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
 * Create date: 31-Oct-2007
 */
package uk.me.parabola.imgfmt.app.labelenc;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

/**
 * Convert text to a specified charset.  This is used when you give a
 * charset name on the command line to convert to.
 *
 * @author Steve Ratcliffe
 */
public class AnyCharsetEncoder extends BaseEncoder implements CharacterEncoder {

	private final String charSet;

	public AnyCharsetEncoder(String cs) {
		prepareForCharacterSet(cs);
		charSet = cs;
	}

	public EncodedText encodeText(String text) {
		if (text == null)
			return NO_TEXT;
		
		if (!isCharsetSupported())
			return simpleEncode(text);

		String ucText;
		if (isUpperCase())
			ucText = text.toUpperCase(Locale.ENGLISH);
		else
			ucText = text;

		try {
			byte[] bytes = ucText.getBytes(charSet);
			byte[] res = new byte[bytes.length + 1];
			System.arraycopy(bytes, 0, res, 0, bytes.length);

			return new EncodedText(res, res.length);
		} catch (UnsupportedEncodingException e) {
			// This can't really happen as we have already checked.
			return simpleEncode(text);
		}

	}
}

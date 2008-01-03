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

import java.io.UnsupportedEncodingException;
import java.util.Locale;

/**
 * For units that support latin1 characters (eg my UK Garmin Cx).
 * Still uppercase only.
 *
 * @author Steve Ratcliffe
 */
public class Latin1Encoder extends BaseEncoder
		implements CharacterEncoder
{

	public Latin1Encoder() {
		prepareForCharacterSet("iso-8859-1");
	}

	public EncodedText encodeText(String text) {
		if (text == null)
			return NO_TEXT;
		
		if (!isCharsetSupported())
			return simpleEncode(text);

		try {
			// Guess that 8859-1 is used in the Garmin.
			String ucText = (isUpperCase())? text.toUpperCase(Locale.ENGLISH): text;
			byte[] bytes = ucText.getBytes("iso-8859-1");

			byte[] res = new byte[bytes.length + 1];
			System.arraycopy(bytes, 0, res, 0, bytes.length);

			return new EncodedText(res, res.length);
		} catch (UnsupportedEncodingException e) {
			// Shouldnt happen because we have already checked.
			return simpleEncode(text);
		}
	}
}

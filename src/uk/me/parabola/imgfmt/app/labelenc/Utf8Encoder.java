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
 * Create date: Feb 19, 2008
 */
package uk.me.parabola.imgfmt.app.labelenc;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

/**
 * Ecoder for labels in utf-8, note that I am not actually sure that this
 * is in fact used anywhere.
 * 
 * @author Steve Ratcliffe
 */
public class Utf8Encoder extends BaseEncoder implements CharacterEncoder {
	
	public EncodedText encodeText(String text) {
		if (text == null)
			return NO_TEXT;

		String uctext;
		if (isUpperCase())
			uctext = text.toUpperCase(Locale.ENGLISH);
		else
			uctext = text;

		EncodedText et;
		try {
			byte[] buf = uctext.getBytes("utf-8");
			byte[] res = new byte[buf.length + 1];
			System.arraycopy(buf, 0, res, 0, buf.length);
			res[buf.length] = 0;
			et = new EncodedText(res, res.length);
		} catch (UnsupportedEncodingException e) {
			// As utf-8 must be supported, this can't happen
			byte[] buf = uctext.getBytes();
			et = new EncodedText(buf, buf.length);
		}
		return et;
	}
}

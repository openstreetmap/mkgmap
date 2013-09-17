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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Locale;

/**
 * Convert text to a specified charset.  This is used when you give a
 * charset name on the command line to convert to.
 *
 * @author Steve Ratcliffe
 */
public class AnyCharsetEncoder extends BaseEncoder implements CharacterEncoder {

	private final CharsetEncoder encoder;
	private final Transliterator transliterator;

	public AnyCharsetEncoder(String cs, Transliterator transliterator) {
		this.transliterator = transliterator;
		prepareForCharacterSet(cs);
		if (isCharsetSupported()) {
			encoder = Charset.forName(cs).newEncoder();
			encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
		} else {
			encoder = null;
		}
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

		// TODO: over allocate space. need better way, or perhaps it is not so bad
		byte[] bytes = new byte[(int) (ucText.length() * encoder.maxBytesPerChar()) * 4 + 5];
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		CharBuffer charBuffer = CharBuffer.wrap(ucText);

		CoderResult result;

		do {
			result = encoder.encode(charBuffer, bb, true);
			if (result.isUnmappable()) {
				char c = charBuffer.get();
				String s = String.valueOf(c);

				s = transliterator.transliterate(s);

				for (int i = 0; i < s.length(); i++)
					bb.put((byte) s.charAt(i));
			}
		} while (result != CoderResult.UNDERFLOW);

		// We need it to be null terminated but also to trim any extra memory from the allocated
		// buffer.
		byte[] res = Arrays.copyOf(bytes, bb.position() + 1);
		return new EncodedText(res, res.length);
	}

	public void setUpperCase(boolean upperCase) {
		super.setUpperCase(upperCase);
		transliterator.forceUppercase(upperCase);
	}
}

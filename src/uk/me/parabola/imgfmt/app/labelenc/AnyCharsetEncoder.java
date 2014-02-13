/*
 * Copyright (C) 2007,2014 Steve Ratcliffe
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

		// Allocate a buffer for the encoded text. This will be large enough in almost all cases,
		// but the code below allocates more space if necessary.
		ByteBuffer outBuf = ByteBuffer.allocate(ucText.length() + 20);
		CharBuffer charBuffer = CharBuffer.wrap(ucText);

		CoderResult result;

		do {
			result = encoder.encode(charBuffer, outBuf, true);

			if (result.isUnmappable()) {
				// There is a character that cannot be represented in the target code page.
				// Read the character(s), transliterate them, and add them to the output.
				// We then continue onward with the rest of the string.
				String s;
				if (result.length() == 1) {
					s = String.valueOf(charBuffer.get());
				} else {
					// Don't know under what circumstances this will be called and may not be the
					// correct thing to do when it does happen.
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < result.length(); i++)
						sb.append(charBuffer.get());

					s = sb.toString();
				}

				s = transliterator.transliterate(s);

				// Make sure that there is enough space for the transliterated string
				while (outBuf.limit() < outBuf.position() + s.length())
					outBuf = reallocBuf(outBuf);

				for (int i = 0; i < s.length(); i++)
					outBuf.put((byte) s.charAt(i));

			} else if (result == CoderResult.OVERFLOW) {
				// Ran out of space in the output
				outBuf = reallocBuf(outBuf);
			}
		} while (result != CoderResult.UNDERFLOW);

		// We need it to be null terminated but also to trim any extra memory from the allocated
		// buffer.
		byte[] res = Arrays.copyOf(outBuf.array(), outBuf.position() + 1);
		char[] cres = new char[res.length];
		for (int i = 0; i < res.length; i++)
			cres[i] = (char) (res[i] & 0xff);
		return new EncodedText(res, res.length, cres);
	}

	/**
	 * Allocate a new byte buffer that has more space.
	 *
	 * It will have the same contents as the existing one and the same position, so you can
	 * continue writing to it.
	 *
	 * @param bb The original byte buffer.
	 * @return A new byte buffer with the same contents with more space that you can continue
	 * writing to.
	 */
	private ByteBuffer reallocBuf(ByteBuffer bb) {
		byte[] newbuf = Arrays.copyOf(bb.array(), bb.capacity() * 2);
		return ByteBuffer.wrap(newbuf, bb.position(), newbuf.length - bb.position());
	}

	public void setUpperCase(boolean upperCase) {
		super.setUpperCase(upperCase);
		transliterator.forceUppercase(upperCase);
	}
}

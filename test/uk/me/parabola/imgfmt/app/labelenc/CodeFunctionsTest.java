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

import java.util.Arrays;

import static org.junit.Assert.*;
import org.junit.Test;

public class CodeFunctionsTest {
	/**
	 * Quick check of the ascii 6 bit format conversion.
	 */
	@Test
	public void testFormat6() {
		CodeFunctions functions = CodeFunctions.createEncoderForLBL(6);
		assertEquals("code page", 0, functions.getCodepage());
		assertEquals("encoding type", 6, functions.getEncodingType());
		CharacterEncoder enc = functions.getEncoder();

		EncodedText etext = enc.encodeText("hello world");
		byte[] ctext = etext.getCtext();
		int len = etext.getLength();

		// This was determined from the behaviour of the existing code, and not
		// from first principles.
		assertEquals("encoded length", 9, len);
		byte[] foo = {
				0x20, 0x53, 0xc, 0x3c, 0x5, 0xffffffcf, 0x48, 0xffffffc1, 0x3f,
		};
		assertArrayEquals("encoded text", foo, Arrays.copyOf(ctext, len));
	}

	@Test
	public void testAscii() {
		CodeFunctions f = CodeFunctions.createEncoderForLBL("ascii");
		assertEquals("code page", 0, f.getCodepage());
		assertEquals("encoding type", 6, f.getEncodingType());
	}

	/**
	 * Transliteration when going to ascii in format 6.  This was originally
	 * the only place where transliteration was available.
	 */
	@Test
	public void testTransliterate6() {
		CodeFunctions functions = CodeFunctions.createEncoderForLBL(6);

		CharacterEncoder encoder = functions.getEncoder();
		EncodedText text = encoder.encodeText("Körnerstraße, Velkomezeříčská, Skólavörðustigur");

		CharacterDecoder decoder = functions.getDecoder();
		byte[] ctext = text.getCtext();
		for (int i = 0; i < text.getLength(); i++) {
			decoder.addByte(ctext[i]);
		}
		decoder.addByte(0xff);
		String result = decoder.getText().getText();
		assertEquals("transliterated text", "KORNERSTRASSE, VELKOMEZERICSKA, SKOLAVORDUSTIGUR", result);
	}

	/**
	 * Backward compatibility test.
	 */
	@Test
	public void testLatin1() {
		CodeFunctions functions = CodeFunctions.createEncoderForLBL("latin1");
		assertEquals("code page", 1252, functions.getCodepage());
		assertEquals("encoding type", 9, functions.getEncodingType());

		StringBuilder sb = new StringBuilder();
		for (char c = 1; c < 256; c++) {
			sb.append(c);
		}

		CharacterEncoder encoder = functions.getEncoder();
		EncodedText text = encoder.encodeText(sb.toString());

		// This encoder appends a null byte.
		assertEquals("length of encoded text", 256, text.getLength());

		for (int i = 1; i < 256; i++) {
			// The following characters do not display on my GPS.  This covers
			// the region where windows-1252 differs from iso 8859 so we don't
			// really know which it is meant to be.
			if (i >= 0x80 && i <= 0xbf)
				continue;
			assertEquals("character", i, text.getCtext()[i-1] & 0xff);
		}
	}
}

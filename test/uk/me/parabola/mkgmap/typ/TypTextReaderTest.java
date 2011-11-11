/*
 * Copyright (C) 2011.
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
package uk.me.parabola.mkgmap.typ;

import java.io.Reader;
import java.io.StringReader;

import uk.me.parabola.imgfmt.app.typ.ShapeStacking;
import uk.me.parabola.imgfmt.app.typ.TypParam;

import func.lib.ArrayImgWriter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TypTextReaderTest {
	private TypTextReader tr;
	private static final int ORDER_SIZE = 5;

	@Before
	public void setUp() {
	}

	@Test
	public void testIdSection() {
		tr = makeTyp("[_id]\n" +
				"FID=24\n" +
				"ProductCode=2\n" +
				"CodePage=99\n" +
				"[End]");

		TypParam param = tr.getParam();
		assertEquals(24, param.getFamilyId());
		assertEquals(2, param.getProductId());
		assertEquals(99, param.getCodePage());
	}

	@Test
	public void testStacking() {

		tr = makeTyp("[_drawOrder]\n" +
				"Type=0x3,1\n" +
				"Type=0x2,2\n" +
				"Type=0x1,4\n" +
				"Type=0x4,2\n" +
				"Type=0x10402,2\n" +
				"Type=0x10405,2\n" +
				"[End]\n");

		ShapeStacking stacking = tr.getStacking();

		ArrayImgWriter out = new ArrayImgWriter();
		stacking.write(out);

		byte[] buf = out.getBytes();
		assertEquals(3, buf[0]);

		assertEquals(0, buf[1 * ORDER_SIZE]);
		assertEquals(2, buf[2 * ORDER_SIZE]);
		assertEquals(4, buf[3 * ORDER_SIZE]);
		assertEquals((1<<2) + (1<<5), buf[3 * ORDER_SIZE + 1]);
	}

	private TypTextReader makeTyp(String in) {
		Reader r = new StringReader(in);

		TypTextReader tr = new TypTextReader();
		tr.read("string", r);
		return tr;
	}
}

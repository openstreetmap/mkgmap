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
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.typ.ShapeStacking;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.app.typ.TypLine;
import uk.me.parabola.imgfmt.app.typ.TypParam;
import uk.me.parabola.imgfmt.app.typ.TypPolygon;

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
				"CodePage=1251\n" +
				"[End]");

		TypParam param = tr.getData().getParam();
		assertEquals(24, param.getFamilyId());
		assertEquals(2, param.getProductId());
		assertEquals(1251, param.getCodePage());
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

		ShapeStacking stacking = tr.getData().getStacking();

		ArrayImgWriter out = new ArrayImgWriter();
		stacking.write(out);

		byte[] buf = out.getBytes();
		assertEquals(3, buf[0]);

		assertEquals(0, buf[ORDER_SIZE]);
		assertEquals(2, buf[2 * ORDER_SIZE]);
		assertEquals(4, buf[3 * ORDER_SIZE]);
		assertEquals((1<<2) + (1<<5), buf[3 * ORDER_SIZE + 1]);
	}

	@Test
	public void testPolygon() {
		tr = makeTyp("[_polygon]\n" +
				"Type=0x2\n" +
				"String1=0x04,Parking\n" +
				"String2=0x03,Parkeergarage\n" +
				"Xpm=\"0 0 2 0\"\n" +
				"\"1 c #7BCAD5\"\n" +
				"\"2 c #00008B\"\n" +
				"[End]\n"
		);

		TypData data = tr.getData();
		List<TypPolygon> polygons = data.getPolygons();
		TypPolygon p = polygons.get(0);
		assertEquals(2, p.getType());
	}

	@Test
	public void testPolygonWithBitmap() {
		tr = makeTyp("[_polygon]\n" +
			"Xpm=\"32 32 4 1\"\n" +
				"\"! c #FFCC99\"\n" +
				"\"  c none\"\n" +
				"\"3 c #000000\"\n" +
				"\"4 c none\"\n" +
				"\"!     !!!     !!!     !!!     !!\"\n" +
				"\"     !!!     !!!     !!!     !!!\"\n" +
				"\"    !!!     !!!     !!!     !!! \"\n" +
				"\"   !!!     !!!     !!!     !!!  \"\n" +
				"\"  !!!     !!!     !!!     !!!   \"\n" +
				"\" !!!     !!!     !!!     !!!    \"\n" +
				"\"!!!     !!!     !!!     !!!     \"\n" +
				"\"!!      !!      !!      !!      \"\n" +
				"\"!     !!!     !!!     !!!     !!\"\n" +
				"\"     !!!     !!!     !!!     !!!\"\n" +
				"\"    !!!     !!!     !!!     !!! \"\n" +
				"\"   !!!     !!!     !!!     !!!  \"\n" +
				"\"  !!!     !!!     !!!     !!!   \"\n" +
				"\" !!!     !!!     !!!     !!!    \"\n" +
				"\"!!!     !!!     !!!     !!!     \"\n" +
				"\"!!      !!      !!      !!      \"\n" +
				"\"!     !!!     !!!     !!!     !!\"\n" +
				"\"     !!!     !!!     !!!     !!!\"\n" +
				"\"    !!!     !!!     !!!     !!! \"\n" +
				"\"   !!!     !!!     !!!     !!!  \"\n" +
				"\"  !!!     !!!     !!!     !!!   \"\n" +
				"\" !!!     !!!     !!!     !!!    \"\n" +
				"\"!!!     !!!     !!!     !!!     \"\n" +
				"\"!!      !!      !!      !!      \"\n" +
				"\"!     !!!     !!!     !!!     !!\"\n" +
				"\"     !!!     !!!     !!!     !!!\"\n" +
				"\"    !!!     !!!     !!!     !!! \"\n" +
				"\"   !!!     !!!     !!!     !!!  \"\n" +
				"\"  !!!     !!!     !!!     !!!   \"\n" +
				"\" !!!     !!!     !!!     !!!    \"\n" +
				"\"!!!     !!!     !!!     !!!     \"\n" +
				"\"!!      !!      !!      !!      \"\n" +
				"[End]\n");

		TypData data = tr.getData();
		List<TypPolygon> polygons = data.getPolygons();
		TypPolygon p = polygons.get(0);
		ArrayImgWriter out = new ArrayImgWriter();
		p.write(out, data.getEncoder());

		byte[] bytes = out.getBytes();
		assertEquals(135, bytes.length);
	}

	@Test
	public void testLineTwoColours() {
		TypTextReader tr = makeTyp("[_line]\n" +
				"Type=0x00\n" +
				"UseOrientation=Y\n" +
				"LineWidth=2\n" +
				"BorderWidth=1\n" +
				"Xpm=\"0 0 2 0\"\n" +
				"\"1 c #DDDDDD\"\n" +
				"\"2 c #999999\"\n" +
				"String1=0x04,Road\n" +
				"String2=0x01,Route non-définie\n" +
				"String3=0x03,Weg\n" +
				"ExtendedLabels=Y\n" +
				"FontStyle=SmallFont\n" +
				"CustomColor=No\n" +
				"[end]");

		TypData data = tr.getData();
		TypLine line = data.getLines().get(0);
		ImgFileWriter w = new ArrayImgWriter();
		line.write(w, data.getEncoder());
	}

	private TypTextReader makeTyp(String in) {
		Reader r = new StringReader(in);

		TypTextReader tr = new TypTextReader();
		tr.read("string", r);
		return tr;
	}
}

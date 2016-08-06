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

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.typ.ShapeStacking;
import uk.me.parabola.imgfmt.app.typ.TYPFile;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.app.typ.TypLine;
import uk.me.parabola.imgfmt.app.typ.TypParam;
import uk.me.parabola.imgfmt.app.typ.TypPoint;
import uk.me.parabola.imgfmt.app.typ.TypPolygon;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.mkgmap.srt.SrtTextReader;

import func.lib.ArrayImgWriter;
import func.lib.TestUtils;
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
		assertEquals(0, buf[3 * ORDER_SIZE + 1]);
		assertEquals(4, buf[4 * ORDER_SIZE]);
		assertEquals((1<<2) + (1<<5), buf[4 * ORDER_SIZE + 1]);
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

	@Test
	public void testPointWithAlpha() {
		TestUtils.registerFile("hello");
		TypTextReader tr = makeTyp("[_point]\n" +
				"Type=0x12\n" +
				"SubType=0x01\n" +
				";23E6\n" +
				";size: 45\n" +
				"String1=0x4,Mini round\n" +
				"String2=0x1,Mini rond-point\n" +
				"ExtendedLabels=N\n" +
				"DayXpm=\"9 9 10 1\"\n" +
				"\"$  c none\"\n" +
				"\"%  c #808080\"  alpha=14\n" +
				"\"&  c #808080\"\n" +
				"\"'  c #808080\"  alpha=15\n" +
				"\"(  c #808080\"  alpha=8\n" +
				"\")  c #F0F7FF\"\n" +
				"\"*  c #808080\"  alpha=4\n" +
				"\"+  c #808080\"  alpha=11\n" +
				"\",  c #808080\"  alpha=12\n" +
				"\"-  c #808080\"  alpha=13\n" +
				"\"$%&&&&&'$\"\n" +
				"\"(&&&)&&&*\"\n" +
				"\"&&)))))&&\"\n" +
				"\"&&)&&&)&&\"\n" +
				"\"&))&)&))&\"\n" +
				"\"&&)&&&)&&\"\n" +
				"\"&&)))))&&\"\n" +
				"\"+&&&)&&&,\"\n" +
				"\"$-&&&&&-$\"\n" +
				"[end]"
				);

		TypData data = tr.getData();
		TypPoint point = data.getPoints().get(0);
		ArrayImgWriter w = new ArrayImgWriter();
		point.write(w, data.getEncoder());
		System.out.println("size " + w.getSize());
		try {
			OutputStream os = new FileOutputStream("hello");
			os.write(w.getBytes());
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		assertEquals(115, w.getBytes().length);

	}

	@Test
	public void testZeroColourBug() {
		String s = "[_point]\n" +
				"Type=0x01e\n" +
				"SubType=0x00\n" +
				"String1=0x04,island\n" +
				"DayXpm=\"5 5 1 1\"   Colormode=32\n" +
				"\"!      c #000000\"  canalalpha=15\n" +
				"\"!!!!!\"\n" +
				"\"!!!!!\"\n" +
				"\"!!!!!\"\n" +
				"\"!!!!!\"\n" +
				"\"!!!!!\"\n" +
				"[end]";

		tr = makeTyp(s);
		TypData data = tr.getData();
		TypPoint point = data.getPoints().get(0);

		ArrayImgWriter w = new ArrayImgWriter();
		point.write(w, data.getEncoder());

		byte[] out = w.getBytes();

		assertEquals("width", 5, out[1]);
		assertEquals("height", 5, out[2]);
		assertEquals("number of colours", 1, out[3]);
	}

	/**
	 * Basic test, reading from a file using most features.
	 */
	@Test
	public void testFromFile() throws IOException {
		Reader r = new BufferedReader(new FileReader("test/resources/typ/test.txt"));
		tr = new TypTextReader();
		tr.read("test.typ", r);

		TestUtils.registerFile("ts__test.typ");
		RandomAccessFile raf = new RandomAccessFile("ts__test.typ", "rw");
		FileChannel channel = raf.getChannel();
		channel.truncate(0);
		FileImgChannel w = new FileImgChannel(channel);
		try (TYPFile typ = new TYPFile(w)) {
			typ.setData(tr.getData());
			typ.write();
		}
		
		
	}

	/**
	 * Check that unknown sections don't throw an exception and are ignored without
	 * affecting anything else.
	 */
	@Test
	public void testIgnoreUnknownSections() {
		tr = makeTyp("[_unknown_section_name]\n" +
				"Type=0x2\n" +
				"String1=0x04,Parking\n" +
				"String2=0x03,Parkeergarage\n" +
				"OtherStuff=Unknown\n" +
				"[End]\n" +
				"[_id]\n" +
				"FID=4455\n" +
				"ProductCode=2\n" +
				"CodePage=1251\n" +
				"[End]"
		);

		TypData data = tr.getData();
		System.out.println(data);
		assertEquals(4455, data.getParam().getFamilyId());
	}

	private TypTextReader makeTyp(String in) {
		Reader r = new StringReader(in);

		TypTextReader tr = new TypTextReader();
		tr.read("string", r);
		if (tr.getData().getSort() == null)
			tr.getData().setSort(SrtTextReader.sortForCodepage(1252));
		return tr;
	}
}

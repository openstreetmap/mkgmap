/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 10-Jan-2009
 */
package func;

import java.io.File;
import java.io.FileNotFoundException;

import uk.me.parabola.imgfmt.app.lbl.LBLFileReader;
import uk.me.parabola.imgfmt.app.trergn.RGNFileReader;
import uk.me.parabola.imgfmt.app.trergn.TREFileReader;
import uk.me.parabola.imgfmt.app.trergn.TREHeader;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.mkgmap.main.Main;

import func.lib.Args;
import func.lib.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Creates a single img file and runs several tests on it to verify
 * the basic structure of it.
 *
 * @author Steve Ratcliffe
 */
public class StructureTest {
	private static FileSystem fs;

	private static LBLFileReader lblFile;
	private static TREFileReader treFile;
	private static RGNFileReader rgnFile;

	/** Just test is exists. */
	@Test
	public void testExists() {
		File f = new File(Args.DEF_MAP_FILENAME);
		assertTrue("file exists", f.exists());
	}

	@Test
	public void testTreHeader() {
		TREHeader header = (TREHeader) treFile.getHeader();
		assertEquals("header length", 188, header.getHeaderLength());
		
		assertEquals("display priority", 25, header.getDisplayPriority());
	}

	/**
	 * Read in the file and open all the sections, leave references to them
	 * in fields so that the other tests can check things.
	 */
	@BeforeClass
	public static void init() throws FileNotFoundException {
		TestUtils.deleteOutputFiles();

		Main.mainNoSystemExit(Args.TEST_STYLE_ARG, Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz");

		fs = ImgFS.openFs(Args.DEF_MAP_FILENAME);
		ImgChannel tre = fs.open(Args.DEF_MAP_ID + ".TRE", "r");
		treFile = new TREFileReader(tre);

		ImgChannel lbl = fs.open(Args.DEF_MAP_ID + ".LBL", "r");
		lblFile = new LBLFileReader(lbl);

		ImgChannel rgn = fs.open(Args.DEF_MAP_ID + ".RGN", "r");
		rgnFile = new RGNFileReader(rgn);
	}

	/**
	 * Close everything down.
	 */
	@AfterClass
	public static void cleanup() {
		if (fs != null) {
			fs.close();
			treFile.close();
			lblFile.close();
			rgnFile.close();
		}
		TestUtils.deleteOutputFiles();
	}
}

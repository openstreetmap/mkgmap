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

import uk.me.parabola.imgfmt.app.lbl.LBLFile;
import uk.me.parabola.imgfmt.app.trergn.RGNFile;
import uk.me.parabola.imgfmt.app.trergn.TREFile;
import uk.me.parabola.imgfmt.app.trergn.TREHeader;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.mkgmap.main.Main;

import func.lib.Args;
import func.lib.TestUtils;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Creates a file and runs several tests on it to verify the structure of
 * it.
 *
 * @author Steve Ratcliffe
 */
public class StructureTest {
	private static FileSystem fs;

	private static LBLFile lblFile;
	private static TREFile treFile;
	private static RGNFile rgnFile;

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
	}

	@BeforeClass
	public static void init() throws FileNotFoundException {
		TestUtils.deleteOutputFiles();

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				Args.TEST_RESOURCE_OSM + "lon1.osm.gz"
		});

		fs = ImgFS.openFs(Args.DEF_MAP_FILENAME);
		ImgChannel tre = fs.open(Args.DEF_MAP_ID + ".TRE", "r");
		treFile = new TREFile(tre, false);

		ImgChannel lbl = fs.open(Args.DEF_MAP_ID + ".LBL", "r");
		lblFile = new LBLFile(lbl, false);

		ImgChannel rgn = fs.open(Args.DEF_MAP_ID + ".RGN", "r");
		rgnFile = new RGNFile(rgn);
	}

	@AfterClass
	public static void cleanup() {
		TestUtils.deleteOutputFiles();
		if (fs != null) {
			fs.close();
			treFile.close();
			lblFile.close();
			rgnFile.close();
		}
	}
}

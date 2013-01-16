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
package func.files;

import java.io.File;
import java.io.IOException;

import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;

import func.Base;
import func.lib.Args;
import func.lib.Outputs;
import func.lib.TestUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class IndexTest extends Base {
	private static final String OVERVIEW_NAME = "testname";
	private static final String MDR_IMG = OVERVIEW_NAME + "_mdr.img";

	@Test
	public void testCreateIndex() throws IOException {
		File f = new File(MDR_IMG);
		f.delete();
		assertFalse("does not pre-exist", f.exists());

		Outputs outputs = TestUtils.run(
				Args.TEST_STYLE_ARG,
				"--index",
				"--latin1",
				"--family-id=1002",
				"--overview-mapname=" + OVERVIEW_NAME,
				Args.TEST_RESOURCE_IMG + "63240001.img",
				Args.TEST_RESOURCE_IMG + "63240002.img"
		);
		outputs.checkNoError();

		TestUtils.registerFile(MDR_IMG);
		TestUtils.registerFile(OVERVIEW_NAME+".tdb");
		TestUtils.registerFile(OVERVIEW_NAME+".mdx");
		TestUtils.registerFile(OVERVIEW_NAME+".img");

		assertTrue(MDR_IMG + " is created", f.exists());

		FileSystem fs = openFs(MDR_IMG);
		DirectoryEntry entry = fs.lookup(OVERVIEW_NAME.toUpperCase() + ".MDR");
		assertNotNull("Contains the MDR file", entry);

		entry = fs.lookup(OVERVIEW_NAME.toUpperCase() + ".SRT");
		assertNotNull("contains the SRT file", entry);
		fs.close();
	}
}

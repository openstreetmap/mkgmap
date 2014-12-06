/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
/* Create date: 17-Feb-2009 */
package func.files;

import java.io.File;
import java.io.IOException;

import uk.me.parabola.mkgmap.main.Main;
import uk.me.parabola.tdbfmt.TdbFile;

import func.Base;
import func.lib.Args;
import func.lib.TestUtils;
import org.junit.Test;

import static org.junit.Assert.*;


public class TdbTest extends Base {
	private static final String TDBNAME = "osmmap.tdb";

	/**
	 * Basic test that the correct file is created.  Check a few
	 * values within it too.
	 */
	@Test
	public void testBasic() throws IOException {
		Main.mainNoSystemExit(new String[]{
				Args.TEST_STYLE_ARG,
				"--tdbfile",
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz",
				Args.TEST_RESOURCE_OSM + "uk-test-2.osm.gz"
		});

		File f = new File(TDBNAME);
		assertTrue("TDB was created", f.exists());

		TdbFile tdb = TdbFile.read(TDBNAME);
		assertEquals("tdb version", 407, tdb.getTdbVersion());
	}


	/**
	 * Check for each possible option.
	 */
	@Test
	public void testOptions() {
		int thisMapname = 11112222;
		TestUtils.registerFile(thisMapname + ".img", thisMapname + ".tdb");
		Main.mainNoSystemExit(new String[]{
				Args.TEST_STYLE_ARG,
				"--tdbfile",
				"--overview-mapname=" + thisMapname,
				"--family-id=198",
				"--product-id=2",
				"--series-name=Test series",
				"--family-name=Test family",
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz"
		});

		File f = new File(thisMapname + ".tdb");
		assertTrue("TDB was created", f.exists());

		// more to do here...
	}
}

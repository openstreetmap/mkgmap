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
/* Create date: 16-Feb-2009 */
package func.route;

import java.io.FileNotFoundException;
import java.util.List;

import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.mkgmap.main.Main;

import func.lib.Args;
import static org.junit.Assert.*;
import org.junit.Test;

public class SimpleRoute {

	/**
	 * Simple test to ensure that nothing has changed.  Of course
	 * if the output should have changed, then this will have to be altered
	 * to match.
	 */
	@Test
	public void testSize() throws FileNotFoundException {
		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--route",
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz"
		});

		FileSystem fs = ImgFS.openFs(Args.DEF_MAP_ID + ".img");
		assertNotNull("file exists", fs);

		List<DirectoryEntry> entries = fs.list();
		int count = 0;
		for (DirectoryEntry ent : entries) {
			String ext = ent.getExt();

			int size = ent.getSize();
			if (ext.equals("RGN")) {
				count++;
				assertEquals("RGN size", 155300, size);
			} else if (ext.equals("TRE")) {
				count++;
				assertEquals("TRE size", 1945, size);
			} else if (ext.equals("LBL")) {
				count++;
				assertEquals("LBL size", 26980, size);
			} else if (ext.equals("NET")) {
				count++;
				assertEquals("NET size", 66827, size);
			} else if (ext.equals("NOD")) {
				count++;
				assertEquals("NOD size", 194727, size);
			}
		}
		assertTrue("enough checks run", count == 5);
	}
}

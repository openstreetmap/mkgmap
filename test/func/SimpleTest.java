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
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.map.MapReader;
import uk.me.parabola.imgfmt.app.trergn.Point;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.mkgmap.main.Main;

import func.lib.Args;
import func.lib.RangeMatcher;
import func.lib.TestUtils;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Very simple checks.  May go away as more detailed checks are developed.
 * 
 * @author Steve Ratcliffe
 */
public class SimpleTest extends Base {

	/**
	 * A very basic check that the size of all the sections has not changed.
	 * This can be used to make sure that a change that is not expected to
	 * change the output does not do so.
	 *
	 * The sizes will have to be always changed when the output does change
	 * though.
	 */
	@Test
	public void testBasic() throws FileNotFoundException {

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--preserve-element-order",
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz"
		});

		MapReader mr = new MapReader(Args.DEF_MAP_ID + ".img");
		TestUtils.registerFile(mr);
		//FileSystem fs = ImgFS.openFs(Args.DEF_MAP_ID + ".img");
		assertNotNull("file exists", mr);

		Area bounds = mr.getTreBounds();
		Area expBox = new Area(2402404, -11185, 2407064, -6524);
		assertEquals("bounds of map", expBox, bounds);

		List<Point> list = mr.pointsForLevel(0);
		assertEquals("number of points at level 0", 204, list.size());

		List<Polyline> list1 = mr.linesForLevel(0);
		assertEquals("number of lines at level 0", 3752, list1.size());
	}

	@Test
	public void testNoSuchFile() {
		Main.main(new String[]{
				"no-such-file-xyz.osm",
		});
		assertFalse("no file generated", new File(Args.DEF_MAP_FILENAME).exists());
	}

	@Test
	public void testPolish() throws FileNotFoundException {
		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				Args.TEST_RESOURCE_MP + "test1.mp"
		});

		FileSystem fs = openFs(Args.DEF_MAP_FILENAME);
		assertNotNull("file exists", fs);

		List<DirectoryEntry> entries = fs.list();
		int count = 0;
		for (DirectoryEntry ent : entries) {
			String ext = ent.getExt();

			int size = ent.getSize();
			if (ext.equals("RGN")) {
				count++;
				assertThat("RGN size", size, new RangeMatcher(2901));
			} else if (ext.equals("TRE")) {
				count++;
				assertEquals("TRE size", 596, size);
			} else if (ext.equals("LBL")) {
				count++;
				assertEquals("LBL size", 957, size);
			}
		}
		assertTrue("enough checks run", count >= 3);
	}
}

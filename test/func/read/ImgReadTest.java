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
package func.read;

import java.io.FileNotFoundException;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.map.MapReader;
import uk.me.parabola.imgfmt.app.net.RoadDef;

import func.lib.Args;
import org.junit.Test;

import static org.junit.Assert.*;


public class ImgReadTest {

	@Test
	public void testNet() throws FileNotFoundException {
		MapReader mr = new MapReader(Utils.joinPath(Args.TEST_RESOURCE_IMG, Args.DEF_MAP_FILENAME3));
		List<RoadDef> roads = mr.getRoads();

		assertEquals("number of roads", 1355, roads.size());
	}
}

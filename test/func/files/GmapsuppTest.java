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

import func.lib.Args;
import func.Base;
import static org.junit.Assert.*;
import org.junit.Test;

public class GmapsuppTest extends Base {
	private static final String GMAPSUPP_IMG = "gmapsupp.img";

	@Test
	public void testBasic() throws IOException {
		File f = new File(GMAPSUPP_IMG);
		assertFalse("does not pre-exist", f.exists());

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz",
				Args.TEST_RESOURCE_OSM + "uk-test-2.osm.gz"
		});

		assertTrue("gmapsupp.img was created", f.exists());
	}
}

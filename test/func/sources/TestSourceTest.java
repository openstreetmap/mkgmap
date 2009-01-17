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
 * Create date: 11-Jan-2009
 */
package func.sources;

import uk.me.parabola.mkgmap.main.Main;

import func.Base;
import org.junit.Test;

/**
 * For the test data sources.
 *
 * @author Steve Ratcliffe
 */
public class TestSourceTest extends Base {
	/**
	 * The test map that includes all elements.
	 */
	@Test
	public void testAllElements() {
		checkNoStdFile();
		Main.main(new String[]{
				"test-map:all-elements"
		});
		checkStdFile();
	}

	/**
	 * The test map that includes all kinds of points.
	 */
	@Test
	public void testAllPoints() {
		Main.main(new String[]{
				"test-map:test-points"
		});
		checkStdFile();
	}
}

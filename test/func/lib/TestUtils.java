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
package func.lib;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Useful routines to use during the functional tests.
 * 
 * @author Steve Ratcliffe
 */
public class TestUtils {

	/**
	 * Delelete output files that were created by the tests.
	 * Used to clean up before/after a test.
	 */
	public static void deleteOutputFiles() {
		File f = new File(Args.DEF_MAP_ID);
		if (f.exists())
			assertTrue("delete existing file", f.delete());
	}
}

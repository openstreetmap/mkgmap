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
package func;

import java.io.File;

import func.lib.Args;
import func.lib.TestUtils;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 * Base class for tests with some useful routines.  It ensures that created
 * files are deleted before the test starts.
 *
 * @author Steve Ratcliffe
 */
public class Base {
	@Before
	public void baseSetup() {
		TestUtils.deleteOutputFiles();
	}


	protected void checkStdFile() {
		assertTrue("std output file exists", new File(Args.DEF_MAP_FILENAME).exists());
	}

	protected void checkNoStdFile() {
		assertFalse("std output file exists", new File(Args.DEF_MAP_FILENAME).exists());
	}
}

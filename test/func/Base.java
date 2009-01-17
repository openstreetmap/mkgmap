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

import func.lib.Outputs;
import func.lib.TestUtils;
import func.lib.Args;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 * @author Steve Ratcliffe
 */
public class Base {
	@Before
	public void baseSetup() {
		TestUtils.deleteOutputFiles();
	}

	/**
	 * Check that the output contains the given strings.  You can specify
	 * any number of strings.
	 * @param outputs The outputs returned from running the command.
	 * @param strings The list of strings to check.
	 */
	protected void checkOutput(Outputs outputs, String ... strings) {
		String out = outputs.getOut();
		for (String s : strings) {
			if (!out.contains(s)) {
				// Test has failed.  Construct an assertion that will print
				// something that is useful to show the problem.
				assertEquals("contains '" + s + "'",
						"..." + s + "...",
						out);
			}
		}
	}

	/**
	 * Check that the standard error is empty.
	 * @param outputs The outputs returned from running the command.
	 */
	protected void checkNoError(Outputs outputs) {
		assertEquals("no error output", "", outputs.getErr());
	}

	protected void checkStdFile() {
		assertTrue("std output file exists", new File(Args.DEF_MAP_FILENAME).exists());
	}

	protected void checkNoStdFile() {
		assertFalse("std output file exists", new File(Args.DEF_MAP_FILENAME).exists());
	}
}

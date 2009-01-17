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

import org.junit.Test;
import func.lib.TestUtils;
import func.lib.Outputs;

/**
 * A basic check of various arguments that can be passed in.
 *
 * @author Steve Ratcliffe
 */
public class ArgsTest extends Base {
	@Test
	public void testHelp() {
		Outputs outputs = TestUtils.run("--help");
		checkOutput(outputs, "--help=options", "--help=links");
		checkNoError(outputs);
		checkNoStdFile();
	}

	@Test
	public void testHelpOptions() {
		Outputs outputs = TestUtils.run("--help=options");
		checkNoError(outputs);
		checkOutput(outputs, "--mapname=name", "--latin1", "--list-styles");
		checkNoStdFile();
	}

	@Test
	public void testHelpUnknown() {
		Outputs outputs = TestUtils.run("--help=unknown-help-option");
		checkNoError(outputs);
		checkOutput(outputs, "Could not find", "unknown-help-option");
		checkNoStdFile();
	}

	@Test
	public void testListStyles() {
		Outputs op = TestUtils.run("--style-file=test/resources/teststyles", "--list-styles");
		checkNoError(op);
		checkOutput(op, "empty", "main", "simple", "derived", "2.2: A simple test style");
		checkNoStdFile();
	}

	@Test
	public void testListStylesVerbose() {
		Outputs op = TestUtils.run("--style-file=test/resources/teststyles",
				"--verbose", "--list-styles");
		checkNoError(op);
		checkOutput(op, "empty", "main", "simple", "derived",
				"2.2: A simple test style", "Used for many functional tests");
		checkNoStdFile();
	}
}

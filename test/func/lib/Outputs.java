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
package func.lib;

import static org.junit.Assert.*;

/**
 * Standard output and error as produced during a run.
 *
 * @author Steve Ratcliffe
 */
public class Outputs {
	private final String out;
	private final String err;

	public Outputs(String out, String err) {
		this.out = out;
		this.err = err;
	}

	protected String getOut() {
		return out;
	}

	protected String getErr() {
		return err;
	}

	/**
	 * Check that the standard error is empty.
	 */
	public void checkNoError() {
		assertEquals("no error output", "", getErr());
	}

	/**
	 * Check that the output contains the given strings.  You can specify
	 * any number of strings.
	 * @param strings The list of strings to check.
	 */
	public void checkOutput(String... strings) {
		String out = getOut();
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
	 * Check that the output contains the given strings.  You can specify
	 * any number of strings.
	 * @param strings The list of strings to check.
	 */
	public void checkError(String... strings) {
		String err = getErr();
		for (String s : strings) {
			if (!err.contains(s)) {
				// Test has failed.  Construct an assertion that will print
				// something that is useful to show the problem.
				assertEquals("contains '" + s + "'",
						"..." + s + "...",
						err);
			}
		}
	}
}

/*
 * Copyright (C) 2010.
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
package func.style;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import uk.me.parabola.mkgmap.main.StyleTester;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A set of tests written as files, using the StyleTester.
 */
public class ScriptedStyleTest {
	private OutputStream output;

	/**
	 * This is to check that the tests are working.  Run a test that fails
	 * on purpose to check that we can detect this.
	 */
	@Test
	public void failureTest() {
		StyleTester.runSimpleTest("test/resources/rules/fails-on-purpose.fail");
		String result = output.toString();
		assertTrue("failure check", result.contains("ERROR"));
	}

	/**
	 * This is really a whole bunch of tests as we find test files in a
	 * directory and run each one in turn.
	 */
	@Test
	public void testAllRuleFiles() throws IOException {
		File d = new File("test/resources/rules/");
		assertTrue(d.isDirectory());

		// Only run files ending in .test
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.endsWith(".test"))
					return true;
				return false;
			}
		};

		int count = 0;
		File[] files = d.listFiles(filter);
		for (File file : files) {
			setup();
			String name = file.getCanonicalPath();
			StyleTester.runSimpleTest(name);
			String result = output.toString();

			// Make sure that the result does not contain an error
			assertFalse(name, result.contains("ERROR"));

			// make sure that the output was reasonable (and not 'cannot open
			// file', for example).
			assertTrue(name, result.contains("WAY 1:"));

			count++;
		}

		// Check that some tests were run (ie. it will fail if you just delete
		// them all).
		assertTrue("tests run", count >= 3);
	}

	@Before
	public void setup() {
		output = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(output);
		StyleTester.setOut(ps);

		// Make sure that there is a given result set
		StyleTester.forceUseOfGiven(true);
	}
}

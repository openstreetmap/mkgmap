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
/* Create date: 15-Mar-2009 */
package uk.me.parabola.mkgmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests for the command line argument processing.
 * Arguments work like a script.  You can set values multiple times
 * on the command line and they take effect from the point they are
 * set to the point they are set to something else.  File that occur
 * on the command line are processed with the options that are set at
 * that point.
 *
 * Files that are processed at the end (overview map etc) are
 * processed with the options that are in effect at the end of the
 * command line.
 */
public class CommandArgsTest {
	private static final String FILE1 = "00000001.osm";
	private static final String FILE2 = "00000002.osm";
	private static final String FILE3 = "00000003.osm";

	private final ArgCollector proc = new ArgCollector();
	private final CommandArgs carg = new CommandArgs(proc);

	/**
	 * Test that the default mapnames are correct.  Should start with 63240001
	 * and then increase by one for each file.
	 */
	@Test
	public void testDefaultMapnames() {
		carg.readArgs(new String[] {
				"fred.osm", "bob.osm"
		});

		assertEquals("first file", "63240001", proc.getProperty(0, "mapname"));
		assertEquals("second file", "63240002", proc.getProperty(1, "mapname"));
	}

	/**
	 * Test that if you have numeric names, then the mapname is set to the
	 * value in the filename.
	 */
	@Test
	public void testNumericNames() {
		carg.readArgs(new String[] {
				FILE1, FILE2
		});

		assertEquals("first numeric name", "00000001", proc.getProperty(0, "mapname"));
		assertEquals("first numeric name", "00000002", proc.getProperty(1, "mapname"));
	}

	/**
	 * Check that if you have numeric names, you can still override with a
	 * --mapname argument.
	 */
	@Test
	public void testMapnameWithNumericFilenames() {

		String SETNAME1 = "11110000";
		String SETNAME2 = "22220000";

		carg.readArgs(new String[] {
				"--mapname=" + SETNAME1, FILE1,
				"--mapname=" + SETNAME2, FILE2
		});

		ArgCollector.FileArgs arg = proc.getFileArg(0);
		assertEquals("file name", FILE1, arg.name);
		assertEquals("first file", SETNAME1, arg.getProperty("mapname"));

		arg = proc.getFileArg(1);
		assertEquals("file name", FILE2, arg.name);
		assertEquals("second file", SETNAME2, arg.getProperty("mapname"));

	}

	/**
	 * Combinations of all mapname possibilities.
	 */
	@Test
	public void testComplexMapname() {
		String SETNAME = "12345678";

		carg.readArgs(new String[] {
				"fred.osm",
				FILE1,
				"--mapname=" + SETNAME,
				FILE2,
				FILE3,
				"other.osm"
		});

		assertEquals("just default", "63240001", proc.getProperty(0, "mapname"));
		assertEquals("numeric", "00000001", proc.getProperty(1, "mapname"));
		assertEquals("with mapname", SETNAME, proc.getProperty(2, "mapname"));
		assertEquals("continue after set", "12345679", proc.getProperty(3, "mapname"));
		assertEquals("continue after set", "12345680", proc.getProperty(4, "mapname"));
	}

	/**
	 * Argument processor that saves the filenames and the values of
	 * the arguments that are in scope for each argument.
	 */
	private static class ArgCollector implements ArgumentProcessor {
		private class FileArgs {
			private String name;
			private Properties props;
			public String getProperty(String key) {
				return props.getProperty(key);
			}
		}

		private final List<FileArgs> files = new ArrayList<FileArgs>();

		public void processOption(String opt, String val) {
		}

		public void processFilename(CommandArgs args, String filename) {
			ArgCollector.FileArgs fa = new FileArgs();
			fa.name = filename;

			fa.props = new Properties();
			fa.props.putAll(args.getProperties());
			files.add(fa);
		}

		public void endOptions(CommandArgs args) {
		}

		public void startOptions() {
		}

		public FileArgs getFileArg(int n) {
			return files.get(n);
		}

		public String getProperty(int n, String key) {
			return files.get(n).props.getProperty(key);
		}
	}
}

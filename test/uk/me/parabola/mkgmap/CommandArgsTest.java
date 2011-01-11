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

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import uk.me.parabola.imgfmt.Utils;

import func.lib.TestUtils;
import org.junit.Test;

import static org.junit.Assert.*;

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
	private final CommandArgsReader carg = new CommandArgsReader(proc);

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
	 * An argument file is parsed a little differently from regular arguments
	 * as the code is reused with the style files.
	 */
	@Test
	public void testArgumentFile() throws IOException {

		String SETNAME1 = "11110000";
		String SETNAME2 = "22220000";

		String F1 = "VIC.osm.gz";
		String F2 = "NSW.osm.gz";

		String cfile = "family-id=3081\n" +
				"product-id=2601\n" +
				"overview-mapname=30810100\n" +
				"net\n" +
				"gmapsupp\n" +
				"tdbfile\n" +
				"mapname=" + SETNAME1 + "\n" +
				"description=OSM-AU-Victoria\n" +
				"country-name=Australia\n" +
				"country-abbr=AUS\n" +
				"region-name=Victoria\n" +
				"region-abbr=VIC\n" +
				"input-file=" + F1 + "\n" +

				"mapname=" + SETNAME2 + "\n" +
				"description {\nOSM-AU New South Wales}\n" +
				"country-name=Australia\n" +
				"country-abbr=AUS\n" +
				"# Test that comments are ignored til EOL\n" +
				"region-name=New-South-Wales\n" +
				"region-abbr=NSW\n" +
				"input-file=" + F2 + "\n";

		TestUtils.registerFile("30810100.img");
		TestUtils.registerFile("30810100.tdb");
		createFile("args", cfile);

		carg.readArgs(new String[] {
				"-c", "args",
		});

		ArgCollector.FileArgs arg = proc.getFileArg(0);
		assertEquals("file name", F1, arg.name);
		assertEquals("first file", SETNAME1, arg.getMapname());
		assertEquals("region-abbr", "VIC", arg.getProperty("region-abbr"));

		arg = proc.getFileArg(1);
		assertEquals("file name", F2, arg.name);
		assertEquals("second file", SETNAME2, arg.getMapname());
		assertEquals("region-abbr", "NSW", arg.getProperty("region-abbr"));
		assertEquals("description", "OSM-AU New South Wales", arg.getProperty("description"));
	}

	private void createFile(String name, String content) throws IOException {
		TestUtils.registerFile(name);
		Writer w = null;
		try {
			w = new FileWriter(name);
			w.append(content);
		} finally {
			Utils.closeFile(w);
		}
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

			public String getMapname() {
				return getProperty("mapname");
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

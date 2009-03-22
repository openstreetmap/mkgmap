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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.me.parabola.mkgmap.main.Main;

import static org.junit.Assert.*;

/**
 * Useful routines to use during the functional tests.
 * 
 * @author Steve Ratcliffe
 */
public class TestUtils {
	private static final List<String> files = new ArrayList<String>();

	static {
		files.add(Args.DEF_MAP_FILENAME);
		files.add(Args.DEF_MAP_FILENAME2);
		files.add(Args.DEF_GMAPSUPP_FILENAME);
		files.add(Args.DEF_TDB_FILENAME);

		Runnable r = new Runnable() {
			public void run() {
				deleteOutputFiles();
			}
		};
		Thread t = new Thread(r);
		Runtime.getRuntime().addShutdownHook(t);
	}

	/**
	 * Delelete output files that were created by the tests.
	 * Used to clean up before/after a test.
	 */
	public static void deleteOutputFiles() {
		for (String fname : files) {
			File f = new File(fname);

			if (f.exists())
				assertTrue("delete existing file", f.delete());
		}
	}

	public static void registerFile(String name) {
		files.add(name);
	}

	/**
	 * Run with a single argument.  The standard arguments are added first.
	 * @param arg The argument.
	 */
	public static Outputs run(String arg) {
		return run(new String[] {arg});
	}

	/**
	 * Run with the given args.  Some standard arguments are added first.
	 *
	 * To run without the standard args, use runRaw().
	 * @param in The arguments to use.
	 */
	public static Outputs run(String ... in) {
		List<String> args = new ArrayList<String>(Arrays.asList(in));
		args.add(0, Args.TEST_STYLE_ARG);

		OutputStream outsink = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(outsink);

		OutputStream errsink = new ByteArrayOutputStream();
		PrintStream err = new PrintStream(errsink);

		PrintStream origout = System.out;
		PrintStream origerr = System.err;

		try {
			System.setOut(out);
			System.setErr(err);
			Main.main(args.toArray(new String[args.size()]));
		} finally {
			out.close();
			err.close();
			System.setOut(origout);
			System.setErr(origerr);
		}

		return new Outputs(outsink.toString(), errsink.toString());
	}
}

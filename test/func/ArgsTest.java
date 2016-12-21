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

import java.io.FileNotFoundException;

import uk.me.parabola.imgfmt.app.trergn.TREFileReader;
import uk.me.parabola.imgfmt.app.trergn.TREHeader;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;

import func.lib.Args;
import func.lib.Outputs;
import func.lib.TestUtils;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A basic check of various arguments that can be passed in.
 *
 * @author Steve Ratcliffe
 */
public class ArgsTest extends Base {
	@Test
	public void testHelp() {
		Outputs outputs = TestUtils.run("--help");
		outputs.checkOutput("--help=options", "--help=links");
		outputs.checkNoError();
		checkNoStdFile();
	}

	@Test
	public void testHelpOptions() {
		Outputs outputs = TestUtils.run("--help=options");
		outputs.checkNoError();
		outputs.checkOutput("--mapname=name", "--latin1", "--list-styles");
		checkNoStdFile();
	}

	@Test
	public void testHelpUnknown() {
		Outputs outputs = TestUtils.run("--help=unknown-help-option");
		outputs.checkNoError();
		outputs.checkOutput("Could not find", "unknown-help-option");
		checkNoStdFile();
	}

	@Test
	public void testListStyles() {
		Outputs op = TestUtils.run("--style-file=test/resources/teststyles", "--list-styles");
		op.checkNoError();
		op.checkOutput("empty", "main", "simple", "derived", "2.2: A simple test style");
		checkNoStdFile();
	}

	@Test
	public void testListStylesVerbose() {
		Outputs op = TestUtils.run("--style-file=test/resources/teststyles",
				"--verbose", "--list-styles");
		op.checkNoError();
		op.checkOutput("empty", "main", "simple", "derived",
				"2.2: A simple test style", "Used for many functional tests");
		checkNoStdFile();
	}

	@Test
	public void testDisplayPriority() throws FileNotFoundException {
		TestUtils.registerFile("osmmap.img");
		 
		int pri = 42;
		Outputs op = TestUtils.run("--draw-priority=" + pri,
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz");
		op.checkNoError();

		FileSystem fs = openFs(Args.DEF_MAP_FILENAME);
		ImgChannel chan = fs.open(Args.DEF_MAP_ID + ".TRE", "r");
		TREFileReader treFile = new TREFileReader(chan);

		assertEquals("display priority", pri, ((TREHeader) treFile.getHeader()).getDisplayPriority());
	}

	@Test
	public void testNoDescription() {
		Outputs op = TestUtils.run("--description", Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz");
		op.checkNoError();
	}
}

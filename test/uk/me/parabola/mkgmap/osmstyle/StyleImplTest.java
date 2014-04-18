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
 * Create date: 30-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;

import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.StyleInfo;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for reading in a complete style.
 */
public class StyleImplTest {
	private static final String STYLE_LOC = "classpath:teststyles";

	@Test
	public void testGetInfo() throws FileNotFoundException {
		StyleImpl style = new StyleImpl(STYLE_LOC, "simple");

		printStyle(style);
		StyleInfo info = style.getInfo();
		assertEquals("version", "2.2", info.getVersion());
		assertEquals("version", "A simple test style with just one example of most things", info.getSummary());
		assertEquals("version", "This style is used for testing.", info.getLongDescription());
	}

	@Test
	public void testGetOption() throws FileNotFoundException {
		StyleImpl style = new StyleImpl(STYLE_LOC, "simple");

		String val = style.getOption("levels");

		assertEquals("option levels", "0:24\n1:20", val);
	}

	@Test
	public void testEmptyFiles() throws FileNotFoundException {
		StyleImpl style = new StyleImpl(STYLE_LOC, "empty");
		assertNotNull("read style ok", style);
	}

	/**
	 * The case when a style name does not exist.  This has always worked in
	 * the way you would expect - there is an error if it does not exist.
	 * @throws FileNotFoundException This should be thrown.
	 */
	@Test(expected = FileNotFoundException.class)
	public void testBadStyleName() throws FileNotFoundException {
		Style style = new StyleImpl(STYLE_LOC, "no-such-style");
		if (style != null) style= null; // pseudo use the value to calm down FindBugs
	}

	/**
	 * This tests the case when a style-file is given but does not exist.
	 * This has always worked as expected, ie given an error.
	 * @throws FileNotFoundException This should be thrown by the test as the
	 * style does not exist.
	 */
	@Test(expected = FileNotFoundException.class)
	public void testBadStyleFileOnClasspath() throws FileNotFoundException {
		Style style = new StyleImpl("classpath:no-such-place", "default");
		if (style != null) style= null; // pseudo use the value to calm down FindBugs
	}

	/**
	 * Test the case where a style file location is given that does not exist.
	 * Previously it used to default to classpath:styles if it did not exist
	 * which was confusing.
	 * @throws FileNotFoundException Should be thrown as the style-file
	 * location does not exist.
	 */
	@Test(expected = FileNotFoundException.class)
	public void testBadStyleFileOnFilesystem() throws FileNotFoundException {
		Style style = new StyleImpl("/no-such-place/hopefully", "default");
		if (style != null) style= null; // pseudo use the value to calm down FindBugs
	}

	private void printStyle(StyleImpl in) {
		in.dumpToFile(new OutputStreamWriter(System.out));
	}
}

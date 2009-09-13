/*
 * Copyright (c) 2009.
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

package uk.me.parabola.mkgmap.osmstyle;

import java.io.Reader;
import java.io.StringReader;

import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Way;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * More tests for rule sets. Mostly concentrating on ordering issues and
 * not on the resulting type.
 * 
 * @see RuleFileReaderTest
 */
public class RuleSetTest {
	private final String MAXSPEED_EXAMPLE = "highway=* & maxspeed=40mph {set mcssl=40}" +
			"highway=primary & mcssl=40 [0x01]" +
			"highway=* & mcssl=40 [0x02]" +
			"highway=primary [0x3]";

	/**
	 * A test for matching in the correct order with a simple set
	 * of tags.  See also the next test.
	 */
	@Test
	public void testFirstMatch1() {
		RuleSet rs = makeRuleSet("c=d & a=b [0x1]" +
				"a=b & c=d [0x2]" +
				"a=b [0x3]");

		Way el = new Way(1);
		el.addTag("a", "b");
		el.addTag("c", "d");
		GType type = rs.resolveType(el);
		assertNotNull("should be found", type);
		assertEquals("first matching rule wins", 1, type.getType());
	}
	/**
	 * As previous test but with order reversed.  Depending on the order
	 * that the tags iterate from the way, you might get different results.
	 */
	@Test
	public void testFirstMatch2() {
		RuleSet rs = makeRuleSet("a=b & c=d [0x1]" +
				"c=d & a=b [0x2]" +
				"a=b [0x3]");

		Way el = new Way(1);
		el.addTag("a", "b");
		el.addTag("c", "d");
		GType type = rs.resolveType(el);
		assertNotNull("should be found", type);
		assertEquals("first matching rule wins", 1, type.getType());
	}

	/**
	 * An action variable is set on a rule that starts with an exists clause.
	 * We then attempt to match on value that it is 
	 * @throws Exception
	 */
	@Test
	public void testActionVarSetOnExistsRule1() throws Exception {
		RuleSet rs = makeRuleSet(MAXSPEED_EXAMPLE);

		Way el = new Way(1);
		el.addTag("highway", "primary");
		el.addTag("maxspeed", "40mph");
		el.addTag("ref", "A123");
		el.addTag("name", "Long Lane");

		GType type = rs.resolveType(el);
		assertEquals("should match first", 1, type.getType());
	}

	@Test
	public void testActionVarSetOnExistsRule2() throws Exception {
		RuleSet rs = makeRuleSet(MAXSPEED_EXAMPLE);

		Way el = new Way(1);
		el.addTag("highway", "unclassified");
		el.addTag("maxspeed", "40mph");
		el.addTag("ref", "A123");
		el.addTag("name", "Long Lane");

		GType type = rs.resolveType(el);
		assertEquals("should match first", 2, type.getType());
	}

	/**
	 * Check that actions are run in the order given.  Use the add command
	 * to set a variable.  The first add that is run will be the value of
	 * the variable.
	 */
	@Test
	public void testActionOrder() {
		RuleSet rs = makeRuleSet("b=c {add fred=1}" +
				"a=b {add fred=2}" +
				"c=d {add fred=3}" +
				"a=b [0x1]");

		// All of the conditions are set.
		Way el = new Way(1);
		el.addTag("a", "b");
		el.addTag("b", "c");
		el.addTag("c", "d");

		rs.resolveType(el);
		assertEquals("b=c was first action", "1", el.getTag("fred"));
	}

	/**
	 * A chain of rules, some of which contain tags from the element and
	 * some that contain only tags that are set in previous rules.
	 */
	@Test
	public void testOrderChain() {
		RuleSet rs = makeRuleSet("z=1 {add fred=1;}" +
				"fred=1 {add abba=1}" +
				"z=1 & abba=1 {add destiny=1}" +
				"destiny=1 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "1");

		GType type = rs.resolveType(el);
		assertNotNull("chain of commands", type);
	}

	/**
	 * Create a rule set out of a string.  The string is processed
	 * as if it were in a file and the levels spec had been set.
	 */
	private RuleSet makeRuleSet(String in) {
		Reader r = new StringReader(in);

		RuleSet rs = new RuleSet();
		RuleFileReader rr = new RuleFileReader(GType.POLYLINE, LevelInfo.createFromString("0:24 1:20 2:18 3:16 4:14"), rs);
		rr.load(r, "string");
		return rs;
	}
}

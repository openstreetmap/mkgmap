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
 * Create date: 29-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Way;

import static org.junit.Assert.*;
import org.junit.Test;


public class RuleFileReaderTest {
	/**
	 * Test of a file containing a number of different rules, with varying
	 * formatting and including comments.
	 */
	@Test
	public void testLoad() {
		RuleSet rs = makeRuleSet("highway=footway & type=rough [0x2 level 2]\n" +
		"highway=footway | highway = path\n" +
		"  [0x3]\n# comment here\n" +
		"foo=\nbar & bar=two [0x4]\n" +
		"highway=* & oneway=true [0x6 level 1]\n" +
		"");

		Map<String,Rule> ruleMap = rs.getMap();
		Rule rule = ruleMap.get("highway=footway");

		Element el = new Way();
		el.addTag("highway", "footway");

		GType type = rule.resolveType(el);
		assertEquals("plain footway", "[0x3 level 0]", type.toString());

		el.addTag("type", "rough");
		type = rule.resolveType(el);
		assertEquals("rough footway", "[0x2 level 2]", type.toString());

		el.addTag("oneway", "true");
		rule = ruleMap.get("oneway=true");
		type = rule.resolveType(el);
		assertEquals("oneway footway", "[0x6 level 1]", type.toString());
	}

	/**
	 * Test for non-standard level specification.  You can give a range
	 * of levels, rather than defaulting the max end to 0.
	 */
	@Test
	public void testLevel() {
		RuleSet rs = makeRuleSet(
				"highway=primary [0x1 level 1-3]"
		);
		Rule rule = rs.getMap().get("highway=primary");

		Element el = new Way();
		el.addTag("highway", "primary");

		GType type = rule.resolveType(el);
		assertEquals("min level", 1, type.getMinLevel());
		assertEquals("max level", 3, type.getMaxLevel());
	}

	/**
	 * Try out arithmetic comparisons and mixtures of 'and' and 'or'.
	 */
	@Test
	public void testComplexExpressions() {
		String str = "a=b & (c=d | e=f) & x>10 [0x1]\n";
		RuleSet rs = makeRuleSet(str);
		Rule rule = rs.getMap().get("a=b");

		Element el = new Way();
		el.addTag("a", "b");
		el.addTag("c", "d");
		el.addTag("x", "11");

		GType type = rule.resolveType(el);
		assertEquals("expression ok", 1, type.getType());

		// fails with x less than 10
		el.addTag("x", "9");
		type = rule.resolveType(el);
		assertNull("x too low", type);

		// also fails with x equal to 10
		el.addTag("x", "10");
		type = rule.resolveType(el);
		assertNull("x too low", type);

		// OK with x > 10
		el.addTag("x", "100");
		el.addTag("e", "f");
		type = rule.resolveType(el);
		assertEquals("c and e set", 1, type.getType());

		el.addTag("c", "");
		el.addTag("e", "");
		type = rule.resolveType(el);
		assertNull("none of c and e set", type);

		el.addTag("e", "f");
		type = rule.resolveType(el);
		assertEquals("e is set to f", 1, type.getType());
	}

	/**
	 * You can now have a wild card at the top level.
	 */
	@Test
	public void testWildcardTop() {
		RuleSet rs = makeRuleSet("highway=* {set a=fred} [0x1]\n");

		Rule rule = rs.getMap().get("highway=*");
		assertNotNull("rule found", rule);
		
		Element el = new Way();
		el.addTag("highway", "secondary");
		GType type = rule.resolveType(el);
		assertNotNull("can find match", type);
		assertEquals("correct type", 1, type.getType());
		assertEquals("tag set", "fred", el.getTag("a"));
	}

	/**
	 * Deal with cases such as
	 * (a = b | a = c) & d!=*
	 * where there is no key at the top level.  This gets converted
	 * to: (a=b & d!=*) | (a=c & d!= *) which can then be used.
	 *
	 * This is applied recursively, so you can have chains of any length.
	 */
	@Test
	public void testLeftSideOr() {
		RuleSet rs = makeRuleSet("(a = b | a = c | a=d) & e!=* [0x2]" +
				"a=c & e!=* [0x1]");

		Map<String, Rule> map = rs.getMap();
		assertNotNull("a=b chain", map.get("a=b"));
		assertNotNull("a=c chain", map.get("a=c"));
		assertNotNull("a=d chain", map.get("a=d"));

		// get the a=c chain and look at it more closely
		Rule rule = map.get("a=c");
		Element el = new Way();
		GType type = rule.resolveType(el);
		assertNotNull("match e not existing", type);
		assertEquals("correct type", 2, type.getType());
	}

	/**
	 * You can now have a wild card at the top level, here we have & between
	 * two of them.
	 */
	@Test
	public void testWildcard2() {
		RuleSet rs = makeRuleSet("highway=* & z=* {set a=square} [0x1]\n");

		Rule rule = rs.getMap().get("highway=*");
		assertNotNull("rule found", rule);

		Element el = new Way();
		el.addTag("highway", "secondary");
		GType type = rule.resolveType(el);
		assertNull("type not found with no z tag", type);

		// now add z
		el.addTag("z", "1");
		type = rule.resolveType(el);
		assertNotNull("found match", type);
		assertEquals("correct type", 1, type.getType());
		assertEquals("tag set", "square", el.getTag("a"));
	}

	/**
	 * Tests for the road classification and other parts of the GType.
	 */
	@Test
	public void testGType() {
		RuleSet rs = makeRuleSet("highway=motorway " +
				"[0x1 road_class=4 road_speed=7 default_name='motor way']\n");

		Rule rule = rs.getMap().get("highway=motorway");
		Element el = new Way();
		el.addTag("highway", "motorway");
		GType type = rule.resolveType(el);

		// Check that the correct class and speed are returned.
		assertEquals("class", 4, type.getRoadClass());
		assertEquals("class", 7, type.getRoadSpeed());
		assertEquals("default name", "motor way", type.getDefaultName());
	}

	/**
	 * Check for the regexp handling.
	 */
	@Test
	public void testRegexp() {
		RuleSet rs = makeRuleSet("highway=* & name ~ 'blue.*' [0x2]\n");

		Rule rule = rs.getMap().get("highway=*");
		assertNotNull("rule found", rule);

		// Set up element with matching name
		Element el = new Way();
		el.addTag("highway", "secondary");
		el.addTag("name", "blue sq");
		GType type = rule.resolveType(el);
		assertNotNull("matched regexp", type);
		assertEquals("matched type", 2, type.getType());

		// change name to one that should not match
		el.addTag("name", "yellow");
		type = rule.resolveType(el);
		assertNull("no match for yello", type);
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

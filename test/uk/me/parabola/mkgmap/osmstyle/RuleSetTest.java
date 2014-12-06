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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;
import uk.me.parabola.mkgmap.reader.osm.Way;

import org.junit.Test;

import static func.lib.TestUtils.makeRuleSet;
import static org.junit.Assert.*;

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
		GType type = getFirstType(rs, el);
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
		GType type = getFirstType(rs, el);
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

		GType type = getFirstType(rs, el);
		assertEquals("should match first", 1, type.getType());
	}

	@Test
	public void testActionVarSetOnExistsRule2() {
		RuleSet rs = makeRuleSet(MAXSPEED_EXAMPLE);

		Way el = new Way(1);
		el.addTag("highway", "unclassified");
		el.addTag("maxspeed", "40mph");
		el.addTag("ref", "A123");
		el.addTag("name", "Long Lane");

		GType type = getFirstType(rs, el);
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

		getFirstType(rs, el);
		assertEquals("b=c was first action", "1", el.getTag("fred"));
	}

	/**
	 * Match on a tag that was set in a previous action rule and was not
	 * on the original element.
	 */
	@Test
	public void testMatchOnSetTag() {
		RuleSet rs = makeRuleSet("highway=yes {set abcxyz = 1}" +
				"abcxyz=1 [0x1]");

		Way el = new Way(1);
		el.addTag("highway", "yes");

		GType type = getFirstType(rs, el);
		assertNotNull("type matched on previously set tag", type);
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
				"destiny=1 [0x1]" +
				"z=1 [0x2]");

		Way el = new Way(1);
		el.addTag("z", "1");

		GType type = getFirstType(rs, el);
		assertNotNull("chain of commands", type);
		assertEquals("'destiny' should be selected", 1, type.getType());
	}

	/**
	 * A chain of rules, some of which contain tags from the element and
	 * some that contain only tags that are set in previous rules.
	 */
	@Test
	public void testOrderChain2() {
		RuleSet rs = makeRuleSet("z=1 {add fred=1;}" +
				"fred=1 {add abba=1}" +
				"abba=1 {add destiny=1}" +
				"destiny=1 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "1");

		GType type = getFirstType(rs, el);
		assertNotNull("chain of commands", type);
	}

	/**
	 * Append to a variable in the correct order as in the rule set.
	 */
	@Test
	public void testAppendInOrder() {
		RuleSet rs = makeRuleSet("highway=primary {set R='${R} a'}" +
				"ref=A1 {set R='${R} b'}" +
				"z=1 {set R='${R} c'}" +
				"a=1 {set R='${R} d'}");

		Way el = new Way(1);
		el.addTag("R", "init");
		el.addTag("highway", "primary");
		el.addTag("ref", "A1");
		el.addTag("z", "1");
		el.addTag("a", "1");

		getFirstType(rs, el);
		String s = el.getTag("R");
		assertEquals("appended value", "init a b c d", s);
	}

	/**
	 * Rules should only be evaluated once for an element.  Because of the
	 * way that we handle rules that may get run after tags are set in actions
	 * it is possible that a rule would get run twice if not careful.
	 *
	 * It is not that easy to trigger, as this is the second attempt at
	 * showing it is possible...
	 */
	@Test
	public void testRuleEvaluatedOnce() {
		RuleSet rs = makeRuleSet("highway=primary " +
				"  {set highway=primary; set result='${result} 1';}" +
				"highway='primary' {set result='${result} 2'");
		Way el = new Way(1);
		el.addTag("highway", "primary");
		el.addTag("result", "0");

		getFirstType(rs, el);
		assertEquals("rules run once", "0 1 2", el.getTag("result"));
	}

	/**
	 * The example that was in the check in comment, make sure it actually
	 * does work ;)
	 */
	@Test
	public void testCheckinExample() {
		RuleSet rs = makeRuleSet("highway=motorway  {set blue=true;}\n" +
				"blue=true  [0x1 ]\n" +
				"highway=motorway [0x2]");

		Way el = new Way(1);
		el.addTag("highway", "motorway");

		GType type = getFirstType(rs, el);
		assertEquals("first match is on blue", 1, type.getType());
	}

	@Test
	public void testActionsMixedWithTypes() {
		RuleSet rs = makeRuleSet("highway=primary {set marker=1}" +
				"marker=2 [0x1]" +
				"highway=primary {set marker=2}" +
				"marker=2 [0x2]");

		Way el = new Way(1);
		el.addTag("highway", "primary");

		GType type = getFirstType(rs, el);
		assertEquals("second marker rule", 2, type.getType());
	}

	@Test
	public void testContinueDefault() {
		RuleSet rs = makeRuleSet("highway=footway {set surface=good;} [0x1 continue]" +
				"surface=good [0x20]" +
				"surface=bad [0x30]");

		Way el = new Way(1);
		el.addTag("highway", "footway");
		el.addTag("surface", "bad");

		List<GType> list = resolveList(rs, el);
		assertEquals("number of lines returned", 2, list.size());
		assertEquals("surface setting not propagated", "bad", el.getTag("surface"));
		assertEquals("result type", 0x30, list.get(1).getType());
	}

	@Test
	public void testContinuePropagate() {
		RuleSet rs = makeRuleSet("highway=footway {set surface=good;} [0x1 continue propagate]" +
				"surface=good [0x20]" +
				"surface=bad [0x30]");

		Way el = new Way(1);
		el.addTag("highway", "footway");
		el.addTag("surface", "bad");

		List<GType> list = resolveList(rs, el);
		assertEquals("number of lines returned", 2, list.size());
		assertEquals("surface setting is propagated", "good", el.getTag("surface"));
		assertEquals("result type", 0x20, list.get(1).getType());
	}

	@Test
	public void testContinueNoPropagate() {
		RuleSet rs = makeRuleSet("highway=footway {set surface=good;} [0x1 continue no_propagate]" +
				"surface=good [0x20]" +
				"surface=bad [0x30]");

		Way el = new Way(1);
		el.addTag("highway", "footway");
		el.addTag("surface", "bad");

		List<GType> list = resolveList(rs, el);
		assertEquals("number of lines returned", 2, list.size());
		assertEquals("surface setting is not propagated", "bad", el.getTag("surface"));
		assertEquals("result type", 0x30, list.get(1).getType());
	}

	@Test
	public void testContinueWithActions() {
		RuleSet rs = makeRuleSet("highway=footway {set surface=good;} [0x1 continue with_actions]" +
				"surface=good [0x20]" +
				"surface=bad [0x30]");

		Way el = new Way(1);
		el.addTag("highway", "footway");
		el.addTag("surface", "bad");

		List<GType> list = resolveList(rs, el);
		assertEquals("number of lines returned", 2, list.size());
		assertEquals("surface setting is propagated", "good", el.getTag("surface"));
		assertEquals("result type", 0x20, list.get(1).getType());
	}

	@Test
	public void testContinueChangesTag() {
		RuleSet rs = makeRuleSet("highway=crossing & crossing=zebra_crossing" +
				"    {set highway=deleted_crossing} [0x10404 resolution 24 continue propagate]" +
				"highway=crossing [0x1010f resolution 24 continue]" +
				"highway=deleted_crossing [0x6 resolution 24 continue]"
		);

		Way el = new Way(1);
		el.addTag("highway", "crossing");
		el.addTag("crossing", "zebra_crossing");

		List<GType> list = resolveList(rs, el);
		assertEquals("first element", 0x10404, list.get(0).getType());
		assertEquals("second element", 0x6, list.get(1).getType());
	}

	private List<GType> resolveList(RuleSet rs, Way el) {
		final List<GType> list = new ArrayList<GType>();
		rs.resolveType(el, new TypeResult() {
			public void add(Element el, GType type) {
				list.add(type);
			}
		});
		return list;
	}

	private GType getFirstType(Rule rs, Element el) {
		final List<GType> types = new ArrayList<GType>();
		rs.resolveType(el, new TypeResult() {
			public void add(Element el, GType type) {
				types.add(type);
			}
		});
		if (types.isEmpty())
			return null;
		else
			return types.get(0);
	}
}

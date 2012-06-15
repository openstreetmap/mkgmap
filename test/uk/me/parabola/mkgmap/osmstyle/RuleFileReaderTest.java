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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;
import uk.me.parabola.mkgmap.reader.osm.Way;

import org.junit.Test;

import static org.junit.Assert.*;


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

		Element el = new Way(1);
		el.addTag("highway", "footway");

		GType type = getFirstType(rs, el);
		assertEquals("plain footway", "[0x3 level 0]", type.toString());

		el.addTag("type", "rough");
		type = getFirstType(rs, el);
		assertEquals("rough footway", "[0x2 level 2]", type.toString());
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

		Element el = new Way(1);
		el.addTag("highway", "primary");

		GType type = getFirstType(rs, el);
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

		Element el = new Way(1);
		el.addTag("a", "b");
		el.addTag("c", "d");
		el.addTag("x", "11");

		GType type = getFirstType(rs, el);
		assertEquals("expression ok", 1, type.getType());

		// fails with x less than 10
		el.addTag("x", "9");
		type = getFirstType(rs, el);
		assertNull("x too low", type);

		// also fails with x equal to 10
		el.addTag("x", "10");
		type = getFirstType(rs, el);
		assertNull("x too low", type);

		// OK with x > 10
		el.addTag("x", "100");
		el.addTag("e", "f");
		type = getFirstType(rs, el);
		assertEquals("c and e set", 1, type.getType());

		el.addTag("c", "");
		el.addTag("e", "");
		type = getFirstType(rs, el);
		assertNull("none of c and e set", type);

		el.addTag("e", "f");
		type = getFirstType(rs, el);
		assertEquals("e is set to f", 1, type.getType());
	}

	/**
	 * Test based on email on the mailing list at:
	 * http://www.mkgmap.org.uk/pipermail/mkgmap-dev/2009q3/003009.html
	 * See that email for an explanation.
	 */
	@Test
	public void testComparasons() {
		String str = "highway=null_null & layer<0  [0x01 resolution 10]\n" +
				"highway=null_null & layer=0  [0x02 resolution 10]\n" +
				"highway=null_null & layer>0  [0x03 resolution 10]\n" +
				"highway=null_null & layer='-1'  [0x04 resolution 10]\n" +
				"highway=null_null & layer='0'  [0x05 resolution 10]\n" +
				"highway=null_null & layer='1'  [0x06 resolution 10]\n" +
				"highway=null_null & layer='+1'  [0x07 resolution 10]\n" +
				"highway=null_null   [0x08 resolution 10]";
		RuleSet rs = makeRuleSet(str);

		// 9902
		Element el = new Way(1);
		el.addTag("highway", "null_null");
		el.addTag("layer", "-1");

		GType type = getFirstType(rs, el);
		assertEquals("9902 layer = -1", 0x1, type.getType());

		// 9912
		el.addTag("layer", "0");
		type = getFirstType(rs, el);
		assertEquals("9912 layer = 0", 0x2, type.getType());

		// 9922
		el.deleteTag("layer");
		type = getFirstType(rs, el);
		assertEquals("9922 no layer tag", 0x8, type.getType());

		// 9932
		el.addTag("layer", "1");
		type = getFirstType(rs, el);
		assertEquals("9932 layer is 1", 0x3, type.getType());

		// 9952
		el.addTag("layer", "+1");
		assertEquals("9952 layer is +1", 0x3, type.getType());
	}

	@Test
	public void testMultipleActions() {
		String rstr = "highway=footway {add access = no; add foot = yes} [0x16 road_class=0 road_speed=0 resolution 23]";
		RuleSet rs = makeRuleSet(rstr);

		Element el = new Way(1);
		el.addTag("highway", "footway");

		getFirstType(rs, el);
		assertEquals("access set", "no", el.getTag("access"));
		assertEquals("access set", "yes", el.getTag("foot"));
	}

	/**
	 * You can now have a wild card at the top level.
	 */
	@Test
	public void testWildcardTop() {
		RuleSet rs = makeRuleSet("highway=* {set a=fred} [0x1]\n");

		assertNotNull("rule found", rs);
		
		Element el = new Way(1);
		el.addTag("highway", "secondary");
		GType type = getFirstType(rs, el);
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

		assertNotNull("a=b chain", rs);
		assertNotNull("a=c chain", rs);
		assertNotNull("a=d chain", rs);

		// get the a=c chain and look at it more closely
		Element el = new Way(1);
		el.addTag("a", "c");
		GType type = getFirstType(rs, el);

		assertNotNull("match e not existing", type);
		assertEquals("correct type", 2, type.getType());

		el = new Way(2);
		el.addTag("a", "d");
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

		assertNotNull("rule found", rs);

		Element el = new Way(1);
		el.addTag("highway", "secondary");
		GType type = getFirstType(rs, el);
		assertNull("type not found with no z tag", type);

		// now add z
		el.addTag("z", "1");
		type = getFirstType(rs, el);
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

		Element el = new Way(1);
		el.addTag("highway", "motorway");
		GType type = getFirstType(rs, el);

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

		assertNotNull("rule found", rs);

		// Set up element with matching name
		Element el = new Way(1);
		el.addTag("highway", "secondary");
		el.addTag("name", "blue sq");
		GType type = getFirstType(rs, el);
		assertNotNull("matched regexp", type);
		assertEquals("matched type", 2, type.getType());

		// change name to one that should not match
		el.addTag("name", "yellow");
		type = getFirstType(rs, el);
		assertNull("no match for yello", type);
	}

	@Test
	public void testRegex2() {
		RuleSet rs = makeRuleSet("a=b & (smoothness ~ '.*(bad|horrible|impassable)' | sac_scale ~ '.*(mountain|alpine)_hiking') [0x1]" +
				"a = '>=' & b = '>' [0x2]");
		assertNotNull(rs);

		Element el = new Way(1);
		el.addTag("a", "b");
		el.addTag("smoothness", "zzzbad");

		GType type = getFirstType(rs, el);
		assertNotNull(type);

		assertEquals("matched .*bad", 1, type.getType());

		el = new Way(1);
		el.addTag("a", "b");
		el.addTag("sac_scale", "zzz alpine_hiking");

		type = getFirstType(rs, el);
		assertNotNull(type);

		el = new Way(1);
		el.addTag("a", "b");
		el.addTag("sac_scale", "zzz alp_hiking");
		type = getFirstType(rs, el);
		assertNull(type);

		el = new Way(1);
		el.addTag("a", ">=");
		el.addTag("b", ">");
		type = getFirstType(rs, el);
		assertNotNull(type);
		assertEquals("match string that is the same as an operator", 2, type.getType());
	}

	/**
	 * Some operations could not originally be used by themselves but now they are converted
	 * into expressions that can be handled automatically. The following few tests verify this.
	 */
	@Test
	public void testRegexAtTop() {
		RuleSet rs = makeRuleSet("QUOTA ~ ' [05]00\\.0+' [0x2]");
		Element el = new Way(1);
		el.addTag("QUOTA", " 500.0");

		GType type = getFirstType(rs, el);
		assertNotNull(type);
		assertEquals(2, type.getType());
	}

	@Test
	public void testNEAtTop() {
		RuleSet rs = makeRuleSet("QUOTA != 'fred' [0x2]");
		Element el = new Way(1);
		el.addTag("QUOTA", "tom");

		GType type = getFirstType(rs, el);
		assertNotNull(type);
		assertEquals(2, type.getType());
	}

	@Test
	public void testNumberOpAtTop() {
		RuleSet rs = makeRuleSet("QUOTA > 10 [0x1] QUOTA < 6 [0x2]");
		Element el = new Way(1);
		el.addTag("QUOTA", "2");

		GType type = getFirstType(rs, el);
		assertNotNull(type);
		assertEquals(2, type.getType());
	}

	/**
	 * This simply is to make sure that actions that affect their own
	 * conditions do not hang. There are no defined semantics for this.
	 */
	@Test
	public void testSelfReference() {
		RuleSet rs = makeRuleSet("iii=* { set iii=no }");
		//Rule rule = rs.getMap().get("foot=*");
		Way el = new Way(1);
		el.addTag("foot", "yes");
		el.addTag("iii", "xyz");
		getFirstType(rs, el);
	}

	/**
	 * Test the not operator.
	 */
	@Test
	public void testNot() {
		RuleSet rs = makeRuleSet("tunnel=yes & !(route=mtb | route=bicycle) [0x1]");
		//RuleSet rs = makeRuleSet("tunnel=yes & (route!=mtb & route!=bicycle) [0x1]");

		Way el = new Way(1);
		el.addTag("tunnel", "yes");
		el.addTag("route", "abc");
		getFirstType(rs, el);
	}

	@Test
	public void testGTR() {
		RuleSet rs = makeRuleSet("z=0 & a >= 10 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "0");
		el.addTag("a", "9");
		GType type = getFirstType(rs, el);
		assertNull("a less that 10, no result", type);

		el.addTag("a", "10");
		type = getFirstType(rs, el);
		assertNotNull(type);
		assertEquals("Valid type returned", 1, type.getType());

		el.addTag("a", "11");
		type = getFirstType(rs, el);
		assertNotNull(type);
		assertEquals("Valid type returned", 1, type.getType());
	}

	@Test
	public void testLTE() {
		RuleSet rs = makeRuleSet("z=0 & a <= 10 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "0");
		el.addTag("a", "9");
		GType type = getFirstType(rs, el);
		assertNotNull("a less that 10", type);
		assertEquals("found type for a <= 10", 1, type.getType());

		el.addTag("a", "10");
		type = getFirstType(rs, el);
		assertNotNull(type);
		assertEquals("Found type for a == 10", 1, type.getType());

		el.addTag("a", "11");
		type = getFirstType(rs, el);
		assertNull("a is 11, a <= 10 is false", type);
	}

	@Test
	public void testNE() {
		RuleSet rs = makeRuleSet("z=0 & a != 10 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "0");
		el.addTag("a", "9");
		GType type = getFirstType(rs, el);
		assertNotNull("a is 9 so a!=10 is true", type);

		el.addTag("a", "10");
		type = getFirstType(rs, el);
		assertNull("a is 10, so a!=10 is false", type);
	}

	/**
	 * Test values such as 3.5 in comparisons.
	 * Originally non-integer values were not allowed and were not even recognised.
	 */
	@Test
	public void testDecimalValues() {
		RuleSet rs = makeRuleSet("z=yes & a < 3.5 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "yes");

		// Is less than so
		el.addTag("a", "2");
		GType type = getFirstType(rs, el);
		assertNotNull("a is less than 3.5", type);

		el.addTag("a", "4");
		type = getFirstType(rs, el);
		assertNull("a is greater than 3.5", type);
	}

	@Test
	public void testDecimalAndDecimalCompare() {
		RuleSet rs = makeRuleSet("z=yes & a < 3.5 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "yes");

		// Is less than so
		el.addTag("a", "3.49");
		GType type = getFirstType(rs, el);
		assertNotNull("a is less than 3.5", type);

		el.addTag("a", "3.55");
		type = getFirstType(rs, el);
		assertNull("a is greater than 3.5", type);
	}

	/**
	 * A moderately complex set of conditions and substitutions.
	 */
	@Test
	public void testMtbRules() {
		RuleSet rs = makeRuleSet(
				"(mtb:scale=*  | mtb:scale:uphill=*) & route=mtb" +
						"{ name 'mtbrt${mtb:scale|def:.}${mtb:scale:uphill|def:.} ${name}' " +
						"       | 'mtbrt${mtb:scale|def:.}${mtb:scale:uphill|def:.}' }" +
						" (mtb:scale=* | mtb:scale:uphill=*) & route!=mtb " +
						"{ name 'mtb${mtb:scale|def:.}${mtb:scale:uphill|def:.} ${name}' " +
						"       | 'mtb${mtb:scale|def:.}${mtb:scale:uphill|def:.}' }"
				
				);

		Way el = new Way(1);
		el.addTag("route", "mtb");
		el.addTag("mtb:scale", "2");
		getFirstType(rs, el);
		assertEquals("mtbrt2.", el.getName());

		el = new Way(1);
		el.addTag("route", "mtb");
		el.addTag("mtb:scale:uphill", "3");
		getFirstType(rs, el);
		assertEquals("mtbrt.3", el.getName());

		el = new Way(1);
		el.addTag("name", "myname");
		el.addTag("route", "mtb");
		el.addTag("mtb:scale:uphill", "3");
		getFirstType(rs, el);
		assertEquals("mtbrt.3 myname", el.getName());

		el = new Way(1);
		el.addTag("mtb:scale:uphill", "3");
		getFirstType(rs, el);
		assertEquals("mtb.3", el.getName());
	}

	/**
	 * Appending to an existing tag.
	 */
	@Test
	public void testTagAppend() {
		RuleSet rs = makeRuleSet(
				"highway=*{set fullname='${ref}';" +
						"set fullname='${fullname} ${name}';" +
						"set fullname='${fullname} ${name1}';" +
						"set fullname='${fullname} ${name2}';" +
						"name '${fullname}'}"
		);
		
		Way el = new Way(1);
		el.addTag("highway", "road");
		el.addTag("ref", "A1");
		el.addTag("name", "long lane");
		el.addTag("name1", "foo");
		el.addTag("name2", "bar");

		getFirstType(rs, el);
		assertEquals("appended name", "A1 long lane foo bar", el.getName());
	}

	@Test
	public void testExists() {
		RuleSet rs = makeRuleSet("highway=* & maxspeed=40 {set mcssl=40}" +
				"highway=primary & mcssl=40 [0x2 ]" +
				"highway=* & mcssl=40 [0x3]");
		Way el = new Way(1);
		el.addTag("ref", "A123");
		el.addTag("name", "Long Lane");
		el.addTag("highway", "primary");
		el.addTag("maxspeed", "40");

		GType type = getFirstType(rs, el);
		assertNotNull("finds the type", type);
		assertEquals("resulting type", 2, type.getType());
	}

	/**
	 * Test the continue keyword.  If a type is marked with this word, then
	 * further matches are performed and this might result in more types
	 * being added.
	 */
	@Test
	public void testContinue() {
		RuleSet rs = makeRuleSet("highway=primary [0x1 continue]" +
				"highway=primary [0x2 continue]" +
				"highway=primary [0x3]" +
				"highway=primary [0x4]"
		);

		Way el = new Way(1);
		el.addTag("highway", "primary");

		final List<GType> list = new ArrayList<GType>();

		rs.resolveType(el, new TypeResult() {
			public void add(Element el, GType type) {
				list.add(type);
			}
		});

		GType type = list.get(0);
		assertEquals("first type", 1, type.getType());
		assertEquals("continue search", true, type.isContinueSearch());

		assertEquals("number of result types", 3, list.size());
		assertEquals("type of first", 1, list.get(0).getType());
		assertEquals("type of second", 2, list.get(1).getType());
		assertEquals("type of third", 3, list.get(2).getType());
	}

	@Test
	public void testContinueRepeat() {
		RuleSet rs = makeRuleSet("highway=primary [0x1 continue]" +
				"highway=primary [0x2 continue]" +
				"highway=primary [0x3]" +
				"highway=primary [0x4]"
		);

		Way el = new Way(1);
		el.addTag("highway", "primary");

		for (int i = 0; i < 3; i++) {
			GType type = getFirstType(rs, el);
			assertEquals("first type", 1, type.getType());
			assertEquals("continue search", true, type.isContinueSearch());
		}
	}

	/**
	 * The main point of this test is to ensure that all the examples compile.
	 */
	@Test
	public void testComplexRegex() {
		RuleSet rs = makeRuleSet(
				//"a~b      [0x0]" +
				"a~b & c=d  [0x1]" +
						"a~b & c~d & e=f   [0x2]" +
						"(a~b | c~d) & e=f  [0x3]" +
						"(a~b | c~d) & e=f & g=h  [0x4]" +
						"((a~b | c~d) & e=f) & g=h [0x5]" +
						"e=f & g=h & (a~b | c~'d.*')  [0x6]" +
						"(e=f & g=h) & (a~b | c~'d.*')  [0x7]" +
						"a=* & b=* & c=d" +
						"a=* & (b=* | c=d)" +
						""
		);

		Way el = new Way(1);
		el.addTag("c", "df");
		el.addTag("g", "h");
		el.addTag("e", "f");

		GType type = getFirstType(rs, el);
		assertNotNull("matches a rule", type);
	}

	@Test
	public void testTagsUsed() {
		RuleSet rs = makeRuleSet("highway=primary & surface=good [0x1]" +
				"A=B | C=D & E~'f.*' & G!=9 & K=* & L!=* [0x2]");

		Set<String> tags = rs.getUsedTags();
		assertEquals("number of tags used", 8, tags.size());
		assertTrue("has highway", tags.contains("highway"));
		assertTrue("has surface", tags.contains("surface"));
		assertTrue("has A", tags.contains("A"));
		assertTrue("has C", tags.contains("C"));
		assertTrue("has E", tags.contains("E"));
		assertTrue("has G", tags.contains("G"));
		assertTrue("has K", tags.contains("K"));
		assertTrue("has L", tags.contains("L"));
	}

	/**
	 * There is a case where a tag is only used in an action but not in any
	 * expression.  If we dropped the tags it would not be available for the
	 * action.  A typical example might be name.
	 */
	@Test
	public void testTagsUsedInActions() {
		RuleSet rs = makeRuleSet("A=B { set t='${C}'; add t='${D} p ${E}'; name '${F} ${G}'; rename K L");

		Set<String> tags = rs.getUsedTags();
		assertTrue("has A", tags.contains("A"));
		assertTrue("has C", tags.contains("C"));
		assertTrue("has D", tags.contains("D"));
		assertTrue("has E", tags.contains("E"));
		assertTrue("has F", tags.contains("F"));
		assertTrue("has G", tags.contains("G"));
		assertTrue("has K", tags.contains("K"));
	}

	/**
	 * Resolve the rule set with the given element and get the first
	 * resolved type.
	 */
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

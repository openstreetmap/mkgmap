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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;


public class RuleFileReaderTest {
	@Test
	public void testLoad() {
		RuleSet rs = makeRuleSet("highway=footway & type=rough [0x2 level 2]\n" +
		"highway=footway | highway = path\n" +
		"  [0x3]\n" +
		"foo=\nbar & bar=two [0x4]\n" +
		//"amenity=pub [0x5]\n" +
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

	@Test
	public void testLevel() {
		RuleSet rs = makeRuleSet(
				"highway=primary [0x1 level 1]"
		);
		Rule rule = rs.getMap().get("highway=primary");

		Element el = new Way();
		el.addTag("highway", "primary");

		GType type = rule.resolveType(el);
		assertEquals("level should be 1", 1, type.getMaxLevel());
	}

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

		el.addTag("x", "9");
		type = rule.resolveType(el);
		assertNull("x too low", type);

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

	private RuleSet makeRuleSet(String in) {
		Reader r = new StringReader(in);

		RuleSet rs = new RuleSet();
		RuleFileReader rr = new RuleFileReader(GType.POLYLINE, LevelInfo.createFromString("0:24 1:20 2:18 3:16 4:14"), rs);
		rr.load(r, "string");
		return rs;
	}
}

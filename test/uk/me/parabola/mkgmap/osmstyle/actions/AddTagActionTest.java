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
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Way;

import static org.junit.Assert.*;
import org.junit.Test;


public class AddTagActionTest {
	private static final String REFVAL = "ref123";
	private static final String PLACENAME = "Trefriw";

	/**
	 * If there are no substitutions, then the exact same string is
	 * used.
	 */
	@Test
	public void testNoSub() {
		String value = "fred";
		Action act = new AddTagAction("a", value);
		Element el = stdElement();
		act.perform(el);
		assertSame("a not changed", value, el.getTag("a"));
	}

	/**
	 * Simple test, substituting the whole string.
	 */
	@Test
	public void testBareSubst() {
		Action act = new AddTagAction("a", "${ref}");

		Element el = stdElement();
		act.perform(el);

		assertEquals("subst ref", REFVAL, el.getTag("a"));
	}

	/**
	 * Complex string with more than one substitution.
	 */
	@Test
	public void testManySubs() {
		Action act = new AddTagAction("a", "Road ${ref}, name ${name:cy}");
		Element el = stdElement();
		act.perform(el);

		assertEquals("many substitutions",
				"Road " + REFVAL + ", name " + PLACENAME,
				el.getTag("a"));
	}

	/**
	 * If a substitution tag has no value then the value of the tag is not
	 * changed by the action.
	 */
	@Test
	public void testNoValue() {
		Action act = new AddTagAction("a", "Road ${noexist}, name ${name:cy}", true);
		Element el = stdElement();
		String val = "before";
		el.addTag("a", val);
		act.perform(el);
		assertSame("no substitution", val, el.getTag("a"));
	}

	/**
	 * Test substitutions that get a conversion factor applied to them.
	 */
	@Test
	public void testNumberWithUnit() {
		Action act = new AddTagAction("result", "${ele|conv:m=>ft}");

		Element el = stdElement();
		el.addTag("ele", "100");
		act.perform(el);

		assertEquals("subst ref", "328", el.getTag("result"));
	}

	@Test
	public void testSubstWithDefault() {
		Action act = new AddTagAction("result", "${ref|def:default-ref}", true);

		Element el = stdElement();
		act.perform(el);

		assertEquals("ref not defaulted", REFVAL, el.getTag("result"));

		act = new AddTagAction("result", "${ref|def:default-ref}", true);
		el = stdElement();
		el.deleteTag("ref");
		act.perform(el);
		assertEquals("ref was defaulted", "default-ref", el.getTag("result"));
	}

	private Element stdElement() {
		Element el1 = new Way();
		el1.addTag("ref", REFVAL);
		el1.addTag("name:cy", PLACENAME);
		return el1;
	}
}

/*
 * Copyright (C) 2013.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Way;

import org.junit.Test;

import static org.junit.Assert.*;


public class AddAccessActionTest {
	private static final String ACCESSVAL = "no";

	/**
	 * If there are no substitutions, then the exact same string is
	 * used.
	 */
	@Test
	public void testNoSub() {
		String value = "fred";
		Action act = new AddAccessAction(value, false);
		Element el = stdElement();
		act.perform(el);
		for (String accessTag : StyledConverter.ACCESS_TAGS) {
			assertSame("a not changed", value, el.getTag(accessTag));
		}
	}

	/**
	 * Simple test, substituting the whole string.
	 */
	@Test
	public void testBareSubst() {
		Action act = new AddAccessAction("${access}", false);

		Element el = stdElement();
		act.perform(el);

		for (String accessTag : StyledConverter.ACCESS_TAGS) {
			assertEquals("subst access", ACCESSVAL, el.getTag(accessTag));
		}
	}

	/**
	 * If a substitution tag has no value then the value of the tag is not
	 * changed by the action.
	 */
	@Test
	public void testNoValue() {
		Action act = new AddAccessAction("${noexist}", true);
		Element el = stdElement();
		String val = "before";
		el.addTag("mkgmap:bike", val);
		act.perform(el);
		assertSame("no substitution", val, el.getTag("mkgmap:bike"));
	}

	/**
	 * If modify is set to false each single access tag should only be set
	 * if it is not already set.
	 */
	@Test
	public void testNoOverwriteValue() {
		Action act = new AddAccessAction("${access}", false);
		Element el = stdElement();
		el.addTag("mkgmap:bike", "yes");
		act.perform(el);
		for (String accessTag : StyledConverter.ACCESS_TAGS) {
			if ("mkgmap:bike".equals(accessTag))
				assertEquals("no overwrite", "yes", el.getTag(accessTag));
			else
				assertEquals("no overwrite", "no", el.getTag(accessTag));
		}
	}
	
	/**
	 * If modify is set to true all access tags should be set
	 * no matter if they are set before.
	 */
	@Test
	public void testOverwriteValue() {
		Action act = new AddAccessAction("${access}", true);
		Element el = stdElement();
		el.addTag("mkgmap:bike", "yes");
		act.perform(el);
		for (String accessTag : StyledConverter.ACCESS_TAGS) {
			assertEquals("no overwrite", "no", el.getTag(accessTag));
		}
	}


	/**
	 * The add/set commands now support alternatives just like the name command
	 * has always done.
	 * Several alternatives, but none match.
	 */
	@Test
	public void testNoMatchingAlternatives() {
		AddAccessAction act = new AddAccessAction("${notset}", false);
		act.add("${hello}");
		act.add("${world}");

		Element el = stdElement();
		act.perform(el);

		for (String accessTag : StyledConverter.ACCESS_TAGS) 
			assertNull(accessTag+"a not set", el.getTag(accessTag));
	}

	/**
	 * Several alternatives and the first one matches.
	 */
	@Test
	public void testFirstAlternativeMatches() {
		AddAccessAction act = new AddAccessAction("${access}", false);
		act.add("${hello}");
		act.add("${world}");

		Element el = stdElement();
		el.addTag("hello", "hello");
		act.perform(el);

		for (String accessTag : StyledConverter.ACCESS_TAGS) 
			assertEquals(accessTag+" is set", ACCESSVAL, el.getTag(accessTag));
	}

	/**
	 * Several alternatives and the second one matches.
	 */
	@Test
	public void testSecondAlternativeMatches() {
		AddAccessAction act = new AddAccessAction("${hello}", false);
		act.add("${access}");
		act.add("${world}");

		Element el = stdElement();
		el.addTag("world", "world");
		act.perform(el);

		for (String accessTag : StyledConverter.ACCESS_TAGS) 
			assertEquals(accessTag+" is set", ACCESSVAL, el.getTag(accessTag));
	}

	private Element stdElement() {
		Element el1 = new Way(1);
		el1.addTag("access", ACCESSVAL);
		el1.addTag("bicycle", "yes");
		el1.addTag("foot", "private");
		el1.addTag("highway", "track");
		return el1;
	}
}

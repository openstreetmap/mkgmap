/*
 * Copyright (C) 2014.
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

import java.util.HashSet;
import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Way;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Test substitutions when building values with ValueBuilder.
 */
public class ValueBuilderTest {

	@Test
	public void testSimpleSubst() {
		ValueBuilder vb = new ValueBuilder("init ${name|subst:abc=>xyz} final");

		Element el = new Way(1);
		el.addTag("name", "abc road abc");

		String s = vb.build(el, null);
		assertEquals("init xyz road xyz final", s);
	}

	@Test
	public void testMultiSubst() {
		ValueBuilder vb = new ValueBuilder("${name|subst:abc=>xyz|subst:def=>www|def:unset}");

		Element el = new Way(1);

		// No tags set, so default value will be applied.
		String s = vb.build(el, null);
		assertEquals("name not set, so default is applied", "unset", s);

		// Name tag is set, so substitutions are made
		el.addTag("name", "abc def");
		s = vb.build(el, null);
		assertEquals("substitutions in name", "xyz www", s);
	}

	@Test
	public void testSubstWithSpace() {
		ValueBuilder vb = new ValueBuilder("${name|subst:abc=>x y z }!");

		Element el = new Way(1);
		el.addTag("name", "Tabc");

		String s = vb.build(el, null);
		assertEquals("Tx y z !", s);
	}

	@Test
	public void testUsedTags() {
		ValueBuilder vb = new ValueBuilder("${name}");

		Element el = new Way(1);
		el.addTag("name", "fred");
		el.addTag("highway", "primary");
		vb.build(el, null);

		Set<String> exp = new HashSet<>();
		exp.add("name");
		assertEquals(exp, vb.getUsedTags());
	}
}

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
	public void testVariable() {
		ValueBuilder vb = new ValueBuilder("${name} road");

		Element el = new Way(1);
		el.addTag("name", "abc abc");

		String s = vb.build(el, null);
		assertEquals("abc abc road", s);
	}

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
	public void testQuotedArg() {
		ValueBuilder vb = new ValueBuilder("${name|subst:'abc=>x y z '}!");

		Element el = new Way(1);
		el.addTag("name", "Tabc");

		String s = vb.build(el, null);
		assertEquals("Tx y z !", s);
	}

	@Test
	public void testDQuotedArg() {
		ValueBuilder vb = new ValueBuilder("${name|subst:\"abc=>x y z \"}!");

		Element el = new Way(1);
		el.addTag("name", "Tabc");

		String s = vb.build(el, null);
		assertEquals("Tx y z !", s);
	}

	@Test
	public void testQuotedArgs() {
		ValueBuilder vb = new ValueBuilder("${name|subst:'abc=>x|y'|subst:'defg=>w|w\"w'|def:'unset string' }");

		Element el = new Way(1);

		// No tags set, so default value will be applied.
		String s = vb.build(el, null);
		assertEquals("name not set, so default is applied", "unset string", s);

		// Name tag is set, so substitutions are made
		el.addTag("name", "abc defg");
		s = vb.build(el, null);
		assertEquals("substitutions in name", "x|y w|w\"w", s);
	}

	@Test
	public void testSpacedQuotedArgs() {
		ValueBuilder vb = new ValueBuilder("${name | subst:'abc=>x|y' | subst:'defg=>w|w' | def:'unset string' }");
		Element el = new Way(1);

		// No tags set, so default value will be applied.
		String s = vb.build(el, null);
		assertEquals("name not set, so default is applied", "unset string", s);

		// Name tag is set, so substitutions are made
		el.addTag("name", "abc defg");
		s = vb.build(el, null);
		assertEquals("substitutions in name", "x|y w|w", s);
	}

	@Test
	public void testQuotedSplitLines() {
		String value =
				"${cs:phone|subst:^00~>+|subst:[-\n" +
				"()]~>|subst:^0~>+353|subst:^+3530~>+353}";
		ValueBuilder vb = new ValueBuilder(value);

		Element el = new Way(1);
		el.addTag("mkgmap:country", "IRL");
		el.addTag("cs:phone", "00(22)5554-444");

		String s = vb.build(el, null);

		assertEquals("+225554444", s);
	}

	@Test
	public void testExample() {
		ValueBuilder vb = new ValueBuilder("${name|subst:'^(Doctor|Dokter) ~>Dr '}");

		Element el = new Way(1);
		el.addTag("name", "Doctor Who");

		String s = vb.build(el, null);
		assertEquals("Dr Who", s);
	}

	@Test
	public void testEmptyArg() {
		ValueBuilder vb = new ValueBuilder("${name|def:}");

		Element el = new Way(1);

		String s = vb.build(el, null);
		assertEquals("", s);
	}

	@Test
	public void testEmptyQuotedArg() {
		ValueBuilder vb = new ValueBuilder("${name|def:''}");

		Element el = new Way(1);

		String s = vb.build(el, null);
		assertEquals("", s);
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

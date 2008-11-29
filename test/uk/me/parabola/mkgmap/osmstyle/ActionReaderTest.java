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
import java.util.List;

import uk.me.parabola.mkgmap.osmstyle.actions.Action;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.scan.TokenScanner;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ActionReaderTest {

	@Test
	public void testSimpleSet() {
		List<Action> actions = readActionsFromString("{set park=yes}");
		assertEquals("one action", 1, actions.size());

		Element el = stdElementRun(actions);

		assertEquals("park overwritten", "yes", el.getTag("park"));
	}

	@Test
	public void testSimpleAdd() {
		List<Action> actions = readActionsFromString("{add park=yes}");
		assertEquals("one action", 1, actions.size());

		Element el = stdElementRun(actions);
		assertEquals("park not overwritten", "no", el.getTag("park"));
	}

	@Test
	public void testFreeForm() {
		List<Action> actions = readActionsFromString(" { set web='world wide';" +
				"set ribbon = 'yellow' } ");

		assertEquals("number of actions", 2, actions.size());
		Element el = stdElementRun(actions);
		assertEquals("park not overwritten", "no", el.getTag("park"));
		assertEquals("word with spaces", "world wide", el.getTag("web"));
		assertEquals("yellow ribbon", "yellow", el.getTag("ribbon"));
	}

	/**
	 * Test several commands in the block.  They should all be executed.
	 */
	@Test
	public void testMultipleCommands() {
		List<Action> actions = readActionsFromString(
				"{set park=yes; add fred=other;" +
						"set pooh=bear}");

		assertEquals("number of actions", 3, actions.size());

		Element el = stdElementRun(actions);

		assertEquals("park set to yes", "yes", el.getTag("park"));
		assertEquals("fred set", "other", el.getTag("fred"));
		assertEquals("pooh set", "bear", el.getTag("pooh"));
	}

	private Element stdElementRun(List<Action> actions) {
		Rule rule = new ActionRule(null, actions);
		Element el = makeElement();
		rule.resolveType(el);
		return el;
	}

	@Test(expected = SyntaxException.class)
	public void testInvalidCommand() {
		readActionsFromString("{bad }");
	}

	/**
	 * Make a standard element for the tests.
	 */
	private Element makeElement() {
		Element el = new Way();
		el.addTag("park", "no");
		el.addTag("test", "1");
		return el;
	}

	/**
	 * Read a action list from a string.
	 */
	private List<Action> readActionsFromString(String in) {
		Reader sr = new StringReader(in);
		TokenScanner ts = new TokenScanner("string", sr);
		ActionReader ar = new ActionReader(ts);
		List<Action> actions = ar.readActions();
		return actions;
	}
}

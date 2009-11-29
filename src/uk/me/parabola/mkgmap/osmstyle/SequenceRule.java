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
 * Create date: 07-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * This holds a list of rules.  Each of them is tried out in order and
 * the first one that matches is the result of this rule.
 *
 * @author Steve Ratcliffe
 */
public class SequenceRule implements Rule, Iterable<Rule> {
	private final List<Rule> ruleList = new ArrayList<Rule>();

	public GType resolveType(Element el) {
		return resolveType(el, null);
	}

	public GType resolveType(Element el, GType pre) {
		for (Rule r : ruleList) {
			GType type = r.resolveType(el, pre);
			if (type != null)
				return type;
		}
		return null;
	}

	/**
	 * Add a rule to this sequence.  We do a quick check for impossible
	 * situations: if a FixedRule is added, then any rule added afterwards
	 * would never be called (because a fixed rule always returns an answer).
	 */
	public void add(Rule rule) {
		//if (blocked && !(rule instanceof FixedRule))
		//	System.err.println("Warning: Unreachable rule (" + rule + "), more general rules should be later in the file");
		
		ruleList.add(rule);
		//boolean blocked;
		//if (rule instanceof FixedRule)
		//	blocked = true;
	}

	public Iterator<Rule> iterator() {
		return ruleList.listIterator();
	}

	public String toString() {
		Formatter fmt = new Formatter(new StringBuilder());
		fmt.format("(");
		for (Rule r : ruleList) {
			fmt.format("%s | ", r);
		}
		fmt.format(")");
		return fmt.toString();
	}
}

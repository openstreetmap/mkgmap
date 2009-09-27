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
import java.util.Collections;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * A list of rules.
 * @author Steve Ratcliffe
 */
public class RuleList implements Rule, Iterable<RuleHolder> {
	private final List<RuleHolder> ruleList = new ArrayList<RuleHolder>();

	public void add(RuleHolder holder) {
		ruleList.add(holder);
	}

	public void add(RuleList other) {
		ruleList.addAll(other.ruleList);
	}

	public GType resolveType(Element el) {
		Collections.sort(ruleList);

		int lastPriority = Integer.MIN_VALUE;
		GType gType = null;
		for (RuleHolder rh : ruleList) {
			// As the priority value identifies the rule, throw out all
			// duplicates.
			int priority = rh.priority();
			if (lastPriority == priority)
				continue;
			lastPriority = priority;

			// Get the type
			GType t = rh.resolveType(el);
			if (t == null)
				continue;

			t.clear();
			if (gType == null) {
				gType = t;
			} else {
				gType.addType(t);
			}
			if (!t.isContinueSearch())
				break;
		}
		return gType;
	}

	public void dumpRules(Formatter fmt, String key) {
		for (RuleHolder rh : ruleList) {
			rh.format(fmt, key);
		}
	}

	public Iterator<RuleHolder> iterator() {
		return ruleList.iterator();
	}

	public String toString() {
		Formatter fmt = new Formatter(new StringBuilder());
		fmt.format("(");
		for (RuleHolder r : ruleList) {
			fmt.format("%s | ", r);
		}
		fmt.format(")");
		return fmt.toString();
	}
}

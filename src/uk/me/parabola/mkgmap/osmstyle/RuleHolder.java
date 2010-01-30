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

import java.util.Formatter;
import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;

/**
 * Holds a rule and allows a collection of them to be sorted.
 * The holder itself implements Rule so that it can be used directly
 * without having to unwrap the underlying rule.
 *
 * @author Steve Ratcliffe
 */
@Deprecated // TODO not needed now
public class RuleHolder implements Rule, Comparable<RuleHolder> {

	private static final int PRIORITY_INC = 100000;

	private static int nextPriority = 1;

	private final int priority;
	private final Rule rule;

	public RuleHolder(Rule rule) {
		this(rule, null);
	}

	public RuleHolder(Rule rule, Set<String> changeableTags) {
		this(rule, changeableTags, nextPriority());
	}

	private RuleHolder(Rule rule, Set<String> changeableTags, int priority) {
		this.rule = rule;
		this.priority = priority;
	}

	public void resolveType(Element el, TypeResult result) {
		rule.resolveType(el, result);
	}

	/**
	 * Each rule has a priority value.  Rules that are defined earlier
	 * have lower priority values.  Rules are to be evaluated in increasing
	 * priority order.
	 */
	public int compareTo(RuleHolder o) {
		if (this.priority == o.priority)
			return 0;
		else if (this.priority < o.priority)
			return -1;
		else
			return 1;
	}

	public void format(Formatter fmt, String key) {
		if (rule instanceof FixedRule)
			fmt.format("%s %s\n", key, rule);
		else {
			String rulestr = rule.toString();
			if (rulestr.startsWith("\n") || rulestr.matches("^[ \t\n].*") || rulestr.matches("^[ \t\n].*"))
				fmt.format("%s %s\n", key, rulestr);
			else
				fmt.format("%s & %s\n", key, rulestr);
		}
	}

	public String toString() {
		return rule.toString();
	}

	private static int nextPriority() {
		return nextPriority++;
	}

	public static void pushPriority() {
		nextPriority += PRIORITY_INC;
	}

	public static void popPriority() {
		nextPriority -= PRIORITY_INC;
	}
}

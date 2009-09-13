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

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * Holds a rule and allows a collection of them to be sorted.
 * The holder itself implements Rule so that it can be used directly
 * without having to unwrap the underlying rule.
 *
 * @author Steve Ratcliffe
 */
public class RuleHolder implements Rule, Comparable<RuleHolder> {

	private static int nextPriority = 1;
	private static final int PRIORITY_PUSH = 100000;

	private final int priority;

	private final Rule rule;

	public RuleHolder(Rule rule) {
		this.rule = rule;
		priority = nextPriority();
	}

	public GType resolveType(Element el) {
		return rule.resolveType(el);
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

	/**
	 * Is the priority of this type better than that of other?
	 * Lower priorities are better and win out.
	 */
	public boolean isBetterPriority(RuleHolder other) {
		return this.priority < other.priority;
	}

	private static int nextPriority() {
		return nextPriority++;
	}

	/**
	 * Increment the priority temporarily so another file can be read in
	 * that should have a higher priority.
	 * XXX find a better way if possible.
	 */
	public static void push() {
		nextPriority += PRIORITY_PUSH;
	}

	/**
	 * Decrement priority to a value the same or greater than the one that
	 * existed before the previous push.
	 */
	public static void pop() {
		nextPriority -= PRIORITY_PUSH;
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
}

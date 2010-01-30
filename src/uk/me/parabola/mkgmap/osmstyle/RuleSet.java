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
 * Create date: 08-Nov-2008
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
 * A group of rules.  Basically just a map of a tag=value strings that is used
 * as an index and the rule that applies for that tag,value pair.
 *
 * The main purpose is to separate out the code to add a new rule
 * which is moderately complex.
 *
 * @author Steve Ratcliffe
 */
public class RuleSet implements Rule, Iterable<RuleHolder> {
	private final List<RuleHolder> rules = new ArrayList<RuleHolder>();

	/**
	 * Resolve the type for this element by running the rules in order.
	 *
	 * This is a very performance critical part of the style system as parts
	 * of the code are run for every tag in the input file.
	 *
	 * @param el The element as read from an OSM xml file in 'tag' format.
	 * @return A GType describing the Garmin type of the first rule that
	 * matches.  If there is no match then null is returned.
	 */
	public GType resolveType(Element el) {
		// Start by literally running through the rules in order.
		for (RuleHolder rh : rules) {
			//System.out.println("R " + rh);
			GType type = rh.resolveType(el);
			if (type != null)
				return type;
		}
		return null;
	}

	public Iterator<RuleHolder> iterator() {
		return rules.iterator();
	}

	public void add(RuleHolder rule) {
		rules.add(rule);
	}

	/**
	 * Add all rules from the given rule set to this one.
	 * @param rs The other rule set.
	 */
	public void addAll(RuleSet rs) {
		for (RuleHolder rh : rs.rules)
			add(rh);
	}

	/**
	 * Format the rule set.  Warning: this doesn't produce a valid input
	 * rule file.
	 */
	public String toString() {
		Formatter fmt = new Formatter();
		for (RuleHolder rh : rules) {
			rh.format(fmt, "");
		}
		return fmt.toString();
	}

	public void merge(RuleSet rs) {
		List<RuleHolder> l = new ArrayList<RuleHolder>(rules);
		l.addAll(rs.rules);
		rules.clear();
		rules.addAll(l);
	}
}

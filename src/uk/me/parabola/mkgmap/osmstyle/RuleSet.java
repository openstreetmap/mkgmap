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

import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
public class RuleSet implements Rule {
	private final Map<String, Rule> rules = new LinkedHashMap<String, Rule>();

	public void add(String s, Rule rule) {
		Rule existingRule = rules.get(s);
		if (existingRule == null) {
			rules.put(s, rule);
		} else {
			if (existingRule instanceof SequenceRule) {
				((SequenceRule) existingRule).add(rule);
			} else {
				// There was already a single rule there.  Create a sequence
				// rule and add the existing and the new rule to it.
				SequenceRule sr = new SequenceRule();
				sr.add(existingRule);
				sr.add(rule);
				rules.put(s, sr);
			}
		}
	}

	public Map<String, Rule> getMap() {
		return rules;
	}

	public Set<Map.Entry<String,Rule>> entrySet() {
		return rules.entrySet();
	}

	public GType resolveType(Element el, GType pre) {
		GType foundType = null;
		for (String tagKey : el) {
			Rule rule = rules.get(tagKey);
			if (rule != null) {
				GType type = rule.resolveType(el, pre);
				if (type != null) {
					if ((foundType == null || type.isBetterPriority(foundType)) && (pre == null || pre.isBetterPriority(type))) {
						foundType = type;
					}
				}
			}
		}
		return foundType;
	}

	public void addAll(RuleSet rs) {
		for (Map.Entry<String, Rule> ent : rs.entrySet())
			add(ent.getKey(), ent.getValue());
	}

	/**
	 * Format the rule set.  Warning: this doesn't produce a valid input
	 * rule file.
	 */
	public String toString() {
		Formatter fmt = new Formatter();
		for (Map.Entry<String, Rule> ent: rules.entrySet()) {
			String first = ent.getKey();
			Rule r = ent.getValue();
			if (r instanceof FixedRule)
				fmt.format("%s %s\n", first, r);
			else
				fmt.format("%s & %s\n", first, r);
		}
		return fmt.toString();
	}
}

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
	private final Map<String, RuleList> rules = new LinkedHashMap<String, RuleList>();

	public void add(String key, Rule rule) {
		RuleList rl = rules.get(key);
		if (rl == null)
			rl = new RuleList();

		rl.add(rule);
		rules.put(key, rl);
	}

	public GType resolveType(Element el) {
		GType foundType = null;
		for (String tagKey : el) {
			Rule rule = rules.get(tagKey);
			if (rule != null) {
				GType type = rule.resolveType(el);
				if (type != null) {
					if (foundType == null /*|| type.isBetterPriority(foundType)*/) {
						foundType = type;
					}
				}
			}
		}
		return foundType;
	}

	public void addAll(RuleSet rs) {
		for (Map.Entry<String, RuleList> ent : rs.rules.entrySet())
			add(ent.getKey(), ent.getValue());
	}

	/**
	 * Format the rule set.  Warning: this doesn't produce a valid input
	 * rule file.
	 */
	public String toString() {
		Formatter fmt = new Formatter();
		for (Map.Entry<String, RuleList> ent: rules.entrySet()) {
			String first = ent.getKey();
			Rule r = ent.getValue();
			if (r instanceof FixedRule)
				fmt.format("%s %s\n", first, r);
			else
				fmt.format("%s & %s\n", first, r);
		}
		return fmt.toString();
	}

	public void merge(RuleSet lines) {
		for (Map.Entry<String, RuleList> ent : lines.rules.entrySet())
			add(ent.getKey(), ent.getValue());
	}

	public Set<Map.Entry<String, RuleList>> entrySet() {
		return rules.entrySet();
	}
	
	public Rule getRule(String key) {
		return rules.get(key);
	}
}

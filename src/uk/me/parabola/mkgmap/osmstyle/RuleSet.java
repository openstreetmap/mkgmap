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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * A group of rules.  Basically just a map of a tag=value strings that is used
 * as an index and the rule that applies for that tag,value pair.
 *
 * The main purpose is
 *
 * @author Steve Ratcliffe
 */
public class RuleSet {
	private Map<String, Rule> rules = new HashMap<String, Rule>();

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
}

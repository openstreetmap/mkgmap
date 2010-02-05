/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.osmstyle;

import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * Holds the keystring, rule and tags that can be changed by the rule.
 * @author Steve Ratcliffe
 */
class RuleDetails {
	private final String keystring;
	private final Rule rule;
	private final Set<String> changingTags;

	RuleDetails(String keystring, Rule rule, Set<String> changingTags) {
		this.keystring = keystring;
		this.rule = rule;
		this.changingTags = changingTags;
	}

	public String getKeystring() {
		return keystring;
	}

	public Rule getRule() {
		return rule;
	}

	public Set<String> getChangingTags() {
		return changingTags;
	}
}

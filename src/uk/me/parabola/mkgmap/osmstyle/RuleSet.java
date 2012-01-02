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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;
import uk.me.parabola.mkgmap.reader.osm.WatchableTypeResult;

/**
 * A list of rules and the logic to select the correct one.
 *
 * A separate {@link RuleIndex} class is used to speed access to the rule list.
 *
 * @author Steve Ratcliffe
 */
public class RuleSet implements Rule, Iterable<Rule> {
	private Rule[] rules;

	private RuleIndex index = new RuleIndex();
	private final Set<String> usedTags = new HashSet<String>();

	/**
	 * Resolve the type for this element by running the rules in order.
	 *
	 * This is a very performance critical part of the style system as parts
	 * of the code are run for every tag in the input file.
	 *
	 * @param el The element as read from an OSM xml file in 'tag' format.
	 * @param result A GType describing the Garmin type of the first rule that
	 * matches is returned here.  If continue types are used then more than
	 * one type may be saved here.  If there are no matches then nothing will
	 * be saved.
	 */
	public void resolveType(Element el, TypeResult result) {
		WatchableTypeResult a = new WatchableTypeResult(result);

		// Get all the rules that could match from the index.  
		BitSet candidates = new BitSet();
		for (String tag : el) {
			BitSet rules = index.getRulesForTag(tag);
			if (rules != null)
				candidates.or(rules);
		}

		for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {			
			a.reset();
			rules[i].resolveType(el, a);
			if (a.isResolved())
				return;
		}
	}

	public Iterator<Rule> iterator() {
		if (rules == null)
			prepare();
		return Arrays.asList(rules).iterator();
	}

	/**
	 * Add a rule to this rule set.
	 * @param keystring The string form of the first term of the rule.  It will
	 * be A=B or A=*.  (In the future we may allow other forms).
	 * @param rule The actual rule.
	 * @param changeableTags The tags that may be changed by the rule.  This
	 * will be either a plain tag name A, or with a value A=B.
	 */
	public void add(String keystring, Rule rule, Set<String> changeableTags) {
		index.addRuleToIndex(new RuleDetails(keystring, rule, changeableTags));
	}

	/**
	 * Add all rules from the given rule set to this one.
	 * @param rs The other rule set.
	 */
	public void addAll(RuleSet rs) {
		for (RuleDetails rd : rs.index.getRuleDetails())
			add(rd.getKeystring(), rd.getRule(), rd.getChangingTags());
	}

	/**
	 * Format the rule set.  Warning: this doesn't produce a valid input
	 * rule file.
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Rule rule : rules) {
			sb.append(rule.toString());
		}
		return sb.toString();
	}

	/**
	 * Merge the two rulesets together so that they appear to be one.
	 * @param rs The other rule set, it will have lower priority, that is the
	 * rules will be tried after the rules of this ruleset.
	 */
	public void merge(RuleSet rs) {
		// We have to basically rebuild the index and reset the rule list.
		RuleIndex newIndex = new RuleIndex();

		for (RuleDetails rd : index.getRuleDetails())
			newIndex.addRuleToIndex(rd);

		for (RuleDetails rd : rs.index.getRuleDetails())
			newIndex.addRuleToIndex(rd);

		index = newIndex;
		rules = newIndex.getRules();
		//System.out.println("Merging used tags: "
		//		   + getUsedTags().toString()
		//		   + " + "
		//		   + rs.getUsedTags());
		addUsedTags(rs.usedTags);
		//System.out.println("Result: " + getUsedTags().toString());
	}

	/**
	 * Prepare this rule set for use.  The index is built and and the rules
	 * are saved to an array for fast access.
	 */
	public void prepare() {
		index.prepare();
		rules = index.getRules();
	}

	public Set<String> getUsedTags() {
		return usedTags;
	}

	public void addUsedTags(Collection<String> usedTags) {
		this.usedTags.addAll(usedTags);
	}
}

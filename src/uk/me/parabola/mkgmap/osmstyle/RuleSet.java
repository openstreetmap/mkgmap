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

import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.EqualsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExistsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;
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


	private boolean initRun;

	public void add(String key, RuleHolder rule) {
		RuleList rl = rules.get(key);
		if (rl == null) {
			rl = new RuleList();
			rules.put(key, rl);
		}

		rl.add(rule);
	}

	/**
	 * Initialise this rule set before it gets used.  What we do is find all
	 * the rules that might be needed as a result of tags being set during the
	 * execution of other rules.
	 */
	public void init() {
		for (RuleList rl : rules.values()) {
			RuleList ruleList = new RuleList();
			for (RuleHolder rh : rl) {
				if (rh.getChangeableTags() != null)
					initExtraRules(rh, ruleList);
			}

			rl.add(ruleList);
		}

		initRun = true;
	}

	/**
	 * Initialise the extra rules that could be needed for a given rule.
	 * Normally we search for rules by looking up the tags that are on an
	 * element (to save having to really look through each rule).  However
	 * if an action causes a tag to be set, we might miss a rule that
	 * would match the newly set tag.
	 *
	 * So we have to search for all rules that could match a newly set tag.
	 * These get added to the rule list.
	 *
	 * @param rule An individual rule.
	 * @param extraRules This is a output parameter, new rules are saved here
	 * by this method.  All rules that might be required due to actions
	 * that set a tag.
	 */
	private void initExtraRules(RuleHolder rule, RuleList extraRules) {
		Set<String> tags = rule.getChangeableTags();
		Set<String> moreTags = new HashSet<String>();

		do {
			for (String t : tags) {
				String match = t + '=';
				for (Map.Entry<String, RuleList> ent : rules.entrySet()) {
					if (ent.getKey().startsWith(match)) {
						String exprstr = ent.getKey();
						RuleList other = ent.getValue();

						// As we are going to run this rule no matter what
						// tags were present, we need to add back the condition
						// that was represented by the key in the rule map.
						Op op = createExpression(exprstr);

						for (RuleHolder rh : other) {
							// There may be even more changeable tags
							// so add them here.
							Set<String> changeableTags = rh.getChangeableTags();
							if (changeableTags == null)
								continue;
							moreTags.addAll(changeableTags);

							// Re-construct the rule and add it to the output list
							Rule r = new ExpressionSubRule(op, rh);
							extraRules.add(rh.createCopy(r));
						}
					}
				}
			}

			// Remove the tags we already know about, and run through the others.
			moreTags.removeAll(tags);
			tags = Collections.unmodifiableSet(new HashSet<String>(moreTags));

		} while (!tags.isEmpty());
	}

	/**
	 * Create an expression from a plain string.
	 * @param expr The string containing an expression.
	 * @return The compiled form of the expression.
	 */
	private Op createExpression(String expr) {
		String[] keyVal = expr.split("=", 2);
		Op op;
		if (keyVal[1].equals("*")) {
			op = new ExistsOp();
			op.setFirst(new ValueOp(keyVal[0]));
		} else {
			op = new EqualsOp();
			op.setFirst(new ValueOp(keyVal[0]));
			((BinaryOp) op).setSecond(new ValueOp(keyVal[1]));
		}
		return op;
	}

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
		assert initRun || rules.isEmpty();
		RuleList combined = new RuleList();
		for (String tagKey : el) {
			RuleList rl = rules.get(tagKey);
			if (rl != null)
				combined.add(rl);
		}

		return combined.resolveType(el);
	}

	/**
	 * Add all rules from the given rule set to this one.
	 * @param rs The other rule set.
	 */
	public void addAll(RuleSet rs) {
		rules.putAll(rs.rules);
	}

	/**
	 * Format the rule set.  Warning: this doesn't produce a valid input
	 * rule file.
	 */
	public String toString() {
		Formatter fmt = new Formatter();
		for (Map.Entry<String, RuleList> ent: rules.entrySet()) {
			String first = ent.getKey();
			RuleList r = ent.getValue();
			r.dumpRules(fmt, first);
		}
		return fmt.toString();
	}

	public Set<Map.Entry<String, RuleList>> entrySet() {
		return rules.entrySet();
	}
	
	public Rule getRule(String key) {
		return rules.get(key);
	}

	public void merge(RuleSet rs) {
		for (Map.Entry<String, RuleList> ent : rs.rules.entrySet()) {
			String key = ent.getKey();
			RuleList otherList = ent.getValue();

			// get our list for this key and merge the lists.
			// if we don't already have a list then just add it.
			RuleList ourList = rules.get(key);
			if (ourList == null) {
				rules.put(key, otherList);
			} else {
				for (RuleHolder rh : otherList) {
					ourList.add(rh);
				}
			}
		}
	}
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * An index to reduce the number of rules that have to be executed.
 *
 * <p>Only the first term (after rearrangement) of the rule is used in the
 * index.  This will (currently) always be an EQUALS or EXISTS (A=B or A=*).
 *
 * <p>We look at the tags of the element and pick out all rules that have the
 * first term that matches the tag=value and tag=*, this is done by a single
 * lookup for each tag in the element (on average a low number such as 3).
 *
 * <p>So if the element had the one tag highway=primary and the rules were as
 * follows:
 *
 * <pre>
 * 1  surface=good { ... }
 * 2  highway=secondary { set fence=no; }
 * 3  highway=primary { set surface=good; }
 * 4  highway=* & abc=yes { }
 * 5  surface=good { }
 * 6  oneway=yes & highway=primary { }
 * </pre>
 *
 * We would select rules 3 and 4.  No other rule can match initially. But there
 * is a further issue; if rule 3 matched it could set the surface tag.  So we also
 * need to select rule 5.  Rule 1 can not be matched because it occurs before
 * the rule that sets the tag, so it is not included.  All this is precomputed
 * when the index is created, so we can still do a single lookup.
 *
 * <p>So the full set of rules that we need to match is 3, 4 and 5.
 * If rule 5 itself sets a tag, then we might have to add more rules and
 * so on.
 *
 * @author Steve Ratcliffe
 */
public class RuleIndex {
	private final List<RuleDetails> ruleDetails = new ArrayList<RuleDetails>();

	// This is an index of all rules that start with EQUALS (A=B)
	private final Map<String, Set<Integer>> existKeys = new HashMap<String, Set<Integer>>();
	// This is an index of all rules that start with EXISTS (A=*)
	private final Map<String, Set<Integer>> tagVals = new HashMap<String, Set<Integer>>();
	// This is an index of all rules by the tag name (A).
	private final Map<String, Set<Integer>> tagnames = new HashMap<String, Set<Integer>>();

	// Maps a rule number to the tags that might be changed by that rule
	private final Map<Integer, List<String>> changeTags = new HashMap<Integer, List<String>>();

	private boolean inited;

	/**
	 * Save the rule and maintains several lists related to it from the other
	 * information that is supplied.
	 * @param rd Contains 1) the key string which is the key into the index.
	 * 2) the rule itself. 3) a list of the tags that might be changed by
	 * this rule, should it be matched.
	 */
	public void addRuleToIndex(RuleDetails rd) {
		assert !inited;
		int ruleNumber = ruleDetails.size();
		String keystring = rd.getKeystring();
		Set<String> changeableTags = rd.getChangingTags();

		if (keystring.endsWith("=*")) {
			String key = keystring.substring(0, keystring.length() - 2);
			addExists(key, ruleNumber);
			addUnknowns(key, ruleNumber);
		} else {
			addKeyVal(keystring, ruleNumber);
			int ind = keystring.indexOf('=');
			if (ind >= 0) {
				String key = keystring.substring(0, ind);
				addUnknowns(key, ruleNumber);
			}
		}

		addChangables(changeableTags, ruleNumber);
		ruleDetails.add(rd);
	}

	/**
	 * Get all the rules that have been added.  This is used in the RuleSet
	 * for looking up by number.
	 * @return The rules as an array for quick lookup.
	 */
	public Rule[] getRules() {
		int len = ruleDetails.size();
		Rule[] rules = new Rule[len];
		for (int i = 0, ruleDetailsSize = ruleDetails.size(); i < ruleDetailsSize; i++) {
			RuleDetails rd = ruleDetails.get(i);
			rules[i] = rd.getRule();
		}
		return rules;
	}

	/**
	 * Get a list of rules that might be matched by this tag.
	 * @param tagval The tag and its value eg highway=primary.
	 * @return A list of rules, it may contain duplicates.  It appears to be
	 * faster just to skip duplicates, rather than using sets to ensure that
	 * there are none for example.
	 * If there are no rules then null will be returned.
	 */
	public List<Integer> getRulesForTag(String tagval) {
		List<Integer> set = null;
		Set<Integer> integers = tagVals.get(tagval);
		if (integers != null) {
			set = new ArrayList<Integer>();
			set.addAll(integers);
		}

		// Need to also look up all rules that might match highway=*
		int i = tagval.indexOf('=');
		String s2 = tagval.substring(0, i);
		Set<Integer> integerSet = existKeys.get(s2);
		if (integerSet != null) {
			if (set == null)
				set = new ArrayList<Integer>();
			set.addAll(integerSet);
		}

		return set;
	}

	/**
	 * Prepare the index for use.  This involves merging in all the possible
	 * rules that could be run as a result of actions changing tags.
	 */
	public void prepare() {
		for (Map.Entry<Integer, List<String>> ent : changeTags.entrySet()) {
			int ruleNumber = ent.getKey();
			List<String> changeTagList = ent.getValue();

			// When we add new rules, we may, in turn get more changeable tags
			// which will force us to run again to find more rules that could
			// be executed.  So save rules that we find here.
			Set<String> newChanged = new HashSet<String>(changeTagList);
			// we have to find all rules that might be now matched
			do {
				for (String s : new HashSet<String>(newChanged)) {
					Set<Integer> set;

					// If we know the value that could be set, then we can restrict to
					// rules that would match that value.  Otherwise we look for any
					// rule using the tag, no matter what the value.
					if (s.indexOf('=') >= 0) {
						set = tagVals.get(s);
					} else {
						set = tagnames.get(s);
					}

					if (set != null && !set.isEmpty()) {
						set = new HashSet<Integer>(set);
						for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();) {
							Integer i = iterator.next();
							// Only rules after this one can be affected
							if (i > ruleNumber) {
								newChanged.addAll(ruleDetails.get(i).getChangingTags());
							} else {
								iterator.remove();
							}
						}

						// Find every rule number set that contains the rule number that we
						// are examining and add all the newly found rules to each such set.
						for (Map<String, Set<Integer>> m : Arrays.asList(existKeys, tagVals, tagnames)) {
							Collection<Set<Integer>> intSets = m.values();
							for (Set<Integer> si : intSets) {
								if (si.contains(ruleNumber)) {
									// contains the rule that we are looking at so we must
									// also add the rules in the set we found.
									si.addAll(set);
								}
							}
						}
					}
				}

				newChanged.removeAll(changeTagList);
				changeTagList.addAll(newChanged);
			} while (!newChanged.isEmpty());
		}

		inited = true;
	}

	private void addExists(String keystring, int ruleNumber) {
		addNumberToMap(existKeys, keystring, ruleNumber);
	}

	private void addKeyVal(String keystring, int ruleNumber) {
		addNumberToMap(tagVals, keystring, ruleNumber);

	}

	private void addUnknowns(String keystring, int ruleNumber) {
		addNumberToMap(tagnames, keystring, ruleNumber);
	}

	private void addNumberToMap(Map<String, Set<Integer>> map, String key, int ruleNumber) {
		Set<Integer> set = map.get(key);
		if (set == null) {
			set = new TreeSet<Integer>();
			map.put(key, set);
		}
		set.add(ruleNumber);
	}

	/**
	 * For each rule number, we maintain a list of tags that might be
	 * changed by that rule.
	 * @param changeableTags The tags that might be changed if the rule is
	 * matched.
	 * @param ruleNumber The rule number.
	 */
	private void addChangables(Set<String> changeableTags, int ruleNumber) {
		List<String> tags = changeTags.get(ruleNumber);
		if (tags == null) {
			tags = new ArrayList<String>();
			changeTags.put(ruleNumber, tags);
		}
		tags.addAll(changeableTags);
	}

	public List<RuleDetails> getRuleDetails() {
		return ruleDetails;
	}
}

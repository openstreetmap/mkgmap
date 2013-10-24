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
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final Map<String, BitSet> existKeys = new HashMap<String, BitSet>();
	// This is an index of all rules that start with EXISTS (A=*)
	private final Map<String, BitSet> tagVals = new HashMap<String, BitSet>();
	// This is an index of all rules by the tag name (A).
	private final Map<String, BitSet> tagnames = new HashMap<String, BitSet>();

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
	 * @return A BitSet of rules numbers.
	 * If there are no rules then null will be returned.
	 */
	public BitSet getRulesForTag(String tagval) {
		BitSet set = tagVals.get(tagval);

		// Need to also look up all rules that might match highway=*
		int i = tagval.indexOf('=');
		String s2 = tagval.substring(0, i);
		BitSet set2 = existKeys.get(s2);
		
		BitSet res = new BitSet();
		if (set != null)
			res.or(set);
		if (set2 != null) 
			res.or(set2);
		return res;
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
				for (String s : new ArrayList<String>(newChanged)) {
					BitSet set;

					// If we know the value that could be set, then we can restrict to
					// rules that would match that value.  Otherwise we look for any
					// rule using the tag, no matter what the value.
					int ind = s.indexOf('=');
					if (ind >= 0) {
						set = tagVals.get(s);

						// Exists rules can also be triggered, so add them too.
						String key = s.substring(0, ind);
						BitSet set1 = existKeys.get(key);

						if (set == null)
							set = set1;
						else if (set1 != null)
							set.or(set1);

					} else {
						set = tagnames.get(s);
					}

					if (set != null && !set.isEmpty()) {
						// create copy that can be safely modified
						BitSet tmp  = new BitSet();
						tmp.or(set);
						set = tmp;
						
						for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
							// Only rules after this one can be affected
							if (i > ruleNumber) {
								newChanged.addAll(ruleDetails.get(i).getChangingTags());
							} else {
								set.clear(i);
							}
						}

						// Find every rule number set that contains the rule number that we
						// are examining and add all the newly found rules to each such set.
						for (Map<String, BitSet> m : Arrays.asList(existKeys, tagVals, tagnames)) {
							Collection<BitSet> bitSets = m.values();
							for (BitSet bi : bitSets) {
								if (bi.get(ruleNumber)) {
									// contains the rule that we are looking at so we must
									// also add the rules in the set we found.
									bi.or(set);
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

	private void addNumberToMap(Map<String, BitSet> map, String key, int ruleNumber) {
		BitSet set = map.get(key);
		if (set == null) {
			set = new BitSet();
			map.put(key, set);
		}
		set.set(ruleNumber);
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

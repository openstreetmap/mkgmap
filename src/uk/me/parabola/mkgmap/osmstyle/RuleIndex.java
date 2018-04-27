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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
import uk.me.parabola.util.MultiHashMap;

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

	private final Map<Short, TagHelper> tagKeyMap = new HashMap<>();
	private TagHelper[] tagKeyArray = null;

	private boolean inited;

	private class TagHelper{
		// This is an index of all rules that start with EXISTS (A=*) or (A=B)
		final BitSet checked;
		// This is an index of all rules that start with EQUALS (A=B) 
		Map<String, BitSet> tagVals;
		
		public TagHelper(BitSet checked){
			this.checked = checked;
		}

		public void addTag(String val, BitSet value) {
			if (tagVals == null)
				tagVals = new HashMap<>();
			if (checked != null){	
				BitSet merged = new BitSet();
				merged.or(checked);
				merged.or(value);
				tagVals.put(val, merged);
			} else
				tagVals.put(val, value);
		}

		public BitSet getBitSet(String tagVal) {
			if (tagVals != null){
				BitSet set = tagVals.get(tagVal);
				if (set != null){
					return (BitSet) set.clone();
				}
			} 
			if (checked != null)
				return (BitSet) checked.clone();
			return new BitSet();
		}
	}
	
	/**
	 * Save the rule and maintains several lists related to it from the other
	 * information that is supplied.
	 * @param rd Contains 1) the key string which is the key into the index.
	 * 2) the rule itself. 3) a list of the tags that might be changed by
	 * this rule, should it be matched.
	 */
	public void addRuleToIndex(RuleDetails rd) {
		assert !inited;
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
	public BitSet getRulesForTag(short tagKey, String tagVal) {
		TagHelper th;
		if (tagKeyArray != null){
			assert tagKey > 0;
			if (tagKey < tagKeyArray.length){
				th = tagKeyArray[tagKey];
			} else 
				th = null;
		} else {
			th = tagKeyMap.get(tagKey);
		}
		if (th == null)
			return new BitSet();
		return th.getBitSet(tagVal);
	}

	
	/**
	 * Prepare the index for use.  This involves merging in all the possible
	 * rules that could be run as a result of actions changing tags.
	 */
	public void prepare() {
		if (inited)
			return;
		// This is an index of all rules that start with EQUALS (A=B)
		Map<String, BitSet> tagVals = new HashMap<String, BitSet>();
		
		// This is an index of all rules by the tag name (A).
		Map<String, BitSet> tagnames = new HashMap<String, BitSet>();

		// Maps a rule number to the tags that might be changed by that rule
		Map<Integer, List<String>> changeTags = new HashMap<Integer, List<String>>();
		
		// Collection of possibly changed or added tags were the new value is
		// NOT known (e.g. set x=$y)
		Set<String> modOrAddedTagKeys = new HashSet<>();

		// Collection of possibly changed or added tags were the new value is
		// known (e.g. set x=1)
		MultiHashMap<String, String> modOrAddedTags = new MultiHashMap<>();

		// remove unnecessary rules
		filterRules();

		for (int i = 0; i < ruleDetails.size(); i++) {
			int ruleNumber = i;
			RuleDetails rd = ruleDetails.get(i);
			String keystring = rd.getKeystring();
			Set<String> changeableTags = rd.getChangingTags();

			if (keystring.endsWith("=*")) {
				String key = keystring.substring(0, keystring.length() - 2);
				addNumberToMap(tagnames, key, ruleNumber);
			} else {
				addNumberToMap(tagVals, keystring, ruleNumber);
				// check if any of the previous rules may have changed the tag
				int ind = keystring.indexOf('=');
				assert ind >= 0 : "rule index: error in keystring " + keystring; 
				String key = keystring.substring(0, ind);
				boolean needed = (modOrAddedTagKeys.contains(key));
				if (!needed) {
					List<String> values = modOrAddedTags.get(key);
					if (values.contains(keystring.substring(ind + 1)))
						needed = true;
				}
				if (needed) {
					addNumberToMap(tagnames, key, ruleNumber);
				}
			}
			addChangables(changeTags, changeableTags, ruleNumber);
			for (String ent : changeableTags) {
				int ind = ent.indexOf('=');
				if (ind >= 0) {
					modOrAddedTags.add(ent.substring(0, ind), ent.substring(ind + 1));
				} else {
					modOrAddedTagKeys.add(ent);
				}
			}
		}
		
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
						String key = s.substring(0, ind);
						set = tagnames.get(key);
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
						for (Map<String, BitSet> m : Arrays.asList(tagVals, tagnames)) {
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

		// compress the index: create one hash map with one entry for each key
		for (Map.Entry<String, BitSet> entry  : tagnames.entrySet()){
			Short skey = TagDict.getInstance().xlate(entry.getKey());
			tagKeyMap.put(skey, new TagHelper(entry.getValue()));
		}
		for (Map.Entry<String, BitSet> entry  : tagVals.entrySet()){
			String keyString = entry.getKey();
			int ind = keyString.indexOf('=');
			if (ind >= 0) {
				short key = TagDict.getInstance().xlate(keyString.substring(0, ind));
				String val = keyString.substring(ind+1);
				TagHelper th = tagKeyMap.get(key);
				if (th == null){
					th = new TagHelper(null);
					tagKeyMap.put(key, th);
				} 
				th.addTag(val, entry.getValue());
			}
		}
		Optional<Short> minKey = tagKeyMap.keySet().stream().min(Short::compare);
		if (minKey.isPresent() && minKey.get() > 0){
			Optional<Short> maxKey = tagKeyMap.keySet().stream().max(Short::compare);
			tagKeyArray = new TagHelper[maxKey.get() + 1];
			for (Map.Entry<Short, TagHelper> entry  : tagKeyMap.entrySet()){
				tagKeyArray[entry.getKey()] = entry.getValue();
			}
			tagKeyMap.clear();
		}
			
		inited = true;
	}

	/**
	 * Remove dead rules.
	 * @param styleOptionTags
	 */
	private void filterRules() {
		List<RuleDetails> filteredRules = new ArrayList<>(ruleDetails);
		Set<String> usedIfVars = new HashSet<>();
		for (RuleDetails rd : filteredRules) {
			findIfVarUsage(rd.getRule(), usedIfVars);
		}
		removeUnused(filteredRules, usedIfVars);
		ruleDetails.clear();
		ruleDetails.addAll(filteredRules);
	}

	private void removeUnused(List<RuleDetails> filteredRules, Set<String> usedIfVars) {
		if (usedIfVars.isEmpty())
			return;
		Iterator<RuleDetails> iter = filteredRules.iterator();
		while (iter.hasNext()) {
			RuleDetails rd = iter.next();
			if (rd.getRule() instanceof ActionRule) {
				ActionRule ar = (ActionRule) rd.getRule();
				if (ar.toString().contains("set " + RuleFileReader.IF_PREFIX)) {
					boolean needed = false;
					for (String ifVars : usedIfVars) {
						if (ar.toString().contains("set " + ifVars)) {
							needed = true;
						}
					}
					if (!needed)
						iter.remove();
				}
			}
		}
	}

	private void findIfVarUsage(Rule rule, Set<String> usedIfVars) {
		if (rule == null)
			return;
//		if (rule.getFinalizeRule() != null)
//			findIfVarUsage(rule.getFinalizeRule(), usedIfVars);
		Op expr = null;
		if (rule instanceof ExpressionRule) 
			expr = ((ExpressionRule) rule).getOp();
		else if (rule instanceof ActionRule)
			expr = ((ActionRule) rule).getOp();
		if (expr == null)
			return;
		for (String usedTag : expr.getEvaluatedTagKeys()) {
			if (usedTag.startsWith(RuleFileReader.IF_PREFIX))
				usedIfVars.add(usedTag);
		}
	}

	private static void addNumberToMap(Map<String, BitSet> map, String key, int ruleNumber) {
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
	 * @param changeTags 
	 * @param changeableTags The tags that might be changed if the rule is
	 * matched.
	 * @param ruleNumber The rule number.
	 */
	private static void addChangables(Map<Integer, List<String>> changeTags, Set<String> changeableTags, int ruleNumber) {
		if(changeableTags.isEmpty())
			return;
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

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;
import uk.me.parabola.mkgmap.reader.osm.WatchableTypeResult;

/**
 * A group of rules.  Basically just a map of a tag=value strings that is used
 * as an index and the rule that applies for that tag,value pair.
 *
 * The main purpose is to separate out the code to add a new rule
 * which is moderately complex.
 *
 * @author Steve Ratcliffe
 */
public class RuleSet implements Rule, Iterable<Rule> {
	private Rule[] rules;

	public static int elements;
	public static int rulesApplied;
	public static int rulesMatched;

	//private final Map<String, String> index = new HashMap<String, String>();

	private Index index = new Index();

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

		List<Integer> cand = new ArrayList<Integer>();
		for (String s : el) {
			List<Integer> integerSet = index.getRulesForTag(s);
			cand.addAll(integerSet);
			//System.out.println("iarr " + s + " -> " + Arrays.toString(iarr));
		}
		Collections.sort(cand);

		elements++;

		int lastNum = -1;
		for (Integer i : cand) {
			if (i == lastNum)
					continue;
			lastNum = i;
			//System.out.println("R " + rh);
			rulesApplied++;
			a.reset();
			rules[i].resolveType(el, a);
			if (a.isActionsOnly() || a.isResolved())
				rulesMatched++;
			if (a.isResolved())
				return;
		}
	}

	public Iterator<Rule> iterator() {
		if (rules == null)
			prepare();
		return Arrays.asList(rules).iterator();
	}

	public void add(String keystring, Rule rule, Set<String> changeableTags) {
		RuleDetails rd = new RuleDetails(keystring, rule, changeableTags);
		index.indexEntry(rd);
	}

	/**
	 * Add all rules from the given rule set to this one.
	 * @param rs The other rule set.
	 */
	public void addAll(RuleSet rs) {
		for (RuleDetails rule : rs.index.ruleDetails)
			add(rule.keystring, rule.rule, rule.changingTags);
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
		Index newIndex = new Index();

		for (RuleDetails rd : index.ruleDetails)
			newIndex.indexEntry(rd);

		for (RuleDetails rd : rs.index.ruleDetails)
			newIndex.indexEntry(rd);

		index = newIndex;
		rules = newIndex.getRules();
	}

	public void prepare() {
		index.initIndex();
		rules = index.getRules();
	}

	class Index {
		private final List<RuleDetails> ruleDetails = new ArrayList<RuleDetails>();

		// This is an index of all rules that start with A=B
		private final Map<String, Set<Integer>> existKeys = new HashMap<String, Set<Integer>>();
		// This is an index of all rules that start with A=*
		private final Map<String, Set<Integer>> keyVals = new HashMap<String, Set<Integer>>();
		// This is an index of all rules that start with A.
		private final Map<String, Set<Integer>> unknowns = new HashMap<String, Set<Integer>>();

		// Maps a rule number to the tags that might be changed by that rule
		private final Map<Integer, List<String>> changeTags = new HashMap<Integer, List<String>>();

		private boolean inited;

		public void indexEntry(RuleDetails rd) {
			assert !inited;
			int ruleNumber = ruleDetails.size();
			String keystring = rd.keystring;
			Set<String> changeableTags = rd.changingTags;

			if (keystring.endsWith("=*")) {
				String key = keystring.substring(0, keystring.length() - 2);
				index.addExists(key, ruleNumber);
				index.addUnknowns(key, ruleNumber);
			} else {
				index.addKeyVal(keystring, ruleNumber);
				int ind = keystring.indexOf('=');
				if (ind >= 0) {
					String key = keystring.substring(0, ind);
					index.addUnknowns(key, ruleNumber);
				}
			}

			index.addChangables(changeableTags, ruleNumber);
			ruleDetails.add(rd);
		}

		public Rule[] getRules() {
			int len = ruleDetails.size();
			Rule[] rules = new Rule[len];
			for (int i = 0, ruleDetailsSize = ruleDetails.size(); i < ruleDetailsSize; i++) {
				RuleDetails rd = ruleDetails.get(i);
				rules[i] = rd.rule;
			}
			return rules;
		}

		public List<Integer> getRulesForTag(String s) {
			List<Integer> set = new ArrayList<Integer>();
			Set<Integer> integers = keyVals.get(s);
			if (integers != null)
				set.addAll(integers);

			int i = s.indexOf('=');
			String s2 = s.substring(0, i);
			Set<Integer> integerSet = existKeys.get(s2);
			if (integerSet != null)
				set.addAll(integerSet);

			return set;
		}

		public void initIndex() {
			long start = System.currentTimeMillis();
			for (Map.Entry<Integer, List<String>> ent : changeTags.entrySet()) {
				int rn = ent.getKey();
				List<String> sl = ent.getValue();

				Set<String> newChanged = new HashSet<String>(sl);
				// we have to find all rules that might be now matched
				do {
					for (String s : sl) {
						Set<Integer> set;
						if (s.indexOf('=') >= 0) {
							set = keyVals.get(s);
						} else {
							set = unknowns.get(s);
						}

						if (set != null && !set.isEmpty()) {
							for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();) {
								Integer i = iterator.next();
								// Only rules after this one can be affected
								if (i > rn) {
									newChanged.addAll(ruleDetails.get(i).changingTags);
								} else {
									iterator.remove();
								}
							}


							for (Map<String, Set<Integer>> m : Arrays.asList(existKeys, keyVals, unknowns)) {
								Collection<Set<Integer>> intSets = m.values();
								for (Set<Integer> si : intSets) {
									if (si.contains(rn)) {
										// contains the rule that we are looking at so we must
										// also add the rules in the set we found.
										//System.out.println("adding " + set + " to " + si);
										si.addAll(set);
									}
								}
							}
						}
					}

					newChanged.removeAll(sl);
					//System.out.println("new changed " + newChanged);
					sl = new ArrayList<String>(newChanged);
				} while (!sl.isEmpty());
			}

			System.out.println("rule set prep time " + (System.currentTimeMillis() - start) + "ms");
			inited = true;			
		}

		private void addExists(String keystring, int ruleNumber) {
			addNumberToMap(existKeys, keystring, ruleNumber);
		}

		private void addKeyVal(String keystring, int ruleNumber) {
			addNumberToMap(keyVals, keystring, ruleNumber);

		}

		private void addUnknowns(String keystring, int ruleNumber) {
			addNumberToMap(unknowns, keystring, ruleNumber);
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
	}

	class RuleDetails {
		private final String keystring;
		private final Rule rule;
		private final Set<String> changingTags;

		RuleDetails(String keystring, Rule rule, Set<String> changingTags) {
			this.keystring = keystring;
			this.rule = rule;
			this.changingTags = changingTags;
		}
	}
}

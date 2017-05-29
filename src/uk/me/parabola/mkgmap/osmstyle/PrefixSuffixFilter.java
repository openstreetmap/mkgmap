/*
 * Copyright (C) 2017.
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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.imgfmt.app.mdr.Mdr7;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Code to add special Garmin separators 0x1b, 0x1e and 0x1f. 
 * The separator 0x1e tells Garmin that the part of the name before that separator
 * should not be displayed when zooming out enough. It is displayed like a blank. 
 * The separator 0x1f tells Garmin that the part of the name after that separator 
 * should not be displayed when zooming out enough. It is displayed like a blank. 
 * The separator 0x1b works like 0x1e, but is not displayed at all.
 * The separator 0x1c works like 0x1f, but is not displayed at all.
 * See also class {@link Mdr7}. 
 * 
 * @author Gerd Petermann
 *
 */
public class PrefixSuffixFilter {
	private static final Logger log = Logger.getLogger(PrefixSuffixFilter.class);

	private static final int MODE_PREFIX = 0;
	private static final int MODE_SUFFIX = 1;
	
	private boolean enabled;
	private final Set<String> languages = new LinkedHashSet<>();
	private final Map<String, List<String>> langPrefixMap = new HashMap<>();
	private final Map<String, List<String>> langSuffixMap = new HashMap<>();
	private final Map<String, List<String>> countryLanguageMap = new HashMap<>();
	private final Map<String, List<String>> countryPrefixMap = new HashMap<>();
	private final Map<String, List<String>> countrySuffixMap = new HashMap<>();

	private EnhancedProperties options = new EnhancedProperties();

	public PrefixSuffixFilter(EnhancedProperties props) {
		String cfgFile = props.getProperty("road-name-config",null);
		enabled = readConfig(cfgFile);
	}

	/**
	 * Read the configuration file for this filter.
	 * @param cfgFile path to file
	 * @return true if filter can be used, else false.
	 */
	private boolean readConfig(String cfgFile) {
		if (cfgFile == null) 
			return false;
		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(cfgFile), "utf-8")) {
			readOptionFile(reader, cfgFile);
			return true;
		} catch (Exception e) {
			log.error(e.getMessage());
			log.error(this.getClass().getSimpleName() + " disabled, failed to read config file " + cfgFile);
			return false;
		}
	}
	
	/**
	 * 
	 * @param r
	 * @param filename
	 */
	private void readOptionFile(Reader r, String filename) {
		BufferedReader br = new BufferedReader(r);
		TokenScanner ts = new TokenScanner(filename, br);
		ts.setExtraWordChars(":");

		while (!ts.isEndOfFile()) {
			Token tok = ts.nextToken();
			if (tok.isValue("#")) {
				ts.skipLine();
				continue;
			}

			String key = tok.getValue();

			ts.skipSpace();
			tok = ts.peekToken();
			
			if (tok.getType() == TokType.SYMBOL) {

				String punc = ts.nextValue();
				String val;
				if (punc.equals(":") || punc.equals("=")) {
					val = ts.readLine();
				} else {
					ts.skipLine();
					continue;
				}
				processOption(key, val);
			} else if (key != null){
				throw new IllegalArgumentException("don't understand line with " + key );
			} else {
				ts.skipLine();
			}
		}
		/**
		 * process lines starting with prefix1 or prefix2. 
		 */
		for (String lang : languages) {
			String prefix1 = options.getProperty("prefix1:" + lang, null);
			if (prefix1 == null)
				continue;
			String prefix2 = options.getProperty("prefix2:" + lang, null);
			List<String> p1 = prefix1 != null ? Arrays.asList(prefix1.split(",")) : Collections.emptyList();
			List<String> p2 = prefix2 != null ? Arrays.asList(prefix2.split(",")) : Collections.emptyList();
			langPrefixMap.put(lang, genPrefix(p1, p2));
		}
	}

	private void processOption(String key, String val) {
		String[] keysParts = key.split(":");
		String[] valParts = val.split(",");
		if (keysParts.length < 2 || val.isEmpty() || valParts.length < 1) {
			throw new IllegalArgumentException("don't understand " + key + " = " + val);
		}
		switch (keysParts[0].trim()) {
		case "prefix1":
		case "prefix2":
			options.put(key, val); // store for later processing
			break;
		case "suffix":
			List<String> suffixes = new ArrayList<>();
			for (String s : valParts) {
				suffixes.add(stripQuotes(s.trim()));
			}
			sortByLength(suffixes);
			langSuffixMap.put(keysParts[1].trim(), suffixes);
			break;
		case "lang":
			String iso = keysParts[1].trim();
			List<String> langs = new ArrayList<>();
			for (String lang : valParts) {
				langs.add(lang.trim());
			}
			countryLanguageMap .put(iso, langs);
			languages.addAll(langs);
		default:
			break;
		}
	}
	

	/** Create all combinations of items in prefix1 with items in prefix2 and finally prefix1 with an extra blank.  
	 * @param prefix1 list of prefix words
	 * @param prefix2 list of prepositions
	 * @return all combinations
	 */
	private List<String> genPrefix (List<String> prefix1, List<String> prefix2) {
		List<String> prefixes = new ArrayList<>();
		for (String p1 : prefix1) {
			p1 = stripQuotes(p1);
			for (String p2 : prefix2) {
				p2 = stripQuotes(p2);
				prefixes.add(p1 + " " + p2);
			}
			prefixes.add(p1 + " ");
		}
		return prefixes;
	}

	private String stripQuotes(String s) {
		if (s.startsWith("'") && s.endsWith("'") || s.startsWith("\"") && s.endsWith("\"")) {
			return s.substring(1, s.length()-1);
		}
		return s;
	}
	
	
	/**
	 * Modify all labels of a road. Each label is checked against country specific lists of 
	 * well known prefixes (e.g. "Rue de la ", "Avenue des "  ) and suffixes (e.g. " Road").
	 * If a well known prefix is found the label is modified. If the prefix ends with a blank,
	 * that blank is replaced by 0x1e, else 0x1b is added after the prefix.
	 * If a well known suffix is found the label is modified. If the suffix starts with a blank,
	 * that blank is replaced by 0x1f, else 0x1c is added before the suffix.
	 * @param road
	 */
	public void filter(MapRoad road) {
		if (!enabled)
			return;
		String country = road.getCountry();
		if (country == null)
			return;
		
		List<String> prefixesCountry = getSearchStrings(country, MODE_PREFIX);
		List<String> suffixesCountry = getSearchStrings(country, MODE_SUFFIX);
		
		// perform brute force search, seems to be fast enough
		String[] labels = road.getLabels();
		for (int i = 0; i < labels.length; i++) {
			String label = labels[i];
			if (label == null || label.length() == 0)
				continue;
			boolean modified = false;
			for (String prefix : prefixesCountry) {
				if (label.charAt(0) < 7)
					break; // label starts with shield code
				if (label.length() < prefix.length())
					continue;
				if (prefix.equalsIgnoreCase(label.substring(0, prefix.length()))) {
					if (prefix.endsWith(" ")) {
						label = prefix.substring(0, prefix.length() - 1) + (char) 0x1e
								+ label.substring(prefix.length());
					} else {
						label = prefix + (char) 0x1b + label.substring(prefix.length());
					}
					modified = true;
					break;
				}
			}
			for (String suffix : suffixesCountry) {
				int len = label.length();
				if (len < suffix.length())
					continue;
				int pos = len - suffix.length();
				if (suffix.equalsIgnoreCase(label.substring(pos, len))) {
					if (suffix.startsWith(" "))
						label = label.substring(0, pos) + (char) 0x1f + suffix.substring(1);
					else 
						label = label.substring(0, pos) + (char) 0x1c + suffix;
					modified = true;
					break;
				}
			}
			if (modified) {
				labels[i] = label;
				log.error("modified",label,country,road.getRoadDef());
			}
		}
	}
	
	/**
	 * Build list of prefixes or suffixes for a given country.
	 * @param country String with 3 letter ISO code
	 * @param mode : signals prefix or suffix
	 * @return List with prefixes or suffixes
	 */
	private List<String> getSearchStrings(String country, int mode) {
		Map<String, List<String>>  cache = (mode == MODE_PREFIX) ? countryPrefixMap : countrySuffixMap;
		List<String> res = cache.get(country); 
		if (res == null) {
			// compile the list 
			List<String> languages = countryLanguageMap.get(country);
			if (languages == null)
				res = Collections.emptyList();
			else  {
				List<List<String>> all = new ArrayList<>();
				for (String lang : languages) {
					List<String> prefixes = mode == MODE_PREFIX ? langPrefixMap.get(lang) : langSuffixMap.get(lang);
					if(prefixes != null)
						all.add(prefixes);
				}
				if(all.isEmpty())
					res = Collections.emptyList();
				else if (all.size() == 1) {
					res = all.get(0);
				}
				else {
					Set<String> allPrefixesSet = new HashSet<>();
					for (List<String> prefOneLang : all)
						allPrefixesSet.addAll(prefOneLang);
					res = new ArrayList<>(allPrefixesSet);
					sortByLength(res);
					
				}
			}
			// cache the result
			cache.put(country, res);
		}
		return res;
	}

	/**
	 * Sort by string length so that longest string comes first.
	 * @param strings
	 */
	private void sortByLength(List<String> strings) {
		strings.sort(new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return Integer.compare(o2.length(), o1.length());
			}
		});
	}
}

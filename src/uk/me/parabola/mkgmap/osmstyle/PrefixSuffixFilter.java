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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.mdr.Mdr7;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapRoad;
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
	private final Map<String, List<String>> langPrefixMap = new HashMap<>();
	private final Map<String, List<String>> langSuffixMap = new HashMap<>();
	private final Map<String, List<String>> countryLanguageMap = new HashMap<>();
	private final Map<String, List<String>> countryPrefixMap = new HashMap<>();
	private final Map<String, List<String>> countrySuffixMap = new HashMap<>();

	public PrefixSuffixFilter(EnhancedProperties props) {
		enabled = props.getProperty("use-prefix-suffix-filter", false);
		if (enabled) {
			buildCountryLanguageMap(props);
			configPrefixMap(props);
			configSuffixMap(props);
		}
	}
	
	/**
	 * @param props the program properties
	 * @return Map which maps 3 letter ISO code for country name to a list of 2 letter language codes
	 * used for road names, e.g. Canada (=CAN) uses English (=en) and French (=fr) language.
	 * The order in the list doesn't matter. 
	 * TODO: read from a configuration file.
	 */
	private void buildCountryLanguageMap(EnhancedProperties props) {
		countryLanguageMap.put("ITA", Arrays.asList("it"));
		countryLanguageMap.put("DEU", Arrays.asList("de"));
		countryLanguageMap.put("FRA", Arrays.asList("fr"));
		countryLanguageMap.put("PRT", Arrays.asList("pt"));
		countryLanguageMap.put("ESP", Arrays.asList("es"));
		countryLanguageMap.put("CAN", Arrays.asList("fr,en"));
		countryLanguageMap.put("GBR", Arrays.asList("en"));
		countryLanguageMap.put("USA", Arrays.asList("en"));
		countryLanguageMap.put("CHE", Arrays.asList("de","it","fr"));
	}
	
	/**
	 * @param props the program properties
	 * @return Map with well known road name suffixed for a given 2 letter language code.  
	 * TODO: read from a configuration file.
	 */
	private void configSuffixMap(EnhancedProperties props) {
		langSuffixMap.put("de", Arrays.asList(" Straße", " Strasse", "-Straße", "-Strasse", " Weg", "-Weg"));
		langSuffixMap.put("en", Arrays.asList(" Road,  Street"));
	}

	/**
	 * @param props the program properties
	 * @return Map with well known road name prefixes for a given 2 letter language code.  
	 * TODO: read from a configuration file.
	 */
	private void configPrefixMap(EnhancedProperties props) {
		{
			List<String> prefix1 = Arrays.asList("Calle", "Carrer", "Avenida");
			List<String> prefix2 = Arrays.asList("de las ", "de los ", "de la ", "del ", "de ", "d'");
			langPrefixMap.put("es", genPrefix(prefix1, prefix2));
		}
		{
			List<String> prefix1 = Arrays.asList("Allée", "Chemin", "Avenue", "Rue", "Place");
			List<String> prefix2 = Arrays.asList("de la ", "du ", "de ", "des ", "d'", "de l'");
			langPrefixMap.put("fr", genPrefix(prefix1, prefix2));
		}
		{
			List<String> prefix1 = Arrays.asList("Rua", "Avenida", "Travessa");
			List<String> prefix2 = Arrays.asList("da ", "do ", "de ", "das ", "dos ");
			langPrefixMap.put("pt", genPrefix(prefix1, prefix2));
		}
		{
			List<String> prefix1 = Arrays.asList("Via", "Piazza", "Viale");
			List<String> prefix2 = Arrays.asList("del ", "dei ", "della ", "delle ", "di ");
			langPrefixMap.put("it", genPrefix(prefix1, prefix2));
		}

		for (Entry<String, List<String>> e : langPrefixMap.entrySet())
			sortByLength(e.getValue());
	}

	private List<String> genPrefix (List<String> prefix1, List<String> prefix2) {
		List<String> prefixes = new ArrayList<>();
		for (String p1 : prefix1) {
			for (String p2 : prefix2) {
				prefixes.add(p1 + " " + p2);
			}
			prefixes.add(p1 + " ");
		}
		return prefixes;
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
				if (label.startsWith(prefix)) {
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
				int pos = label.lastIndexOf(suffix);
				if (pos > 0) {
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
				log.error("check",label,country,road.getRoadDef());
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
	 * @param prefixes
	 */
	private void sortByLength(List<String> prefixes) {
		prefixes.sort(new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return Integer.compare(o2.length(), o1.length());
			}
		});
	}
}

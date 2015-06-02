/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.build;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import uk.me.parabola.util.EnhancedProperties;

public class LocatorUtil {

	private static final Pattern COMMA_OR_SPACE_PATTERN = Pattern
			.compile("[,\\s]+");
	
	public static List<String> getNameTags(Properties props) {
		String nameTagProp = props.getProperty("name-tag-list", "name");
		return Arrays.asList(COMMA_OR_SPACE_PATTERN.split(nameTagProp));
	}

	/**
	 * Parses the parameters of the location-autofill option. Establishes also downwards
	 * compatibility with the old integer values of location-autofill. 
	 * @param props program properties
	 * @return the options
	 */
	public static Set<String> parseAutofillOption(EnhancedProperties props) {
		String optionStr = props.getProperty("location-autofill", null);
		if (optionStr == null) {
			return Collections.emptySet();
		}
	
		Set<String> autofillOptions = new HashSet<String>(Arrays.asList(COMMA_OR_SPACE_PATTERN
				.split(optionStr)));
	
		// convert the old autofill options to the new parameters
		if (autofillOptions.contains("0")) {
			autofillOptions.add("is_in");
			autofillOptions.remove("0");
		}
		if (autofillOptions.contains("1")) {
			autofillOptions.add("is_in");
			// PENDING: fuzzy search
			autofillOptions.remove("1");
		}
		if (autofillOptions.contains("2")) {
			autofillOptions.add("is_in");
			// PENDING: fuzzy search
			autofillOptions.add("nearest");
			autofillOptions.remove("2");
		}		
		if (autofillOptions.contains("3")) {
			autofillOptions.add("is_in");
			// PENDING: fuzzy search
			autofillOptions.add("nearest");
			autofillOptions.remove("3");
		}	
		final List<String> knownOptions = Arrays.asList("bounds","is_in","nearest");
		for (String s : autofillOptions){
			if (knownOptions.contains(s) == false){
				throw new IllegalArgumentException(s + " is not a known sub option for option location-autofill: " + optionStr);
			}
		}
		return autofillOptions;
	}
}

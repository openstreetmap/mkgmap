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
 * Create date: May 25, 2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * A OSM feature can have a default name that is used when there is no
 * real name for the feature.  This is most useful when several different OSM
 * features are represented by the same garmin type.
 *  
 * @author Steve Ratcliffe
 */
public class DefaultFeatureNames {
	private static final Logger log = Logger.getLogger(DefaultFeatureNames.class);
	
	private final Map<String, String> values = new HashMap<String, String>();

	public DefaultFeatureNames(StyleFileLoader loader, Locale loc) {
		log.info("reading default names");
		String lang = loc.getLanguage();
		String country = loc.getCountry();

		// First try for the lang_country varient
		String[] names = new String[]{
				"default-names/names",
				"default-names/names_" + lang,
				"default-names/names_" + lang + '_' + country,
		};

		for (String name : names) {
			try {
				Reader r = loader.open(name);
				readFile(r);
			} catch (FileNotFoundException e) {
				// carry on with the next
				log.debug("no file", name);
			}
		}
	}

	public String get(String key) {
		log.debug("looking for", key);
		return values.get(key);
	}

	private void readFile(Reader r) {
		TokenScanner ws = new TokenScanner(r);

		ws.skipSpace();
		while (!ws.isEndOfFile()) {
			String key = ws.readWord();
			ws.skipSpace();

			Token t = ws.nextToken();
			if (checkSymbolValue(t, "(")) {
				String def = readDefault(ws);
				log.debug("putting", key, def);
				values.put(key, def);
			} else {
				ws.skipLine();
			}
		}
	}

	private String readDefault(TokenScanner ws) {
		StringBuffer sb = new StringBuffer();
		while (!ws.isEndOfFile()) {
			Token t = ws.nextToken();
			if (checkSymbolValue(t, ")"))
				break;
			sb.append(t.getValue());
		}
		return sb.toString();
	}

	private boolean checkSymbolValue(Token t, String value) {
		return t.getType() == TokType.SYMBOL && t.getValue().equals(value);
	}
}

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
 * Create date: Apr 20, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.io.Reader;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Information about a style.  This is so style authors can include
 * descriptions of their styles within the style itself.
 *
 * @author Steve Ratcliffe
 */
public class StyleInfo {
	private static final Logger log = Logger.getLogger(StyleInfo.class);

	private String version;
	private String description;
	private String longDescription;

	public void readInfo(Reader r) {
		TokenScanner ws = new TokenScanner(r);
		while (!ws.isEndOfFile()) {
			String word = ws.nextValue();
			if (word == null)
				continue;
			if (word.equals("description"))
				fetchSummary(ws);
			else if (word.equals("version")) {
				fetchVersion(ws);
			}
		}
	}

	private void fetchVersion(TokenScanner ws) {
		if (ws.firstTokenType() == TokType.SYMBOL)
			ws.nextToken();
		version = ws.readLine();
		log.debug("file info: set version to", version);
	}

	private void fetchSummary(TokenScanner ws) {
		if (ws.nextToken().getType() == TokType.SYMBOL)
			ws.nextToken();
		ws.skipSpace();
		description = ws.readLine();
		log.debug("file info: set description to", description);
	}

	public String getDescription() {
		return description == null ? "No description available" : description;
	}

	public String getVersion() {
		return version == null ? "1" : version;
	}

	public String getLongDescription() {
		return longDescription != null ? longDescription : "";
	}

	/**
	 * Merge the other style info in so that it doesn't override anything
	 * in the current info.  In general, it is probably not very useful.
	 * @param other The info to be merged in.  Nothing will overwrite anything
	 * in 'this', although it could be added.
	 */
	public void merge(StyleInfo other) {
		if (other.description != null)
			this.description = "Based on: " + other.description;
	}
}

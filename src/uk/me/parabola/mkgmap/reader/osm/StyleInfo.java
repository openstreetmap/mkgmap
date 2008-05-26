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

	private String version = "";
	private String description = "No description available";
	private String longDescription = "";

	void readInfo(TokenScanner ws) {
		while (!ws.isEndOfFile()) {
			String word = ws.nextValue();
			if (word.equals("description"))
				fetchSummary(ws);
			else if (word.equals("version")) {
				fetchVersion(ws);
			}
		}
	}

	private void fetchVersion(TokenScanner ws) {
		if (ws.nextToken().getType() == TokType.SYMBOL)
			ws.nextToken();
		version = ws.readLine();
		log.debug("file info: set version to", version);
	}

	private void fetchSummary(TokenScanner ws) {
		if (ws.nextToken().getType() == TokType.SYMBOL)
			ws.nextToken();
		
		description = ws.readLine();
		log.debug("file info: set description to", description);
	}

	public String getDescription() {
		return description;
	}

	public String getVersion() {
		return version;
	}

	public String getLongDescription() {
		return longDescription;
	}
}

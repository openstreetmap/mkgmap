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
 * Create date: 16-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.List;
import java.util.ArrayList;

import uk.me.parabola.mkgmap.scan.TokenScanner;
import uk.me.parabola.mkgmap.scan.TokType;

/**
 * @author Steve Ratcliffe
 */
public class ActionReader {
	private final TokenScanner scanner;

	public ActionReader(TokenScanner scanner) {
		this.scanner = scanner;
	}

	public List<Action> readActions() {
		List<Action> actions = new ArrayList<Action>();
		scanner.skipSpace();
		while (!scanner.isEndOfFile()) {
			if (scanner.checkToken(TokType.SYMBOL, "}"))
				break;
		}
		scanner.skipSpace();
		return null;
	}
}

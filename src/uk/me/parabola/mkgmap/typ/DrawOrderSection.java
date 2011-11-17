/*
 * Copyright (C) 2011.
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
package uk.me.parabola.mkgmap.typ;

import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Process lines from the draw order section of the typ.txt file.
 *
 * @author Steve Ratcliffe
 */
class DrawOrderSection implements ProcessSection {
	private final TypData data;

	DrawOrderSection(TypData data) {
		this.data = data;
	}

	/**
	 * There is only one tag in this section.
	 */
	public void processLine(TokenScanner scanner, String name, String value) {
		if (!name.equalsIgnoreCase("Type"))
			throw new SyntaxException(scanner, "Unrecognised keyword in draw order section: " + name);

		String[] typeDrawOrder = value.split(",",-1);
		if (typeDrawOrder.length != 2)
			throw new SyntaxException(scanner, "Unrecognised drawOrder type " + value);

		int fulltype;
		try {
			fulltype = Integer.decode(typeDrawOrder[0]);
		} catch (NumberFormatException e) {
			throw new SyntaxException(scanner, "Bad number " + typeDrawOrder[0]);
		}
		int type;
		int subtype = 0;

		if (fulltype > 0x10000) {
			type = (fulltype >> 8) & 0xff;
			subtype = fulltype & 0xff;
		} else {
			type = fulltype & 0xff;
		}

		try {
			int level = Integer.parseInt(typeDrawOrder[1]);
			data.addPolygonStackOrder(level, type, subtype);
		} catch (NumberFormatException e) {
			throw new SyntaxException(scanner, "Bad number '" + typeDrawOrder[1] + "'");
		}
	}

	/**
	 * Nothing to do, each line stands by itself.
	 */
	public void finish(TokenScanner scanner) {
	}
}

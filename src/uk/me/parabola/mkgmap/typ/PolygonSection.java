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

import uk.me.parabola.imgfmt.app.typ.ColourInfo;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.app.typ.TypPolygon;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Process lines from the polygon section of a typ.txt file.
 *
 * This is the simplest, so all the work is done in the superclass.
 *
 * @author Steve Ratcliffe
 */
class PolygonSection extends CommonSection implements ProcessSection {

	private final TypPolygon current = new TypPolygon();

	PolygonSection(TypData data) {
		super(data);
	}

	public void processLine(TokenScanner scanner, String name, String value) {
		if (commonKey(scanner, current, name, value))
			return;

		warnUnknown(name);
	}

	/**
	 * Add the polygon to the data and reset for the next.
	 */
	public void finish(TokenScanner scanner) {
		validate(scanner);
		data.addPolygon(current);
	}

	/**
	 * Check xmp restrictions for polygons.
	 *
	 * The main one is that bitmaps must be 32x32.  Only certain numbers of colours are
	 * allowed as well.
	 */
	protected void xpmCheck(TokenScanner scanner, ColourInfo colourInfo) {
		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();

		switch (colourInfo.getNumberOfColours()) {
		case 1:
		case 2:
		case 4:
			break;
		default:
			throw new SyntaxException(scanner, "Polygons must have 1, 2 or 4 colours");
		}

		if (width == 0 && height == 0)
			return;

		if (height != 32 || width != 32)
			throw new SyntaxException(scanner, "Polygon bitmaps must be 32x32");
	}
}

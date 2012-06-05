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
import uk.me.parabola.imgfmt.app.typ.TypLine;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Process lines from a line definition in the typ.txt file.
 *
 * Most of the work is done in the superclass since all the element types
 * are very similar.
 *
 * @author Steve Ratcliffe
 */
class LineSection extends CommonSection implements ProcessSection {

	private final TypLine current = new TypLine();

	LineSection(TypData data) {
		super(data);
	}

	public void processLine(TokenScanner scanner, String name, String value) {
		if (commonKey(scanner, current, name, value))
			return;

		if (name.equalsIgnoreCase("UseOrientation")) {
			current.setUseOrientation(value.charAt(0) == 'Y');
		} else if (name.equalsIgnoreCase("LineWidth")) {
			try {
				int ival = Integer.decode(value);
				current.setLineWidth(ival);
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Bad number for line width: " + value);
			}
		} else if (name.equalsIgnoreCase("BorderWidth")) {
			try {
				int ival = Integer.decode(value);
				current.setBorderWidth(ival);
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Bad number for line width: " + value);
			}
		} else
			warnUnknown(name);
	}

	public void finish(TokenScanner scanner) {
		validate(scanner);
		current.finish();
		data.addLine(current);
	}
}

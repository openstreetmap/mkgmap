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
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Process lines from a line definition in the typ.txt file.
 *
 * @author Steve Ratcliffe
 */
class LineSection extends CommonSection implements ProcessSection {

	private TypLine current = new TypLine();

	LineSection(TypData data) {
		super(data);
	}

	public void processLine(TokenScanner scanner, String name, String value) {
		if (commonKey(scanner, current, name, value))
			return;

		if (name.equals("UseOrientation")) {
			current.setUseOrientation(value.charAt(0) == 'Y');
		} else if (name.equals("LineWidth")) {
			int ival = Integer.decode(value);
			current.setLineWidth(ival);
		} else
			warnUnknown(name);
	}

	public void finish() {
		data.addLine(current);
		current = new TypLine();
	}
}

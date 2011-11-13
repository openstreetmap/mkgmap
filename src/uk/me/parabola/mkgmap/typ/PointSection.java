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
import uk.me.parabola.imgfmt.app.typ.TypPoint;
import uk.me.parabola.imgfmt.app.typ.Xpm;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Process lines from the point section.
 *
 * @author Steve Ratcliffe
 */
class PointSection extends CommonSection implements ProcessSection {

	private final TypPoint current = new TypPoint();

	protected PointSection(TypData data) {
		super(data);
	}

	public void processLine(TokenScanner scanner, String name, String value) {
		if (commonKey(scanner, current, name, value))
			return;

		if (name.equals("SubType")) {
			int ival;
			try {
				ival = Integer.decode(value);
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Bad number for sub type " + value);
			}

			current.setSubType(ival);

		} else if (name.equals("DayXpm")) {
			Xpm xpm = readXpm(scanner, value);
			xpm.getColourInfo().setColourMode(16); // XXX temporary
			xpm.getColourInfo().setSimple(false);
			current.setXpm(xpm);

		} else if (name.equals("NightXpm")) {
			Xpm xpm = readXpm(scanner, value);
			xpm.getColourInfo().setColourMode(16); // XXX temporary
			xpm.getColourInfo().setSimple(false);
			current.setNightXpm(xpm);

		} else {
			warnUnknown(name);
		}
	}

	public void finish() {
		data.addPoint(current);
	}
}

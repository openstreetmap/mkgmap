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

		if (name.equalsIgnoreCase("DayXpm")) {
			Xpm xpm = readXpm(scanner, value, current.simpleBitmap());
			current.setXpm(xpm);

		} else if (name.equalsIgnoreCase("NightXpm")) {
			Xpm xpm = readXpm(scanner, value, current.simpleBitmap());
			current.setNightXpm(xpm);

		} else {
			warnUnknown(name);
		}
	}

	public void finish(TokenScanner scanner) {
		validate(scanner);
		data.addPoint(current);
	}
}

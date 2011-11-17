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
import uk.me.parabola.imgfmt.app.typ.TypIconSet;
import uk.me.parabola.imgfmt.app.typ.Xpm;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * The new icon type. There are several icons at different resolutions.
 * 
 * @author Steve Ratcliffe
 */
public class IconSection extends CommonSection implements ProcessSection {
	private final TypIconSet current = new TypIconSet();

	protected IconSection(TypData data) {
		super(data);
	}

	public void processLine(TokenScanner scanner, String name, String value) {
		if (name.equalsIgnoreCase("String")) {
			// There is only one string and it doesn't have a language prefix.
			// But if it does we will just ignore it.
			current.addLabel(value);
			return;
		}

		if (commonKey(scanner, current, name, value))
			return;

		if (name.equalsIgnoreCase("IconXpm")) {
			Xpm xpm = readXpm(scanner, value);
			xpm.getColourInfo().setSimple(false);
			current.addIcon(xpm);
		} else {
			warnUnknown(name);
		}
	}

	public void finish(TokenScanner scanner) {
		data.addIcon(current);
	}
}

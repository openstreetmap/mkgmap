/*
 * Copyright (C) 2012.
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
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * A section processor that does nothing.
 *
 * Used for unknown sections, or for sections that we want to ignore.
 *
 * @author Steve Ratcliffe
 */
public class IgnoreSection implements ProcessSection {
	public IgnoreSection(TypData data) {
	}

	public void processLine(TokenScanner scanner, String name, String value) {
	}

	public void finish(TokenScanner scanner) {
	}
}

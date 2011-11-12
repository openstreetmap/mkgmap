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
package uk.me.parabola.imgfmt.app.typ;

import java.nio.charset.CharsetEncoder;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Represents a POI in the typ file.
 *
 * @author Steve Ratcliffe
 */
public class TypPoint extends TypElement {
	private Xpm nightXpm;

	public void write(ImgFileWriter writer, CharsetEncoder encoder) {
		throw new UnsupportedOperationException();
	}

	public void setNightXpm(Xpm nightXpm) {
		this.nightXpm = nightXpm;
	}
}

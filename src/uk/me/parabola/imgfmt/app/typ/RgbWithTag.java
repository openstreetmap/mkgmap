/*
 * Copyright (C) 2008 Steve Ratcliffe
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.typ;

/**
 * @author Steve Ratcliffe
 */
public class RgbWithTag extends Rgb {
	private final String tag;

	public RgbWithTag(String tag, Rgb rgb) {
		super(rgb.getR(), rgb.getG(), rgb.getB(), rgb.getA());
		this.tag = tag;
	}

	public RgbWithTag(RgbWithTag rgb, int alpha) {
		super(rgb, alpha);
		this.tag = rgb.getTag();
	}

	public String getTag() {
		return tag;
	}
}

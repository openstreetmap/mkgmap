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
package uk.me.parabola.imgfmt.app.typ;

/**
 * Interface for adding an alpha value to a previously saved colour value.
 *
 * The current TYP editors place the alpha value after the colour definition, eg:
 *
 *   "#992299" alpha 5
 *
 * so we need to add the alpha value to a colour that has already been read
 * in.
 *
 * @author Steve Ratcliffe
 */
public interface AlphaAdder {

	/**
	 * Add an alpha value to the last colour that was saved.
	 *
	 * @param alpha A true alpha value ie 0 is transparent, 255 opaque.
	 */
	public void addAlpha(int alpha);
}

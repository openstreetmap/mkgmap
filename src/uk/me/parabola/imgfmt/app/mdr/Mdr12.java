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
package uk.me.parabola.imgfmt.app.mdr;

/**
 * This is the index for the POI section (Mdr11).
 * It works exactly the same way as Mdr8 as far as we know.
 *
 * @author Steve Ratcliffe
 */
public class Mdr12 extends Mdr8{
	public Mdr12(MdrConfig config) {
		super(config);
	}

	protected int associatedSize() {
		return getSizes().getPoiSize();
	}
}

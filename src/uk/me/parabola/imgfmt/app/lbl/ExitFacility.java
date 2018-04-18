/*
 * Copyright (C) 2007 Steve Ratcliffe
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *
 * Author: Steve Ratcliffe
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;

/**
 * Represent a facility at a motorway exit
 *
 * @author Mark Burton
 */
public class ExitFacility {

	private final int index;
	private final Label description;
	private final	int type;	// truck stop - 24 hour diesel + food
	private final	int direction;	// undefined
	private final	int facilities;	// none
	private final boolean last;

	public ExitFacility(int type, char direction, int facilities, Label description, boolean last, int index) {
		this.type = type;
		this.direction = directionCode(direction);
		this.facilities = facilities;
		this.description = description;
		this.last = last;
		this.index = index;
	}

	void write(ImgFileWriter writer) {
		int word = 0;
		word |= description.getOffset(); // 0:21 = label offset
		// 22 = unknown
		if(last)
			word |= 1 << 23; // 23 = last facility for this exit
		word |= type << 24;	 // 24:27 = 4 bit type
		// 28 = unknown
		word |= direction << 29; // 29:31 = 3 bit direction
		writer.put4(word);
		writer.put1u(facilities);
	}

	public int getIndex() {
		return index;
	}

	public boolean getOvernightParking() {
		return false;
	}

	private int directionCode(char direction) {
		int code = "NSEWIOB".indexOf(direction);
		if(code < 0)
			code = 7; // undefined
		return code;
	}
}

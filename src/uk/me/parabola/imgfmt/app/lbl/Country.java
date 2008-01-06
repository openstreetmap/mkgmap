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

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.WriteStrategy;

/**
 * A country contains one or more regions.
 * 
 * @author Steve Ratcliffe
 */
public class Country {
	// The country number.  This is not recorded in the file
	private final char index;
	private Label label;

	public Country(int index) {
		this.index = (char) index;
	}

	void write(WriteStrategy writer) {
		writer.put3(label.getOffset());
	}

	public char getIndex() {
		return index;
	}

	public void setLabel(Label label) {
		this.label = label;
	}
}

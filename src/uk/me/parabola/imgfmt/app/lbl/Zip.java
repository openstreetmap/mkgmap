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
 * A zip or postal code record.
 *
 * @author Steve Ratcliffe
 */
public class Zip {
	// The index is not stored in the file, you just use the index of it in
	// the section.
	private final int index;
	
	private Label label;

	public Zip(int index) {
		this.index = index;
	}

	public void write(WriteStrategy writer) {
		writer.put3(label.getOffset());
	}

	public void setLabel(Label label) {
		this.label = label;
	}

	public int getIndex() {
		return index;
	}
}

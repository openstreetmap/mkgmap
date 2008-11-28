/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 18-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * @author Steve Ratcliffe
 */
public class TableA {
	private static final Logger log = Logger.getLogger(TableA.class);
	
	private static final int ITEM_SIZE = 5;

	private int size;
	private int numberOfItems;

	public void write(ImgFileWriter writer) {
	}

	public int getSize() {
		return size;
	}

	public void addItem() {
		numberOfItems++;
		size += ITEM_SIZE;
	}

	public int getNumberOfItems() {
		return numberOfItems;
	}

	/**
	 * This is called first to reserve enough space.  It will be rewritten
	 * later.
	 */
	public void reserve(ImgFileWriter writer) {
		log.debug("tab a size ", size);
		writer.position(writer.position() + size);
	}
}

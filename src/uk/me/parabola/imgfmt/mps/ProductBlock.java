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
 * Create date: Dec 19, 2007
 */
package uk.me.parabola.imgfmt.mps;

import java.io.IOException;

import uk.me.parabola.io.StructuredOutputStream;

/**
 * A block describing a particular product.  Not sure how this relates
 * to the map set.
 *
 * @author Steve Ratcliffe
 */
public class ProductBlock extends Block {
	private static final int BLOCK_TYPE = 0x46;
	
	private int product;
	private String description = "OSM maps";

	public ProductBlock() {
		super(BLOCK_TYPE);
	}

	protected void writeBody(StructuredOutputStream out) throws IOException {
		out.write4(product);
		out.writeString(description);
	}

	public void setProduct(int product) {
		this.product = product;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}

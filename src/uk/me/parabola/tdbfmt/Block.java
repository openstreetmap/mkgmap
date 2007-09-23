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
 * Create date: 23-Sep-2007
 */
package uk.me.parabola.tdbfmt;

import java.io.ByteArrayInputStream;

/**
 * A block within the tdb file.  Really just a type and the contents.
 *
 * @author Steve Ratcliffe
 */
public class Block {
	private final int blockId;
	private final int blockLength;
	private final byte[] body;

	public Block(int type, byte[] body) {
		blockId = type;
		this.body = body;
		this.blockLength = body.length;
	}

	public int getBlockId() {
		return blockId;
	}

	public int getBlockLength() {
		return blockLength;
	}

	public byte[] getBody() {
		return body;
	}

	/**
	 * Get a stream for the body of this block.
	 *
	 * @return A structured stream that can be used to read the body of this
	 * block.
	 */
	public StructuredInputStream getStream() {
		ByteArrayInputStream stream = new ByteArrayInputStream(getBody());
		StructuredInputStream ds = new StructuredInputStream(stream);
		return ds;
	}
}

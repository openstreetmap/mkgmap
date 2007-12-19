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

import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * All the blocks in the file have a type and a length.
 *
 * @author Steve Ratcliffe
 */
public abstract class Block {
	private final int type;
	private ByteArrayOutputStream output = new ByteArrayOutputStream();

	protected Block(int type) {
		this.type = type;
	}

	protected OutputStream getOutput() {
		return output;
	}

	public int getType() {
		return type;
	}

	/**
	 * This is only valid after everything is written to the block.
	 *
	 * @return The length of the block (or the amount written already).
	 */
	public int getLength() {
		return output.toByteArray().length;
	}

	public abstract void write(ImgChannel chan);
}

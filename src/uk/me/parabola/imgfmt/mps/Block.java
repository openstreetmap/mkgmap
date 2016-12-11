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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import uk.me.parabola.imgfmt.app.labelenc.AnyCharsetEncoder;
import uk.me.parabola.imgfmt.app.labelenc.TableTransliterator;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.io.StructuredOutputStream;

/**
 * All the blocks in the file have a type and a length.
 *
 * @author Steve Ratcliffe
 */
public abstract class Block {
	private final int type;
	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	protected Block(int type) {
		this.type = type;
	}

	public void write(ImgChannel chan) throws IOException {
		// First write the body to the byte buffer so that we know its length.
		writeBody(new StructuredOutputStream(output, // TODO temporary while tdbfile is fixed.
				new AnyCharsetEncoder("latin1", new TableTransliterator("ascii"))));

		ByteBuffer buf = ByteBuffer.allocate(16);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) type);
		char len = getLength();
		buf.putChar(len);

		// write the header.
		buf.flip();
		chan.write(buf);

		// write the body.
		buf = ByteBuffer.allocate(len);
		buf.put(output.toByteArray());
		buf.flip();
		chan.write(buf);
	}

	/**
	 * Writes the body to the output stream given.
	 *
	 * @param out The stream to write to.
	 */
	protected abstract void writeBody(StructuredOutputStream out) throws IOException;

	/**
	 * This is only valid after everything is written to the block.
	 *
	 * @return The length of the block (or the amount written already).
	 */
	private char getLength() {
		int len = output.toByteArray().length;
		assert len <= 0xffff;
		return (char) len;
	}
}

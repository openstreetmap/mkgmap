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

import uk.me.parabola.log.Logger;
import uk.me.parabola.io.StructuredInputStream;
import uk.me.parabola.io.StructuredOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A block within the tdb file.  Really just a type and the contents.
 *
 * @author Steve Ratcliffe
 */
class Block {
	private static final Logger log = Logger.getLogger(Block.class);

	private final int blockId;
	private int blockLength;
	private byte[] body;
	private StructuredInputStream istream;
	private ByteArrayOutputStream arrayBody;
	private StructuredOutputStream ostream;

	/**
	 * Create a block that is going to be written to.
	 * @param blockId The id for this block.
	 */
	Block(int blockId) {
		this.blockId = blockId;
	}

	/**
	 * Create a block from data that is read in from a file.
	 * @param type The block type.
	 * @param body The raw bytes in the block.
	 */
	Block(int type, byte[] body) {
		blockId = type;
		this.body = body;
		this.blockLength = body.length;
		ByteArrayInputStream stream = new ByteArrayInputStream(body);
		this.istream = new StructuredInputStream(stream);
	}

	public int getBlockId() {
		return blockId;
	}

	/**
	 * Get the raw bytes for this block.  The source depends on if this block
	 * was constructed from file data, or is being created from program calls
	 * so that it can be written.
	 *
	 * @return A byte array of the raw bytes representing this block.
	 */
	byte[] getBody() {
		if (body == null && arrayBody != null) {
			byte[] bytes = arrayBody.toByteArray();

			blockLength = bytes.length - 3;

			// Fill in the length in the space that we left earlier.
			bytes[1] = (byte) (blockLength & 0xff);
			bytes[2] = (byte) ((blockLength >> 8) & 0xff);
			return bytes;
		}
		return body;
	}

	/**
	 * Get a stream for the body of this block.
	 *
	 * @return A structured stream that can be used to read the body of this
	 * block.
	 */
	public StructuredInputStream getInputStream() {
		arrayBody = null;
		return this.istream;
	}

	public StructuredOutputStream getOutputStream() {
		if (ostream == null) {
			arrayBody = new ByteArrayOutputStream();
			body = null;
			ostream = new StructuredOutputStream(arrayBody);
			try {
				ostream.write(blockId);
				ostream.write2(0); // This will be filled in later.
			} catch (IOException e) {
				log.warn("failed writing to array");
			}
		}

		return ostream;
	}

	public void write(OutputStream stream) throws IOException {
		byte[] b = getBody();
		if (b != null)
			stream.write(b);
	}
}

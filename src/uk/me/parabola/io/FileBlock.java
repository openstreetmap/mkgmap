/*
 * Copyright (C) 2016.
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
package uk.me.parabola.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import uk.me.parabola.imgfmt.app.labelenc.CharacterEncoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;

/**
 * A block forming part of a file.
 *
 * Used for TDB and MPS files.  It will be read and written using the structured reader and
 * writer defined in this package.
 */
public abstract class FileBlock {
	private final byte blockId;

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	public FileBlock(int blockId) {
		this.blockId = (byte) blockId;

		fillHeader();
	}

	/**
	 * Write the body of the block.
	 */
	protected abstract void writeBody(StructuredOutputStream output) throws IOException;

	private StructuredOutputStream getStructuredOutput(int codePage) {
		CharacterEncoder enc = CodeFunctions.createEncoderForLBL(0, codePage).getEncoder();
		return new StructuredOutputStream(output, enc);
	}

	/**
	 * Write to an output stream.
	 *
	 * This will be to the actual TDB file.
	 */
	public void writeTo(OutputStream os, int codePage) throws IOException {
		StructuredOutputStream output = getStructuredOutput(codePage);
		writeBody(output);

		byte[] b = this.output.toByteArray();

		// Fill in the actual full block length
		int blockLength = b.length - 3;
		b[1] = (byte) (blockLength & 0xff);
		b[2] = (byte) ((blockLength >> 8) & 0xff);
		os.write(b);
	}

	/**
	 * Write the header to the internal buffer.  The length may not be known yet and so is re-written later.
	 */
	private void fillHeader() {
		try {
			byte[] b = new byte[3];
			b[0] = this.blockId;

			output.write(b);
		} catch (IOException ignore) {
			// does not throw
		}
	}
}

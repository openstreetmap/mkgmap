/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 14-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.log.Logger;

/**
 * A class to write the bitstream.
 *
 * @author Steve Ratcliffe
 */
public class BitWriter {
	private static final Logger log = Logger.getLogger(BitWriter.class);

	// Choose so that most roads will not fill it.
	private static final int INITIAL_BUF_SIZE = 20;

	// The byte buffer and its current length (allocated length)
	private byte[] buf;  // The buffer
	private int bufsize;  // The allocated size
	private int buflen; // The actual used length

	// The bit offset into the byte array.
	private int bitoff;
	private static final int BUFSIZE_INC = 50;

	public BitWriter() {
		bufsize = INITIAL_BUF_SIZE;
		buf = new byte[bufsize];
	}

	/**
	 * Put exactly one bit into the buffer.
	 *
	 * @param b The bottom bit of the integer is set at the current bit position.
	 */
	private void put1(int b) {
		ensureSize(bitoff + 1);

		int off = getByteOffset(bitoff);

		// Get the remaining bits into the byte.
		int rem = bitoff - 8 * off;

		// Or it in, we are assuming that the position is never turned back.
		buf[off] |= (b & 0x1) << rem;

		// Increment position
		bitoff++;

		// If we are in a new byte, increase the byte length.
		if ((bitoff & 0x7) == 1)
			buflen++;

		debugPrint(b, 1);
	}
	
	public void put1(boolean b) {
		put1(b ? 1 : 0);
	}

	/**
	 * Put a number of bits into the buffer, growing it if necessary.
	 *
	 * @param bval The bits to add, the lowest <b>n</b> bits will be added to
	 * the buffer.
	 * @param nb The number of bits.
	 */
	public void putn(int bval, int nb) {
		int val = bval & ((1<<nb) - 1);
		int n = nb;

		// We need to be able to deal with more than 24 bits, but now we can't yet
		if (n >= 24)
			throw new IllegalArgumentException();

		ensureSize(bitoff + n);

		// Get each affected byte and set bits into it until we are done.
		while (n > 0) {
			int ind = getByteOffset(bitoff);
			int rem = bitoff - 8*ind;

			buf[ind] |= ((val << rem) & 0xff);

			// Shift down in preparation for next byte.
			val >>>= 8-rem;

			// Account for change so far
			int nput = 8 - rem;
			if (nput > n)
				nput = n;
			bitoff += nput;
			n -= nput;
		}

		buflen = (bitoff+7)/8;

		debugPrint(bval, nb);
	}

	public byte[] getBytes() {
		return buf;
	}

	public int getLength() {
		return buflen;
	}

	/**
	 * Get the byte offset for the given bit number.
	 *
	 * @param boff The number of the bit in question.
	 * @return The index into the byte array where the bit resides.
	 */
	private int getByteOffset(int boff) {
		return boff/8;
	}

	/**
	 * Set everything up so that the given size can be accomodated.
	 * The buffer is resized if necessary.
	 *
	 * @param newlen The new length of the bit buffer in bits.
	 */
	private void ensureSize(int newlen) {
		if (newlen/8 >= bufsize)
			reallocBuffer();
	}

	/**
	 * Reallocate the byte buffer.
	 */
	private void reallocBuffer() {
		log.debug("reallocating buffer");
		bufsize += BUFSIZE_INC;
		byte[] newbuf = new byte[bufsize];

		System.arraycopy(this.buf, 0, newbuf, 0, this.buf.length);
		this.buf = newbuf;
	}

	private void debugPrint(int b, int i) {
		if (log.isDebugEnabled())
		log.debug("after put" + i + " of " + b + " bufsize=" + bufsize + ", len="
				+ buflen + ", pos="+bitoff);
	}

	private boolean isBitSet(int i) {
		int ind = getByteOffset(i);
		int rem = i - ind*8;
		byte b = buf[ind];

		return (b & (1 << rem)) !=0;
	}


}

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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.Utils;

import java.io.UnsupportedEncodingException;
import java.util.Date;

/**
 * The header that is common to all application files within the .img file.
 * It basically contains two things of interest, the size of the header and
 * its type.  The type is usually of the form 'GARMIN.YYY' where YYY is the
 * file extension of the type eg TRE, LBL, RGN etc.
 *
 * @author Steve Ratcliffe
 */
public abstract class CommonHeader {
	protected static final int COMMON_HEADER_LEN = 21;
	private static final int TYPE_LEN = 10;

	// The common header contains the length and the type which are set at
	// construction time.
	private int headerLength;
	private String type;

	// Set to 0x80 on locked maps.  We are not interested in creating locked
	// maps, but may be useful to recognise them for completeness.
	private byte lockFlag;

	// A date of creation.
	private Date creationDate;

	protected CommonHeader(int headerLength, String type) {
		this.headerLength = headerLength;
		this.type = type;
	}

	/**
	 * Writes out the header that is common to all the file types.  It should
	 * be called by the sync() methods of subclasses when they are ready.
	 * @param writer Used to write the header.
	 */
	public final void writeHeader(WriteStrategy writer)  {
		writePrepare();

		writer.position(0);

		writer.putChar((char) headerLength);
		writer.put(Utils.toBytes(type, TYPE_LEN, (byte) 0));

		writer.put((byte) 1);  // unknown
		writer.put((byte) 0);  // not locked

		byte[] date = Utils.makeCreationTime(new Date());
		writer.put(date);

		writeFileHeader(writer);
	}

	/**
	 * Read the common header.  It starts at the beginning of the file.
	 * @param reader Used to read the header.
	 */
	public final void readHeader(ReadStrategy reader) throws ReadFailedException {
		reader.position(0);
		headerLength = reader.getChar();
		byte[] bytes = reader.get(TYPE_LEN);
		try {
			type = new String(bytes, "ascii");
		} catch (UnsupportedEncodingException e) {
			// ascii is supported always, so this can't happen
		}
		reader.get(); // ignore
		reader.get(); // ignore

		byte[] date = reader.get(7);
		creationDate = Utils.makeCreationTime(date);

		readFileHeader(reader);
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is
	 * guaranteed that the file position will be set to the correct place
	 * before this is called.
	 * @param reader The header is read from here.
	 */
	protected abstract void readFileHeader(ReadStrategy reader) throws ReadFailedException;

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will
	 * be set to the correct position before calling.
	 * @param writer The header is written here.
	 */
	protected abstract void writeFileHeader(WriteStrategy writer);

	public byte getLockFlag() {
		return lockFlag;
	}

	public void setLockFlag(byte lockFlag) {
		this.lockFlag = lockFlag;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public int getHeaderLength() {
		return headerLength;
	}

	public String getType() {
		return type;
	}

	private void writePrepare() {
		// Prepare for write by setting our defaults.
		lockFlag = 0;
		if (creationDate == null)
			creationDate = new Date();
	}
}

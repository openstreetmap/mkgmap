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

import uk.me.parabola.io.StructuredInputStream;
import uk.me.parabola.io.StructuredOutputStream;

import java.io.IOException;

/**
 * One copyright that is within the copyright block.
 *
 * @author Steve Ratcliffe
 */
class CopyrightSegment {
	 
	/**
	 * Source information text string.  Describes what data sources were used
	 * in generating the map.
	 */
	public static final int CODE_SOURCE_INFORMATION = 0x00;

	/** Copyright information from the map manufacturer. */
	public static final int CODE_COPYRIGHT_TEXT_STRING = 0x06;

	/**
	 * A filename that contains a BMP image to be printed along with
	 * the map.
	 */
	public static final int CODE_COPYRIGHT_BITMAP_REFERENCE = 0x07;

	/**
	 * A code that shows what kind of copyright information is
	 * contaied in this segment.
	 * The field {@link #extraProperties} can be used too as extra information.
	 */
	private final byte copyrightCode;
	private final byte whereCode;
	private final short extraProperties;
	private final String copyright;

	CopyrightSegment(StructuredInputStream ds) throws IOException {
		copyrightCode = (byte) ds.read();
		whereCode = (byte) ds.read();
		extraProperties = (short) ds.read2();
		copyright = ds.readString();
	}

	CopyrightSegment(int code, int where, String msg) {
		this.copyrightCode = (byte) code;
		this.whereCode = (byte) where;
		this.copyright = msg;
		this.extraProperties = 0;
	}

	public void write(Block block) throws IOException {
		StructuredOutputStream os = block.getOutputStream();
		os.write(copyrightCode);
		os.write(whereCode);
		os.write2(extraProperties);
		os.writeString(copyright);
	}

	public String toString() {
		return "Copyright: "
				+ copyrightCode
				+ ", where="
				+ whereCode
				+ ", extra="
				+ extraProperties
				+ ": "
				+ copyright
				;
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		CopyrightSegment that = (CopyrightSegment) o;

		if (copyrightCode != that.copyrightCode) return false;
		if (extraProperties != that.extraProperties) return false;
		if (whereCode != that.whereCode) return false;
		if (!copyright.equals(that.copyright)) return false;

		return true;
	}

	public int hashCode() {
		int result = (int) copyrightCode;
		result = 31 * result + (int) whereCode;
		result = 31 * result + (int) extraProperties;
		result = 31 * result + copyright.hashCode();
		return result;
	}
}

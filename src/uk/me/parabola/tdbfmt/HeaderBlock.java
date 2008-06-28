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
 * The header block.  Identifies the particular map set.
 *
 * @author Steve Ratcliffe
 */
class HeaderBlock {

	/** The map family. */
	private short familyId;

	/** A unique number associated with the map product */
	private short productId;

	/** The version of TDB */
	private final int tdbVersion;

	/** The series name is an overall name eg 'US Topo' */
	private String seriesName;

	/** The version number of the map product */
	private short productVersion;

	/**
	 * Identifies a map within the series
	 * @see #seriesName
	 */
	private String familyName;

	HeaderBlock(int tdbVersion) {
		this.tdbVersion = tdbVersion;
	}

	HeaderBlock(Block block) throws IOException {
		StructuredInputStream ds = block.getInputStream();

		productId = (short) ds.read2();
		/*int junk = */ds.read2();
		//assert junk == 0;
		// junk is the product id, in version 4 of the format anyway

		tdbVersion = ds.read2();
		seriesName = ds.readString();
		productVersion = (short) ds.read2();
		familyName = ds.readString();
	}

	public void write(Block block) throws IOException {
		StructuredOutputStream os = block.getOutputStream();
		os.write2(productId);
		os.write2(familyId);
		os.write2(tdbVersion);
		os.writeString(seriesName);
		os.write2(productVersion);
		os.writeString(familyName);

		if (tdbVersion >= TdbFile.TDB_V407) {
			// Unknown purpose
			byte[] buf = new byte[] {
					0, 0x12, 1,1,1,0,0,0,
					0,0,0x15,0,0,0,0,0,
					0,0,0,0,0,0,0,0,
					0,0,0,0,0,0, (byte) 0xe4,4,
					0,0,0x10,0x27,0,0,1,0,
					0
			};
			os.write(buf);
		}
	}

	public String toString() {
		return "TDB header: "
				+ productId
				+ " version="
				+ tdbVersion
				+ ", series:"
				+ seriesName
				+ ", family:"
				+ familyName
				+ ", ver="
				+ productVersion
				;
	}

	public void setProductId(short productId) {
		this.productId = productId;
	}

	public void setSeriesName(String seriesName) {
		this.seriesName = seriesName;
	}

	public void setFamilyName(String familyName) {
		this.familyName = familyName;
	}

	public void setProductVersion(short productVersion) {
		this.productVersion = productVersion;
	}

	public void setFamilyId(short familyId) {
		this.familyId = familyId;
	}


}

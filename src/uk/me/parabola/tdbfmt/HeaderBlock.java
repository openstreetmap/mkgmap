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

import java.io.IOException;

/**
 * The header block.  Identifies the particular map set.
 *
 * @author Steve Ratcliffe
 */
class HeaderBlock {
	/** A unique number associated with the map product */
	private short productId;

	/** The version of TDB */
	private int tdbVersion = 0x012c;

	/** The series name is an overall name eg 'US Topo' */
	private String seriesName;

	/** The version number of the map product */
	private short productVersion;

	/**
	 * Identifies a map within the series
	 * @see #seriesName
	 */
	private String familyName;

	HeaderBlock() {
	}

	HeaderBlock(Block block) throws IOException {
		StructuredInputStream ds = block.getInputStream();

		productId = (short) ds.read2();
		int junk = ds.read2();
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
		os.write2(0);
		os.write2(tdbVersion);
		os.writeString(seriesName);
		os.write2(productVersion);
		os.writeString(familyName);
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
}

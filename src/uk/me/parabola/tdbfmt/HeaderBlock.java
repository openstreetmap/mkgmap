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

import uk.me.parabola.io.FileBlock;
import uk.me.parabola.io.StructuredInputStream;
import uk.me.parabola.io.StructuredOutputStream;

/**
 * The header block.  Identifies the particular map set.
 *
 * @author Steve Ratcliffe
 */
class HeaderBlock extends FileBlock {
	static final int BLOCK_ID = 0x50;

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

	private byte enableProfile;

	private int codePage;

	HeaderBlock(int tdbVersion) {
		super(BLOCK_ID);
		this.tdbVersion = tdbVersion;
	}

	public HeaderBlock(StructuredInputStream ds) throws IOException {
		super(BLOCK_ID);

		productId = (short) ds.read2();
		familyId = (short) ds.read2();

		tdbVersion = ds.read2();
		seriesName = ds.readString();
		productVersion = (short) ds.read2();
		familyName = ds.readString();
	}

	/**
	 * This is to overridden in a subclass.
	 */
	protected void writeBody(StructuredOutputStream os) throws IOException {
		os.write2(productId);
		os.write2(familyId);
		os.write2(tdbVersion);
		os.writeString(seriesName);
		os.write2(productVersion);
		os.writeString(familyName);

		if (tdbVersion >= TdbFile.TDB_V407) {
			// Unknown purpose

			os.write(0);
			os.write(0x12); // lowest map level
			os.write(1);
			os.write(1);
			os.write(1);
			os.write4(0);
			os.write(0);
			os.write(0x18); // highest routable? 19 no, 21 ok
			os.write4(0);
			os.write4(0);
			os.write4(0);
			os.write4(0);
			os.write3(0);
			os.write4(codePage);
			os.write4(10000);
			os.write(1);    // map is routable
			if (enableProfile == 1)
				os.write(1);    // map has profile information
			else
				os.write(0);
			os.write(0);    // map has DEM sub files
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

	void setCodePage(int codePage) {
		this.codePage = codePage;
	}

	public int getTdbVersion() {
		return tdbVersion;
	}

	public void setEnableProfile(byte enableProfile) {
		this.enableProfile = enableProfile;		
	}
}

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

import uk.me.parabola.io.StructuredInputStream;
import uk.me.parabola.io.StructuredOutputStream;

/**
 * Details of a single .img file that is part of the map set.  There will be
 * one of these for each .img file.
 *
 * @author Steve Ratcliffe
 */
public class DetailMapBlock extends OverviewMapBlock {
	public static final int BLOCK_ID = 0x4c;

	private int tdbVersion;

	private String innername;

	// Sizes of the regions.  It is possible that rgn and tre are reversed?
	private int rgnDataSize;
	private int treDataSize;
	private int lblDataSize;
	private int netDataSize;
	private int nodDataSize;
	private int demDataSize;

	public DetailMapBlock(int tdbVersion) {
		super(BLOCK_ID);
		assert tdbVersion > 0;
		this.tdbVersion = tdbVersion;
	}

	/**
	 * Initialise this block from the raw block given.
	 *
	 * @throws IOException For io problems.
	 */
	public DetailMapBlock(StructuredInputStream ds) throws IOException {
		super(ds);

		// First there are a couple of fields that we ignore.
		int junk = ds.read2();
		assert junk == 4;

		junk = ds.read2();
		assert junk == 3;

		// Sizes of the data
		rgnDataSize = ds.read4();
		treDataSize = ds.read4();
		lblDataSize = ds.read4();

		// Another ignored field
		junk = ds.read();
		assert junk == 1;
	}

	/**
	 * Write into the given block.
	 *
	 * @throws IOException Problems writing, probably can't really happen as
	 * we use an array backed stream.
	 */
	public void writeBody(StructuredOutputStream os) throws IOException {
		super.writeBody(os);

		int n = 3;
		if (tdbVersion >= TdbFile.TDB_V407) {
			if (netDataSize > 0)
				n++;
			if (nodDataSize > 0)
				n++;
			if (demDataSize > 0)
				n++;
		}
		
		os.write2(n+1);
		os.write2(n);

		os.write4(treDataSize);
		os.write4(rgnDataSize);
		os.write4(lblDataSize);
		
		if (tdbVersion >= TdbFile.TDB_V407) {
			if (n > 3) os.write4(netDataSize);
			if (n > 4) os.write4(nodDataSize);
			if (n > 5) os.write4(demDataSize);
//01 c3 00 ff
			os.write4(0xff00c301);
			os.write(0);
			os.write(0);
			os.write(0);

			String mn = getInnername();
			os.writeString(mn + ".TRE");
			os.writeString(mn + ".RGN");
			os.writeString(mn + ".LBL");
			if (n > 3) os.writeString(mn + ".NET");
			if (n > 4) os.writeString(mn + ".NOD");
			if (n > 5) os.writeString(mn + ".DEM");
		} else {
			os.write(1);
		}
	}

	private String getInnername() {
		return innername;
	}

	public void setInnername(String innername) {
		this.innername = innername;
	}

	public void setRgnDataSize(int rgnDataSize) {
		this.rgnDataSize = rgnDataSize;
	}

	public void setTreDataSize(int treDataSize) {
		this.treDataSize = treDataSize;
	}

	public void setLblDataSize(int lblDataSize) {
		this.lblDataSize = lblDataSize;
	}

	public void setNetDataSize(int netDataSize) {
		this.netDataSize = netDataSize;
	}

	public void setNodDataSize(int nodDataSize) {
		this.nodDataSize = nodDataSize;
	}

	public void setDemDataSize(int demDataSize) {
		this.demDataSize = demDataSize;
	}

	public String toString() {
		return super.toString()
				+ ", rgn size="
				+ rgnDataSize
				+ ", tre size="
				+ treDataSize
				+ ", lbl size"
				+ lblDataSize
				;
	}

}

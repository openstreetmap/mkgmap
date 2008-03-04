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
 * Details of a single .img file that is part of the map set.  There will be
 * one of these for each .img file.
 *
 * @author Steve Ratcliffe
 */
public class DetailMapBlock extends OverviewMapBlock {

	// Sizes of the regions.  It is possible that rgn and tre are reversed?
	private int rgnDataSize;
	private int treDataSize;
	private int lblDataSize;

	public DetailMapBlock() {
	}

	/**
	 * Initialise this block from the raw block given.
	 * @param block The raw block read from the file.
	 * @throws IOException For io problems.
	 */
	public DetailMapBlock(Block block) throws IOException {
		super(block);

		StructuredInputStream ds = block.getInputStream();

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
	 * @param block The block that will have been initialised to be a detail
	 * block.
	 * @throws IOException Problems writing, probably can't really happen as
	 * we use an array backed stream.
	 */
	public void write(Block block) throws IOException {
		super.write(block);

		StructuredOutputStream os = block.getOutputStream();

		os.write2(4);
		os.write2(3);
		os.write4(rgnDataSize);
		os.write4(treDataSize);
		os.write4(lblDataSize);
		os.write(1);
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

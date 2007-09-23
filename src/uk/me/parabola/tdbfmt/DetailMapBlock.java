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

import java.io.IOException;

/**
 * Details of a single .img file that is part of the map set.  There will be
 * one of these for each .img file.
 *
 * @author Steve Ratcliffe
 */
public class DetailMapBlock extends OverviewMapBlock {
	private static final Logger log = Logger.getLogger(DetailMapBlock.class);

	// Sizes of the regions.  It is possible that rgn and tre are reversed?
	private int rgnDataSize;
	private int treDataSize;
	private int lblDataSize;

	public DetailMapBlock(Block block) throws IOException {
		super(block);

		StructuredInputStream ds = block.getInputStream();
		int junk = ds.read2();
		//log.debug("junk1 is", junk);
		assert junk == 4;

		junk = ds.read2();
		//log.debug("junk2 is", junk);
		assert junk == 3;

		rgnDataSize = ds.read4();
		treDataSize = ds.read4();
		lblDataSize = ds.read4();

		junk = ds.read();
		//log.debug("junk3 is", junk);
		assert junk == 1;
	}

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

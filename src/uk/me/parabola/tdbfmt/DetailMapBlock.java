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
 * @author Steve Ratcliffe
 */
public class DetailMapBlock extends OverviewMapBlock {
	private static final Logger log = Logger.getLogger(DetailMapBlock.class);
	
	private int rgnDataSize;
	private int treDataSize;
	private int lblDataSize;

	public DetailMapBlock(Block block) throws IOException {
		super(block);

		StructuredInputStream ds = block.getStream();
		int junk = ds.read2();
		log.debug("junk1 is", junk);
		assert junk == 4;

		junk = ds.read2();
		log.debug("junk2 is", junk);
		assert junk == 3;

		rgnDataSize = ds.read4();
		treDataSize = ds.read4();
		lblDataSize = ds.read4();

		junk = ds.read();
		log.debug("junk3 is", junk);
		assert junk == 1;
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

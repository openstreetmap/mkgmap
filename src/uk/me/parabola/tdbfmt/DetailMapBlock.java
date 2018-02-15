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
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.io.StructuredInputStream;
import uk.me.parabola.io.StructuredOutputStream;
import uk.me.parabola.mkgmap.combiners.SubFileInfo;

/**
 * Details of a single .img file that is part of the map set.  There will be
 * one of these for each .img file.
 *
 * @author Steve Ratcliffe
 */
public class DetailMapBlock extends OverviewMapBlock {
	public static final int BLOCK_ID = 0x4c;

	private int tdbVersion;

	// Sizes of the regions.
	private List<SubFileInfo> subFiles;

	public DetailMapBlock(int tdbVersion) {
		super(BLOCK_ID);

		this.tdbVersion = tdbVersion;
	}

	/**
	 * Initialise this block from the raw block given.
	 *
	 * @throws IOException For io problems.
	 */
	public DetailMapBlock(StructuredInputStream ds) throws IOException {
		super(ds);

		ds.read2(); // expected value : n + 1
		int n = ds.read2();

		// Sizes of the data
		int[] sizes = new int[n];
		for (int i = 0; i < n; i++) {
			sizes[i] = ds.read4();
		}

		// some more ignored fields
		ds.read(); 
		ds.read();
		ds.read();
		ds.read4();

		subFiles = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			String name = ds.readString();
			SubFileInfo si = new SubFileInfo(name, sizes[i]);
			subFiles.add(si);
		}
	}

	/**
	 * Write into the given block.
	 *
	 * @throws IOException Problems writing, probably can't really happen as
	 * we use an array backed stream.
	 */
	public void writeBody(StructuredOutputStream os) throws IOException {
		super.writeBody(os);

		// We do not support writing a version less than v4.07
		assert tdbVersion >= TdbFile.TDB_V407;

		int n = subFiles.size();
		os.write2(n+1);
		os.write2(n);

		for (SubFileInfo si : subFiles) {
			os.write4((int) si.getSize());
		}

//01 c3 00 ff
		os.write4(0xff00c301);
		os.write(0);
		os.write(0);
		os.write(0);

		for (SubFileInfo si : subFiles) {
			os.writeString(si.getName());
		}
	}

	public void setSubFiles(List<SubFileInfo> subFiles) {
		this.subFiles = subFiles;
	}
}

/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 26-Nov-2006
 */
package uk.me.parabola.imgfmt.sys;

import uk.me.parabola.imgfmt.fs.DirectoryEntry;

import java.util.List;
import java.util.ArrayList;
import java.nio.channels.FileChannel;
import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * The directory.  There is only one directory and it contains the
 * filenames and block information.  On disk each entry is a
 * multiple of the block size.
 *
 * @author Steve Ratcliffe
 */
class Directory {
	static private Logger log = Logger.getLogger(Directory.class);

	private int startBlock; // The starting block for the directory.
	private int blockSize;
	private int nEntries;
	private FileChannel file;

	private List<DirectoryEntry> entries = new ArrayList<DirectoryEntry>();
	private DirectoryEntryImpl specialEntry;

	public Directory(FileChannel file, int start) {
		this.file = file;
		this.startBlock = start;

	}

	/**
	 * Write out the directory to the file.  The file should be correctl
	 * positioned by the caller.
	 * @throws IOException If there is a problem writing out any
	 * of the directory entries.
	 */
	public void sync() throws IOException {
		// We need to work out the complete size of the header+directory.
//		int hsize = 2*blockSize + nEntries * blockSize; // TODO not accurate
//		specialEntry.setSize(hsize);
//		specialEntry.addBlock(0);
//		specialEntry.addBlock(1);
//		specialEntry.addBlock(2);
		for (DirectoryEntry ent : entries) {
			log.debug("wrting ent at " + file.position());
			((DirectoryEntryImpl) ent).sync(file);
		}
	}

	private void addEntry(DirectoryEntry ent) {
		nEntries++;

		// take account of the directory block as part of the header.
		specialEntry.addBlock(startBlock + nEntries - 1);
		entries.add(ent);
	}

	/**
	 * Set the block size.
	 * @param size The size which must be a power of two.
	 */
	public void setBlockSize(int size) {
		blockSize = size;
	}

	public void init() {
		// There is a special entry in the directory that covers the whole
		// of the header and the directory itself.  We have to allocate it
		// and make it cover the right part of the file.
		DirectoryEntryImpl ent = new DirectoryEntryImpl("        .   ",
				blockSize, 0);

		// Add blocks for the header before the directory.
		for (int i = 0; i < startBlock; i++)
			ent.addBlock(i);

		ent.setSpecial(true);
		specialEntry = ent;

		// Add it to this directory.
		addEntry(ent);
	}


	public int getNEntries() {
		return nEntries;
	}
}

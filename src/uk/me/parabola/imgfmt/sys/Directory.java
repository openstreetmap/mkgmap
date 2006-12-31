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
import uk.me.parabola.imgfmt.FileExistsException;

import java.util.List;
import java.util.ArrayList;
import java.nio.channels.FileChannel;
import java.io.IOException;

import uk.me.parabola.log.Logger;

/**
 * The directory.  There is only one directory and it contains the
 * filenames and block information.  On disk each entry is a
 * multiple of the block size.
 *
 * @author Steve Ratcliffe
 */
class Directory {
	private static final Logger log = Logger.getLogger(Directory.class);

	private final int startBlock; // The starting block for the directory.
	private int blockSize;
	private int nEntries;

	private final FileChannel file;

	// The list of files themselves.
	private final List<DirectoryEntry> entries = new ArrayList<DirectoryEntry>();

	// The first entry in the directory covers the header and directory itself
	// and so is special.
	private Dirent specialEntry;

	Directory(FileChannel file, int start) {
		this.file = file;
		this.startBlock = start;

	}

	/**
	 * Create a new file in the directory.
	 * 
	 * @param name The file name.  Must be 8+3 characters.
	 * @return The new directory entity.
	 * @throws FileExistsException If the entry already
	 * exists.
	 */
	Dirent create(String name) throws FileExistsException {
		for (DirectoryEntry e : entries) {
			String name2 = e.getName() + '.' + e.getExt();
			if (name.equals(name2)) {
				throw new FileExistsException("File " + name + " exists");
			}
		}

		Dirent ent = new Dirent(name, blockSize);
		addEntry(ent);

		return ent;
	}

	/**
	 * Write out the directory to the file.  The file should be correctly
	 * positioned by the caller.
	 *
	 * @throws IOException If there is a problem writing out any
	 * of the directory entries.
	 */
	public void sync() throws IOException {
		file.position((long) startBlock * blockSize);
		for (DirectoryEntry ent : entries) {
			log.debug("wrting ent at " + file.position());
			((Dirent) ent).sync(file);
		}
	}

	/**
	 * Set the block size.
	 * @param size The size which must be a power of two.
	 */
	public void setBlockSize(int size) {
		blockSize = size;
	}

	/**
	 * Initialise the directory.
	 */
	public void init() {

		// There is a special entry in the directory that covers the whole
		// of the header and the directory itself.  We have to allocate it
		// and make it cover the right part of the file.
		Dirent ent = new Dirent("        .   ", blockSize);

		// Add blocks for the header before the directory.
		for (int i = 0; i < startBlock; i++)
			ent.addFullBlock(i);

		ent.setSpecial(true);
		specialEntry = ent;

		// Add it to this directory.
		addEntry(ent);
	}

	/**
	 * Add an entry to the directory. This updates the header block allocation
	 * too.
	 *
	 * @param ent The entry to add.
	 */
	private void addEntry(DirectoryEntry ent) {
		nEntries++;

		// take account of the directory block as part of the header.
		specialEntry.addFullBlock(startBlock + nEntries - 1);
		entries.add(ent);
	}


}

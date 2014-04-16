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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The directory.  There is only one directory and it contains the
 * file names and block information.  On disk each entry is a
 * multiple of the block size.
 *
 * @author Steve Ratcliffe
 */
class Directory {
	private static final Logger log = Logger.getLogger(Directory.class);

	//private final FileChannel file;
	private ImgChannel chan;

	private final BlockManager headerBlockManager;
	private final int startEntry;
	private long startPos;

	// The list of files themselves.
	private final Map<String, DirectoryEntry> entries = new LinkedHashMap<String, DirectoryEntry>();

	Directory(BlockManager headerBlockManager, int startEntry) {
		this.headerBlockManager = headerBlockManager;
		this.startEntry = startEntry;
	}

	/**
	 * Create a new file in the directory.
	 * 
	 * @param name The file name.  Must be 8+3 characters.
	 * @param blockManager To allocate blocks for the created file entry.
	 * @return The new directory entity.
	 * @throws FileExistsException If the entry already
	 * exists.
	 */
	Dirent create(String name, BlockManager blockManager) throws FileExistsException {

		// Check to see if it is already there.
		if (entries.get(name) != null)
			throw new FileExistsException("File " + name + " already exists");

		Dirent ent;
		if (name.equals(ImgFS.DIRECTORY_FILE_NAME)) {
			ent = new HeaderDirent(name, blockManager);
		} else {
			ent = new Dirent(name, blockManager);
		}
		addEntry(ent);

		return ent;
	}

	/**
	 * Initialise the directory for reading the file.  The whole directory
	 * is read in.
	 *
	 * @throws IOException If it cannot be read.
	 */
	void readInit(byte xorByte) throws IOException {
		assert chan != null;

		ByteBuffer buf = ByteBuffer.allocate(512);
		buf.order(ByteOrder.LITTLE_ENDIAN);

		chan.position(startPos);
		Dirent current = null;
		while ((chan.read(buf)) > 0) {
			buf.flip();
			if(xorByte != 0) {
				byte[] bufBytes = buf.array();
				for(int i = 0; i < bufBytes.length; ++i)
					bufBytes[i] ^= xorByte;
			}

			int used = buf.get(Dirent.OFF_FILE_USED);
			if (used != 1)
				continue;

			String name = Utils.bytesToString(buf, Dirent.OFF_NAME, Dirent.MAX_FILE_LEN);
			String ext = Utils.bytesToString(buf, Dirent.OFF_EXT, Dirent.MAX_EXT_LEN);

			log.debug("readinit name", name, ext);

			int flag = buf.get(Dirent.OFF_FLAG);
			int part = buf.get(Dirent.OFF_FILE_PART) & 0xff;

			if (flag == 3 && current == null) {
				current = (Dirent) entries.get(ImgFS.DIRECTORY_FILE_NAME);
				current.initBlocks(buf);
			} else if (part == 0) {
				current = create(name + '.' + ext, headerBlockManager);
				current.initBlocks(buf);
			} else {
				assert current != null;
				current.initBlocks(buf);
			}
			buf.clear();
		}
	}


	/**
	 * Write out the directory to the file.  The file should be correctly
	 * positioned by the caller.
	 *
	 * @throws IOException If there is a problem writing out any
	 * of the directory entries.
	 */
	public void sync() throws IOException {

		// The first entry can't really be written until the rest of the directory is
		// so we have to step through once to calculate the size and then again
		// to write it out.
		int headerEntries = 0;
		for (DirectoryEntry dir : entries.values()) {
			Dirent ent = (Dirent) dir;
			log.debug("ent size", ent.getSize());
			int n = ent.numberHeaderBlocks();
			headerEntries += n;
		}

		// Save the current position
		long dirPosition = chan.position();
		int blockSize = headerBlockManager.getBlockSize();

		// Get the number of blocks required for the directory entry representing the header.
		// First calculate the number of blocks required for the directory entries.
		int headerBlocks = (int) Math.ceil((startEntry + 1.0 + headerEntries) * DirectoryEntry.ENTRY_SIZE / blockSize);
		int forHeader = (headerBlocks + DirectoryEntry.ENTRY_SIZE - 1)/DirectoryEntry.ENTRY_SIZE;

		log.debug("header blocks needed", forHeader);

		// There is nothing really wrong with larger values (perhaps, I don't
		// know for sure!) but the code is written to make it 1, so make sure that it is.
		assert forHeader == 1;

		// Write the blocks that will will contain the header blocks.
		chan.position(dirPosition + (long) forHeader * DirectoryEntry.ENTRY_SIZE);

		for (DirectoryEntry dir : entries.values()) {
			Dirent ent = (Dirent) dir;

			if (!ent.isSpecial()) {
				log.debug("wrting ", dir.getFullName(), " at ", chan.position());
				log.debug("ent size", ent.getSize());
				ent.sync(chan);
			}
		}

		long end = (long) blockSize * headerBlockManager.getMaxBlock();
		ByteBuffer buf = ByteBuffer.allocate((int) (end - chan.position()));
		for (int i = 0; i < buf.capacity(); i++)
			buf.put((byte) 0);
		buf.flip();
		chan.write(buf);

		// Now go back and write in the directory entry for the header.
		chan.position(dirPosition);
		Dirent ent = (Dirent) entries.values().iterator().next();
		log.debug("ent header size", ent.getSize());
		ent.sync(chan);

	}

	/**
	 * Get the entries. Used for listing the directory.
	 *
	 * @return A list of the directory entries.  They will be in the same
	 * order as in the file.
	 */
	public List<DirectoryEntry> getEntries() {
		return new ArrayList<DirectoryEntry>(entries.values());
	}

	/**
	 * Add an entry to the directory.
	 *
	 * @param ent The entry to add.
	 */
	private void addEntry(DirectoryEntry ent) {
		entries.put(ent.getFullName(), ent);
	}

	public void setFile(ImgChannel chan) {
		this.chan = chan;
	}

	public void setStartPos(long startPos) {
		this.startPos = startPos;
	}

	public DirectoryEntry lookup(String name) {
		return entries.get(name);
	}
}

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

import org.apache.log4j.Logger;

import java.nio.channels.FileChannel;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import uk.me.parabola.imgfmt.fs.FSOps;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;

/**
 * The img file is really a filesystem containing several files.
 * It is made up of a header, a directory area and a data area which
 * occur in the filesystem in that order.
 *
 * @author steve
 */
public class FileSystem implements FSOps {
	static protected Logger log = Logger.getLogger(FileSystem.class);
	
	private FileChannel file;
	private RandomAccessFile rafile;

	private int blockSize;

	// A file system consists of a header, a directory and a data area.
	private ImgHeader header;

	private Directory directory;

	public FileSystem(String filename) throws FileNotFoundException {

		rafile = new RandomAccessFile(filename, "rw");

		file = rafile.getChannel();

		header = new ImgHeader(file);
		header.setCreationTime(new Date());
		header.setDescription("hello world this is a test of desc");
	}


	/**
	 * Open a file.  The returned file object can be used to read and write the
	 * underlying file.
	 *
	 * @param name The file name to open.
	 * @param mode Either "r" for read access, "w" for write access or "rw"
	 *             for both read and write.
	 * @return A file descriptor.
	 * @throws java.io.FileNotFoundException When the file does not exist.
	 */
	public uk.me.parabola.imgfmt.fs.FileChannel open(String name, String mode) throws FileNotFoundException {
		return null;
	}

	/**
	 * Lookup the file and return a directory entry for it.
	 *
	 * @param name The filename to look up.
	 * @return A directory entry.
	 * @throws java.io.IOException If an error occurs reading the directory.
	 */
	public DirectoryEntry lookup(String name) throws IOException {
		return null;
	}

	/**
	 * List all the files in the directory.
	 *
	 * @return A List of directory entries.
	 * @throws java.io.IOException If an error occurs reading the directory.
	 */
	public List list() throws IOException {
		return null;
	}

	/**
	 * Sync with the underlying file.  All unwritten data is written out to
	 * the underlying file.
	 *
	 * @throws java.io.IOException If an error occurs during the write.
	 */
	public void sync() throws IOException {
	}

	/**
	 * Close the filesystem.  Any saved data is flushed out.  It is better
	 * to explicitly sync the data out first, to be sure that it has worked.
	 */
	public void close() {
	}

	public void setBlockSize(int blockSize) {
		this.blockSize = blockSize;
	}
}

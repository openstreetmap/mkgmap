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

import uk.me.parabola.log.Logger;

import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.nio.channels.FileChannel;

import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.FileExistsException;

/**
 * The img file is really a filesystem containing several files.
 * It is made up of a header, a directory area and a data area which
 * occur in the filesystem in that order.
 *
 * @author steve
 */
public class ImgFS implements FileSystem {
	private static final Logger log = Logger.getLogger(ImgFS.class);

	private int blockSize = 512;

	private FileChannel file;

	// A file system consists of a header, a directory and a data area.
	private ImgHeader header;

	// There is only one directory that holds all filename and block allocation
	// information.
	private Directory directory;

	// The filesystem is responsible for allocating blocks
	private BlockManager blockManager;

	/**
	 * Create an IMG file from its external filesystem name and optionally some
	 * parameters.
	 *
	 * @param filename The filename eg 'gmapsupp.img'
	 * @param params File system parameters.  Can be null.
	 * @throws FileNotFoundException If the file cannot be created.
	 */
	public ImgFS(String filename, FileSystemParam params)
			throws FileNotFoundException
	{
		log.info("Creating file system");
		RandomAccessFile rafile = new RandomAccessFile(filename, "rw");
		try {
			// Set length to zero because if you don't you can get a
			// map that doesn't work.  Not clear why.
			rafile.setLength(0);
		} catch (IOException e) {
			// It doesn't matter that much.
			log.warn("Could not set file length to zero");
		}

		file = rafile.getChannel();

		header = new ImgHeader(file);
		header.setDirectoryStartBlock(2); // could be from params

		// Set the times.
		Date date = new Date();
		header.setCreationTime(date);
		header.setUpdateTime(date);

		// The block manager allocates blocks for files.
		blockManager = new BlockManager(blockSize,
				header.getDirectoryStartBlock());

		directory = new Directory(file, blockManager);

		if (params != null)
			setParams(params);

		// Initialise the directory.
		directory.init();
	}


	/**
	 * Set various parameters of the file system.  Anything that
	 * has not been set in <tt>params</tt> (ie is zero or null)
	 * will not have any effect.
	 *
	 * @param params A set of parameters.
	 */
	private void setParams(FileSystemParam params) {
		int bs = params.getBlockSize();
		if (bs > 0) {
			blockSize = bs;
			header.setBlockSize(bs);
			directory.setBlockSize(bs);
		}

		String mapdesc = params.getMapDescription();
		if (mapdesc != null)
			header.setDescription(mapdesc);
	}

	/**
	 * Create a new file it must not allready exist.
	 *
	 * @param name The file name.
	 * @return A directory entry for the new file.
	 */
	public ImgChannel create(String name) throws FileExistsException {
		Dirent dir = directory.create(name);

		FileNode f = new FileNode(file, blockManager, dir, "w");
		return f;
	}

	/**
	 * Open a file.  The returned file object can be used to read and write the
	 * underlying file.
	 *
	 * @param name The file name to open.
	 * @param mode Either "r" for read access, "w" for write access or "rw"
	 *             for both read and write.
	 * @return A file descriptor.
	 * @throws FileNotFoundException When the file does not exist.
	 */
	public ImgChannel open(String name, String mode) throws FileNotFoundException {
		if (name == null || mode == null)
			throw new IllegalArgumentException("null argument");

		// Its wrong to do this as this routine should not throw an exception
		// when the file exists.  Needs lookup().
//		if (mode.indexOf('w') >= 0)
//			return create(name);

		throw new FileNotFoundException("File not found because it isn't implemented yet");
	}

	/**
	 * Lookup the file and return a directory entry for it.
	 *
	 * @param name The filename to look up.
	 * @return A directory entry.
	 * @throws IOException If an error occurs reading the directory.
	 */
	public DirectoryEntry lookup(String name) throws IOException {
		if (name == null)
			throw new IllegalArgumentException("null name argument");

		throw new IOException("not implemented");
	}

	/**
	 * List all the files in the directory.
	 *
	 * @return A List of directory entries.
	 * @throws IOException If an error occurs reading the directory.
	 */
	public List<DirectoryEntry> list() throws IOException {
		throw new IOException("not implemented yet");
	}

	/**
	 * Sync with the underlying file.  All unwritten data is written out to
	 * the underlying file.
	 *
	 * @throws IOException If an error occurs during the write.
	 */
	public void sync() throws IOException {
		header.sync();

		file.position((long) header.getDirectoryStartBlock() * header.getBlockSize());
		directory.sync();
	}

	/**
	 * Close the filesystem.  Any saved data is flushed out.  It is better
	 * to explicitly sync the data out first, to be sure that it has worked.
	 */
	public void close() {
		try {
			sync();
		} catch (IOException e) {
			log.debug("could not sync filesystem");
		}
	}
}

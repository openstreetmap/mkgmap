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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

import static uk.me.parabola.imgfmt.fs.DirectoryEntry.ENTRY_SIZE;
import static uk.me.parabola.imgfmt.fs.DirectoryEntry.SLOTS_PER_ENTRY;

/**
 * The img file is really a filesystem containing several files.
 * It is made up of a header, a directory area and a data area which
 * occur in the filesystem in that order.
 *
 * @author steve
 */
public class ImgFS implements FileSystem {
	private static final Logger log = Logger.getLogger(ImgFS.class);

	// The directory is just like any other file, but with a name of 8+3 spaces
	static final String DIRECTORY_FILE_NAME = "        .   ";

	// This is the read or write channel to the real file system.
	private final FileChannel file;
	private boolean readOnly = true;

	// The header contains general information.
	private ImgHeader header;
	private FileSystemParam fsparam;

	// There is only one directory that holds all filename and block allocation
	// information.
	private Directory directory;

	// The filesystem is responsible for allocating blocks
	private BlockManager fileBlockManager;

	// The header entries are written in 512 blocks, regardless of the block size of the file itself.
	private static final long ENTRY_BLOCK_SIZE = 512L;
	private BlockManager headerBlockManager;

	// Open files for write
	private final List<FileNode> openNodes = new ArrayList<>();

	// if non-zero, all bytes are XORed with this
	private byte xorByte;

	/**
	 * Private constructor, use the static {@link #createFs} and {@link #openFs}
	 * routines to make a filesystem.
	 *
	 * @param chan The open file.
	 */
	private ImgFS(FileChannel chan) {
		file = chan;
	}

	/**
	 * Create an IMG file from its external filesystem name and optionally some
	 * parameters.
	 *
	 * @param filename The name of the file to be created.
	 * @param params File system parameters.  Can not be null.
	 * @throws FileNotWritableException If the file can not be written to.
	 */
	public static FileSystem createFs(String filename, FileSystemParam params) throws FileNotWritableException {
		params.setFilename(filename);
		try {
			RandomAccessFile rafile = new RandomAccessFile(filename, "rw");
			return createFs(rafile.getChannel(), params);
		} catch (FileNotFoundException e) {
			throw new FileNotWritableException("Could not create file: " + params.getFilename(), e);
		}
	}

	private static FileSystem createFs(FileChannel chan, FileSystemParam params)
			throws FileNotWritableException
	{
		assert params != null;

		// Truncate the file, because extra bytes beyond the end make for a
		// map that doesn't work on the GPS (although its likely to work in
		// other software viewers).
		try {
			chan.truncate(0);
		} catch (IOException e) {
			throw new FileNotWritableException("Failed to truncate file", e);
		}

		ImgFS fs = new ImgFS(chan);
		fs.createInitFS(chan, params);

		return fs;
	}

	/**
	 * Open an existing IMG file system.
	 * @param name The file name to open.
	 * @return A File system that can be used lookup the internal files.
	 * @throws FileNotFoundException When the file doesn't exist or can't be
	 * read.
	 */
	public static FileSystem openFs(String name) throws FileNotFoundException {
		RandomAccessFile rafile = new RandomAccessFile(name, "r");
		return openFs(name, rafile.getChannel());
	}

	private static FileSystem openFs(String name, FileChannel chan) throws FileNotFoundException {
		ImgFS fs = new ImgFS(chan);

		try {
			fs.readInitFS(chan);
		} catch (IOException e) {
			throw new FileNotFoundException(name + ": " + e.getMessage());
		}

		return fs;
	}

	/**
	 * Create a new file, it must not already exist.
	 *
	 * @param name The file name.
	 * @return A directory entry for the new file.
	 */
	public ImgChannel create(String name) throws FileExistsException {
		Dirent dir = directory.create(name, fileBlockManager);

		FileNode node = new FileNode(file, dir, "w");
		openNodes.add(node);
		return node;
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

		if (mode.indexOf('r') >= 0) {
			Dirent ent = internalLookup(name);

			FileNode fn = new FileNode(file, ent, "r");
			if(xorByte != 0)
				fn.setXorByte(xorByte);
			return fn;
		} else if (mode.indexOf('w') >= 0) {
			Dirent ent;
			try {
				ent = internalLookup(name);
			} catch (FileNotFoundException e) {
				try {
					ent = directory.create(name, fileBlockManager);
				} catch (FileExistsException e1) {
					// This shouldn't happen as we have just checked.
					throw new FileNotFoundException("Attempt to duplicate a file name");
				}
			}
			FileNode node = new FileNode(file, ent, "w");
			openNodes.add(node);
			return node;
		} else {
			throw new IllegalArgumentException("Invalid mode given");
		}
	}

	/**
	 * Lookup the file and return a directory entry for it.
	 *
	 * @param name The filename to look up.
	 * @return A directory entry.
	 * @throws FileNotFoundException If an error occurs looking for the file,
	 * including it not existing.
	 */
	public DirectoryEntry lookup(String name) throws FileNotFoundException {
		return internalLookup(name);
	}

	/**
	 * List all the files in the directory.
	 *
	 * @return A List of directory entries.
	 */
	public List<DirectoryEntry> list()  {
		return directory.getEntries();
	}

	/**
	 * Sync with the underlying file.  All unwritten data is written out to
	 * the underlying file.
	 *
	 * @throws IOException If an error occurs during the write.
	 */
	public void sync() throws IOException {
		if (readOnly)
			return;

		assert fileBlockManager.getMaxBlockAllocated() == 0;

		FileSystemParam param = fsparam;
		int totalBlocks = calcBlockParam(param);

		fileBlockManager.setBlockSize(param.getBlockSize());
		headerBlockManager.setBlockSize(param.getBlockSize());
		file.position(param.getReservedDirectoryBlocks() * param.getBlockSize());

		fileBlockManager.setCurrentBlock(param.getReservedDirectoryBlocks());
		for (FileNode n : openNodes) {
			n.close();
		}

		header.createHeader(param);
		header.setNumBlocks(totalBlocks);
		header.sync();
		directory.sync();
	}

	/**
	 * Calculate the block size and related parameters.
	 *
	 * We need to know the block size and the size of the directory before writing any of the files.
	 */
	private int calcBlockParam(FileSystemParam param) {
		int bestBlockSize = 0;
		int reserved = 0;
		int sizeInBlocks = 0;
		int bestSize = Integer.MAX_VALUE;

		for (int blockSize = param.getBlockSize(); blockSize < (1 << 24); blockSize <<= 1) {
			int headerSlotsRequired = 1;  // for the top level directory entry
			int fileBlocks = 0;

			for (FileNode fn : openNodes) {
				long len = fn.getSize();

				// Blocks required for this file
				int nBlocks = (int) ((len + blockSize - 1) / blockSize);
				fileBlocks += nBlocks;

				// Now we calculate how many directory blocks we need, you have
				// to round up as files do not share directory blocks.
				headerSlotsRequired += (nBlocks + SLOTS_PER_ENTRY - 1) / SLOTS_PER_ENTRY;
			}

			// Header blocks include the blocks before the directory.
			int requiredSlots = param.getDirectoryStartEntry() + headerSlotsRequired;
			int headerBlocks = (requiredSlots * ENTRY_SIZE + blockSize - 1) / blockSize;
			int totalBlocks = headerBlocks + fileBlocks;
			int size = totalBlocks * blockSize;
			log.info("bs=%d, whole size=%d, hb=%d, fb=%d, blocks=%d\n", blockSize, size,
					headerBlocks, fileBlocks, totalBlocks);

			if (totalBlocks > 0xfffe)
				continue;

			if (size > bestSize)
				break;

			bestBlockSize = blockSize;
			reserved = headerBlocks;
			sizeInBlocks = fileBlocks + headerBlocks;
			bestSize = size;
		}
		log.info("Best block size: %d sizeInBlocks=%d, reserved=%d\n", bestBlockSize, sizeInBlocks, reserved);

		param.setBlockSize(bestBlockSize);
		param.setReservedDirectoryBlocks(reserved);
		return sizeInBlocks;
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
		} finally {
			try {
				file.close();
			} catch (IOException e) {
				log.warn("Could not close file");
			}
		}
	}

	public FileSystemParam fsparam() {
		return fsparam;
	}

	/**
	 * Set up and ImgFS that has just been created.
	 *
	 * @param chan The real underlying file to write to.
	 * @param params The file system parameters.
	 * @throws FileNotWritableException If the file cannot be written for any
	 * reason.
	 */
	private void createInitFS(FileChannel chan, FileSystemParam params) throws FileNotWritableException {
		readOnly = false;

		this.fsparam = params;

		// The block manager allocates blocks for files.
		headerBlockManager = new BlockManager(params.getBlockSize(), 0);
		headerBlockManager.setMaxBlock(params.getReservedDirectoryBlocks());

		// This bit is tricky.  We want to use a regular ImgChannel to write
		// to the header and directory, but to create one normally would involve
		// it already existing, so it is created by hand.
		try {
			directory = new Directory(headerBlockManager, params.getDirectoryStartEntry());

			Dirent ent = directory.create(DIRECTORY_FILE_NAME, headerBlockManager);
			ent.setSpecial(true);
			ent.setInitialized(true);

			FileNode f = new FileNode(chan, ent, "w");

			directory.setFile(f);
			header = new ImgHeader(f);
			header.createHeader(params);
		} catch (FileExistsException e) {
			throw new FileNotWritableException("Could not create img file directory", e);
		}

		fileBlockManager = new BlockManager(params.getBlockSize(), params.getReservedDirectoryBlocks());

		assert header != null;
	}

	/**
	 * Initialise a filesystem that is going to be read from.  We need to read
	 * in the header including directory.
	 *
	 * @param chan The file channel to read from.
	 * @throws IOException If the file cannot be read.
	 */
	private void readInitFS(FileChannel chan) throws IOException {
		ByteBuffer headerBuf = ByteBuffer.allocate(512);
		headerBuf.order(ByteOrder.LITTLE_ENDIAN);
		chan.read(headerBuf);
		xorByte = headerBuf.get(0);
		if(xorByte != 0) {
			byte[] headerBytes = headerBuf.array();
			for(int i = 0; i < headerBytes.length; ++i)
				headerBytes[i] ^= xorByte;
		}

		if (headerBuf.position() < 512)
			throw new IOException("File too short or corrupted");

		header = new ImgHeader(null);
		header.setHeader(headerBuf);
		fsparam = header.getParams();

		BlockManager headerBlockManager = new BlockManager(fsparam.getBlockSize(), 0);
		headerBlockManager.setMaxBlock(fsparam.getReservedDirectoryBlocks());

		directory = new Directory(headerBlockManager, fsparam.getDirectoryStartEntry());
		directory.setStartPos(fsparam.getDirectoryStartEntry() * ENTRY_BLOCK_SIZE);

		Dirent ent = directory.create(DIRECTORY_FILE_NAME, headerBlockManager);
		FileNode f = new FileNode(chan, ent, "r");

		header.setFile(f);
		directory.setFile(f);
		directory.readInit(xorByte);
	}

	/**
	 * Lookup the file and return a directory entry for it.
	 *
	 * @param name The filename to look up.
	 * @return A directory entry.
	 * @throws FileNotFoundException If an error occurs reading the directory.
	 */
	private Dirent internalLookup(String name) throws FileNotFoundException {
		if (name == null)
			throw new IllegalArgumentException("null name argument");

		Dirent ent = (Dirent) directory.lookup(name);
		if (ent == null)
			throw new FileNotFoundException(name + " not found");

		return ent;
	}
}

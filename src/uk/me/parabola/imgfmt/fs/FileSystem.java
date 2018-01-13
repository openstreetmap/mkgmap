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
package uk.me.parabola.imgfmt.fs;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileSystemParam;


/**
 * File system operations.
 *
 * @author Steve Ratcliffe
 */
public interface FileSystem extends Closeable {

	/**
	 * Create a new file it must not already exist.
	 * @param name The file name.
	 * @return A directory entry for the new file.
	 * @throws FileExistsException If the file already exists.
	 */
	public ImgChannel create(String name) throws FileExistsException;

	/**
	 * Open a file.  The returned file object can be used to read and write the
	 * underlying file.
	 *
	 * @param name The file name to open.
	 * @param mode Either "r" for read access, "w" for write access or "rw"
	 * for both read and write.
	 * @return A file descriptor.
	 * @throws FileNotFoundException When the file does not exist.
	 */
	public ImgChannel open(String name, String mode)
			throws FileNotFoundException;

	/**
	 * Lookup the file and return a directory entry for it.
	 *
	 * @param name The filename to look up.
	 * @return A directory entry.
	 * @throws IOException If an error occurs reading the directory.
	 */
	public DirectoryEntry lookup(String name) throws IOException;

	/**
	 * List all the files in the directory.
	 * @return A List of directory entries.
	 */
	public List<DirectoryEntry> list() ;

	/**
	 * Get the filesystem / archive parameters.  Things that are stored in
	 * the header.
	 *
	 * @return The filesystem parameters.
	 */
	public FileSystemParam fsparam();

	/**
	 * Sync with the underlying file.  All unwritten data is written out to
	 * the underlying file.
	 * @throws IOException If an error occurs during the write.
	 */
	public void sync() throws IOException;

	/**
	 * Close the filesystem.  Any saved data is flushed out.  It is better
	 * to explicitly sync the data out first, to be sure that it has worked.
	 */
	void close();
}

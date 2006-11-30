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
package uk.me.parabola.mkosmgmap.img;

import org.apache.log4j.Logger;

import java.nio.channels.FileChannel;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

/**
 * Date: 26-Nov-2006 20:58:37
 *
 * @author steve
 */
public class FileSystem {
	static protected Logger log = Logger.getLogger(FileSystem.class);
	
	private FileChannel file;
	private RandomAccessFile rafile;

	private int blockSize;
	
	private ImgHeader header;


	public FileSystem(String filename) throws FileNotFoundException {

		rafile = new RandomAccessFile(filename, "rw");

		file = rafile.getChannel();

		header = new ImgHeader(file);
		header.setCreationTime(new Date());
		header.setDescription("hello world this is a test of desc");
	}

	public void close() throws IOException {
		header.sync();
		try {
			file.close();
		} catch (IOException e) {
			log.warn("Close failed");
		}
	}
}

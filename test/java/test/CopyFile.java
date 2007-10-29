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
 * Create date: 20-Oct-2007
 */
package test;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.log.Logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * A quick program to copy all of the files out of a .img into separate
 * file system files and to create a new .img file based on the files.
 * This is a prototype of a program to create a gmapsupp for example.
 *
 * @author Steve Ratcliffe
 */
public class CopyFile {
	private static final Logger log = Logger.getLogger(CopyFile.class);

	private Map<String, Integer> files = new LinkedHashMap<String, Integer>();
	private static final int ENTRY_SIZE = 240;

	public static void main(String[] args) throws IOException {
		//FileSystemParam params = new FileSystemParam();
		String file = "63240001.img";
		if (args.length > 0)
			file = args[0];

		(new CopyFile()).mainprog(file);
	}

	private void mainprog(String file) throws IOException {


		FileSystem fs = ImgFS.openFs(file);
		lister(fs);

		BlockInfo bi = calcBlockSize();
		int blockSize = bi.blockSize;
		int reserved = 2 + bi.reserveBlocks + bi.headerSlots;
		log.debug("bs of", blockSize, "reserving", reserved);

		// Create this file, containing all the sub files
		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(blockSize);
		params.setMapDescription("output file");
		params.setDirectoryStartBlock(2);
		params.setReservedDirectoryBlocks(reserved*512/blockSize);
		FileSystem outfs = ImgFS.createFs("outimg.img", params);
		try {
			for (String name : files.keySet()) {
				if (name.startsWith("        "))
					continue;

				ImgChannel fin = fs.open(name, "r");
				ImgChannel fout = outfs.create(name);
				ByteBuffer buf = ByteBuffer.allocate(1024);
				try {
					while (fin.read(buf) > 0) {
						buf.flip();
						fout.write(buf);
						buf.compact();
					}
				} finally {
					fout.close();
					fin.close();
				}
				
			}
		} finally {
			outfs.close();
			fs.close();
		}
	}

	private static class BlockInfo {
		private int blockSize;
		private int headerSlots;
		private int reserveBlocks;

		private BlockInfo(int blockSize, int headerSlots, int reserveBlocks) {
			this.blockSize = blockSize;
			this.headerSlots = headerSlots;
			this.reserveBlocks = reserveBlocks;
		}
	}

	private BlockInfo calcBlockSize() {
		int[] ints = {
				1<<9,
				1<<10,
				1<<11,
				1<<12,
				1<<13,
				1<<14,
				1<<15,
				1<<16,
				1<<17,
				1<<18,
		};

		int resultBlockSize = 512;
		for (int bs : ints) {
			int totHeaderSlots = 0;
			for (int s : files.values()) {
				// Each file will take up at least one directory block.
				// Each directory block can hold 480 block-references
				int n = (s + (bs-1))/bs;
				log.debug("number of blocks", n, " for", s, "with bs=", bs);

				int hslots = (n+ENTRY_SIZE-1) / ENTRY_SIZE;
				log.debug("header slot", hslots);
				totHeaderSlots += hslots;
			}

			totHeaderSlots += 2;
			int totBlocks = totHeaderSlots*512/bs;
			log.info("total blocks for", bs, "is", totBlocks, "based on slots=", totHeaderSlots);

			if (totBlocks <= ENTRY_SIZE) {
				resultBlockSize = bs;
				return new BlockInfo(resultBlockSize, totHeaderSlots,
						totHeaderSlots/resultBlockSize+1);
			}
		}
		throw new IllegalArgumentException("hmm");
	}

	private void lister(FileSystem fs) throws IOException {
		List<DirectoryEntry> entries = fs.list();
		for (DirectoryEntry ent : entries) {
			String fullname = ent.getFullName();
			System.out.format("Copying %-15s %d\n", fullname, ent.getSize());

			files.put(fullname, ent.getSize());

			ImgChannel f = fs.open(fullname, "r");
			try {
				//copyToFile(f, fullname);
			} finally {
				f.close();
			}
		}
	}

	private void copyToFile(ImgChannel f, String name) {
		FileOutputStream os = null;
		ByteBuffer buf = ByteBuffer.allocate(8 * 1024);
		try {
			os = new FileOutputStream(name);
			FileChannel outchan = os.getChannel();
			while (f.read(buf) > 0) {
				buf.flip();
				outchan.write(buf);
				buf.compact();
			}
		} catch (FileNotFoundException e) {
			log.warn("Could not create file");
		} catch (IOException e) {
			log.warn("Failed during copy");
		} finally {
			if (os != null)
				try { os.close(); }
				catch (IOException e) { /* nothing */ }
		}
	}
}
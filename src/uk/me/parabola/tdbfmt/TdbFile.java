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

import uk.me.parabola.log.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The TDB file.  See the package documentation for more details.
 *
 * @author Steve Ratcliffe
 */
public class TdbFile {
	private static final Logger log = Logger.getLogger(TdbFile.class);
	private HeaderBlock headerBlock;
	private CopyrightBlock copyrightBlock;
	private OverviewMapBlock overviewMapBlock;
	private final List<DetailMapBlock> detailBlocks = new ArrayList<DetailMapBlock>();
	private static final int BLOCK_HEADER = 0x50;
	private static final int BLOCK_COPYRIGHT = 0x44;
	private static final int BLOCK_OVERVIEW = 0x42;
	private static final int BLOCK_DETAIL = 0x4c;


	private TdbFile() {
	}

	/**
	 * Read in a TDB file from the disk.
	 *
	 * @param name The file name to load.
	 * @return A TdbFile instance.
	 * @throws IOException For problems reading the file.
	 */
	public static TdbFile read(String name) throws IOException {
		TdbFile tdb = new TdbFile();

		InputStream is = new BufferedInputStream(new FileInputStream(name));

		try {
			StructuredInputStream ds = new StructuredInputStream(is);
			tdb.load(ds, name);
		} finally {
			is.close();
		}

		return tdb;
	}

	public void write(String name) throws IOException {
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(name));

		try {
			Block block = new Block(BLOCK_HEADER);
			headerBlock.write(block);
			block.write(stream);

			block = new Block(BLOCK_COPYRIGHT);
			copyrightBlock.write(block);
			block.write(stream);

			block = new Block(BLOCK_OVERVIEW);
			overviewMapBlock.write(block);
			block.write(stream);

			for (DetailMapBlock detail : detailBlocks) {
				block = new Block(BLOCK_DETAIL);
				detail.write(block);
				block.write(stream);
			}
		} finally {
			stream.close();
		}
	}

	/**
	 * Load from the given file name.
	 *
	 * @param name The file name to load from.
	 * @param ds The stream to read from.
	 * @throws IOException For problems reading the file.
	 */
	private void load(StructuredInputStream ds, String name) throws IOException {


		while (!ds.testEof()) {
			Block block = readBlock(ds);
			log.info("block", block.getBlockId(), ", len=", block.getBlockLength());
			switch (block.getBlockId()) {
			case BLOCK_HEADER:
				headerBlock = new HeaderBlock(block);
				log.info("header block seen", headerBlock);
				break;
			case BLOCK_COPYRIGHT:
				log.info("copyright block");
				copyrightBlock = new CopyrightBlock(block);
				break;
			case BLOCK_OVERVIEW:
				overviewMapBlock = new OverviewMapBlock(block);
				log.info("overview block", overviewMapBlock);
				break;
			case BLOCK_DETAIL:
				DetailMapBlock db = new DetailMapBlock(block);
				log.info("detail block", db);
				detailBlocks.add(db);
				break;
			default:
				log.warn("Unknown block in tdb file");
				break;
			}
		}

	}

	/**
	 * The file is divided into blocks.  This reads a single block.
	 *
	 * @param is The input stream.
	 * @return A block from the file.
	 * @throws IOException For problems reading the file.
	 */
	private Block readBlock(StructuredInputStream is) throws IOException {
		int blockType = is.read();
		if (blockType == -1)
			throw new EndOfFileException();
		int blockLength = is.read2();

		byte[] body = new byte[blockLength];
		int n = is.read(body);
		if (n < 0)
			throw new IOException("failed to read block");

		Block block = new Block(blockType, body);
		return block;
	}

}

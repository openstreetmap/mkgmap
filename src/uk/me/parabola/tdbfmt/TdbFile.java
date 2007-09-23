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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
	private List<DetailMapBlock> detailBlocks = new ArrayList<DetailMapBlock>();


	private TdbFile() {
	}

	public static TdbFile read(String name) {
		TdbFile tdb = new TdbFile();

		try {
			tdb.load(name);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tdb;
	}

	private void load(String name) throws IOException {
		InputStream is = new BufferedInputStream(new FileInputStream(name));

		StructuredInputStream ds = new StructuredInputStream(is);

		while (!ds.testEof()) {
			Block block = readBlock(ds);
			log.info("block", block.getBlockId(), ", len=", block.getBlockLength());
			switch (block.getBlockId()) {
			case 0x50:
				headerBlock = new HeaderBlock(block);
				log.info("header block seen", headerBlock);
				break;
			case 0x44:
				log.info("copyright block");
				copyrightBlock = new CopyrightBlock(block);
				break;
			case 0x42:
				overviewMapBlock = new OverviewMapBlock(block);
				log.info("overview block", overviewMapBlock);
				break;
			case 0x4c:
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

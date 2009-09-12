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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.io.EndOfFileException;
import uk.me.parabola.io.StructuredInputStream;
import uk.me.parabola.log.Logger;

/**
 * The TDB file.  See the package documentation for more details.
 *
 * @author Steve Ratcliffe
 */
public class TdbFile {
	private static final Logger log = Logger.getLogger(TdbFile.class);

	public static final int TDB_V3 = 300;
	public static final int TDB_V407 = 407;

	private static final int BLOCK_OVERVIEW = 0x42;
	private static final int BLOCK_HEADER = 0x50;
	private static final int BLOCK_COPYRIGHT = 0x44;
	private static final int BLOCK_DETAIL = 0x4c;
	private static final int BLOCK_R = 0x52;
	private static final int BLOCK_T = 0x54;

	// The version number of the TDB format
	private int tdbVersion;

	// The blocks that go to make up the file.
	private HeaderBlock headerBlock;
	private CopyrightBlock copyrightBlock = new CopyrightBlock();
	private OverviewMapBlock overviewMapBlock;
	private final List<DetailMapBlock> detailBlocks = new ArrayList<DetailMapBlock>();
	private final RBlock rblock = new RBlock();
	private final TBlock tblock = new TBlock();
	private String overviewDescription;

	public TdbFile() {
	}

	public TdbFile(int tdbVersion) {
		this.tdbVersion = tdbVersion;
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
			tdb.load(ds);
		} finally {
			is.close();
		}

		return tdb;
	}

	public void setProductInfo(int familyId, int productId,
			short productVersion, String seriesName, String familyName, String overviewDescription)
	{
		headerBlock = new HeaderBlock(tdbVersion);
		headerBlock.setFamilyId((short) familyId);
		headerBlock.setProductId((short) productId);
		headerBlock.setProductVersion(productVersion);
		headerBlock.setSeriesName(seriesName);
		headerBlock.setFamilyName(familyName);
		this.overviewDescription = overviewDescription;
	}

	/**
	 * Add a copyright segment to the file.
	 * @param msg The message to add.
	 */
	public void addCopyright(String msg) {
		CopyrightSegment seg = new CopyrightSegment(CopyrightSegment.CODE_COPYRIGHT_TEXT_STRING, 3, msg);
		copyrightBlock.addSegment(seg);
	}

	/**
	 * Set the overview information.  Basically the overall size of the map
	 * set.
	 * @param name The overview map name.
	 * @param bounds The bounds for the map.
	 */
	public void setOverview(String name, Area bounds, String number) {
		overviewMapBlock = new OverviewMapBlock();
		overviewMapBlock.setArea(bounds);
		overviewMapBlock.setMapName(name, number);
		overviewMapBlock.setDescription(overviewDescription);
	}

	/**
	 * Add a detail block.  This describes and names one of the maps in the
	 * map set.
	 * @param detail The detail to add.
	 */
	public void addDetail(DetailMapBlock detail) {
		detailBlocks.add(detail);
	}

	public void write(String name) throws IOException {
		CheckedOutputStream stream = new CheckedOutputStream(
				new BufferedOutputStream(new FileOutputStream(name)),
				new CRC32());

		if (headerBlock == null || overviewMapBlock == null)
			throw new IOException("Attempting to write file without being fully set up");

		try {
			Block block = new Block(BLOCK_HEADER);
			headerBlock.write(block);
			block.write(stream);

			block = new Block(BLOCK_COPYRIGHT);
			copyrightBlock.write(block);
			block.write(stream);

			if (tdbVersion >= TDB_V407) {
				block = new Block(BLOCK_R);
				rblock.write(block);
				block.write(stream);
			}

			block = new Block(BLOCK_OVERVIEW);
			overviewMapBlock.write(block);
			block.write(stream);

			for (DetailMapBlock detail : detailBlocks) {
				block = new Block(BLOCK_DETAIL);
				detail.write(block);
				block.write(stream);
			}

			if (tdbVersion >= TDB_V407) {
				tblock.setSum(stream.getChecksum().getValue());

				block = new Block(BLOCK_T);
				tblock.write(block);
				block.write(stream);
			}
		} finally {
			stream.close();
		}
	}

	/**
	 * Load from the given file name.
	 *
	 * @param ds The stream to read from.
	 * @throws IOException For problems reading the file.
	 */
	private void load(StructuredInputStream ds) throws IOException {

		while (!ds.testEof()) {
			Block block = readBlock(ds);

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

		return new Block(blockType, body);
	}

	public int getTdbVersion() {
		return headerBlock.getTdbVersion();
	}
}

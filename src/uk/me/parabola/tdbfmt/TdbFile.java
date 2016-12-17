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
import java.io.ByteArrayInputStream;
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

	public static final int TDB_V407 = 407;

	// The version number of the TDB format
	private int tdbVersion;

	// The blocks that go to make up the file.
	private HeaderBlock headerBlock;
	private CopyrightBlock copyrightBlock = new CopyrightBlock();
	private OverviewMapBlock overviewMapBlock;
	private final List<DetailMapBlock> detailBlocks = new ArrayList<>();
	private final RBlock rblock = new RBlock();
	private final TBlock tblock = new TBlock();
	private String overviewDescription;
	private int codePage;

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

		try (InputStream is = new BufferedInputStream(new FileInputStream(name))) {
			tdb.load(is);
		}

		return tdb;
	}

	public void setProductInfo(int familyId, int productId,
			short productVersion, String seriesName, String familyName, String overviewDescription,
			byte enableProfile)
	{
		headerBlock = new HeaderBlock(tdbVersion);
		headerBlock.setFamilyId((short) familyId);
		headerBlock.setProductId((short) productId);
		headerBlock.setProductVersion(productVersion);
		headerBlock.setSeriesName(seriesName);
		headerBlock.setFamilyName(familyName);
		headerBlock.setEnableProfile(enableProfile);
		this.overviewDescription = overviewDescription;
	}

	public void setCodePage(int codePage) {
		this.codePage = codePage;
		headerBlock.setCodePage(codePage);
	}

	/**
	 * Add a copyright segment to the file.
	 * @param msg The message to add.
	 */
	public void addCopyright(String msg) {
		if (msg.isEmpty())
			return;

		CopyrightSegment seg = new CopyrightSegment(CopyrightSegment.CODE_COPYRIGHT_TEXT_STRING, 3, msg);
		copyrightBlock.addSegment(seg);
	}

	/**
	 * Set the overview information.  Basically the overall size of the map
	 * set.
	 * @param bounds The bounds for the map.
	 */
	public void setOverview(Area bounds, String number) {
		overviewMapBlock = new OverviewMapBlock();
		overviewMapBlock.setArea(bounds);
		overviewMapBlock.setMapName(number);
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

		if (headerBlock == null || overviewMapBlock == null)
			throw new IOException("Attempting to write file without being fully set up");

		try (CheckedOutputStream stream = new CheckedOutputStream(
				new BufferedOutputStream(new FileOutputStream(name)),
				new CRC32()))
		{
			headerBlock.writeTo(stream, codePage);

			copyrightBlock.writeTo(stream, codePage);

			if (tdbVersion >= TDB_V407) {
				rblock.writeTo(stream, codePage);
			}

			overviewMapBlock.writeTo(stream, codePage);

			for (DetailMapBlock detail : detailBlocks) {
				detail.writeTo(stream, codePage);
			}

			if (tdbVersion >= TDB_V407) {
				tblock.setSum(stream.getChecksum().getValue());
				tblock.writeTo(stream, codePage);
			}
		}
	}

	/**
	 * Load from the given file name.
	 *
	 * @param ds The stream to read from.
	 * @throws IOException For problems reading the file.
	 */
	private void load(InputStream ds) throws IOException {

		boolean eof = false;
		while (!eof) {
			try {
				readBlock(ds);
			} catch (EndOfFileException ignore) {
				eof = true;
			}
		}
	}

	/**
	 * The file is divided into blocks.  This reads a single block.
	 *
	 * @param is The input stream.
	 * @throws IOException For problems reading the file.
	 */
	private void readBlock(InputStream is) throws IOException {
		int blockType = is.read();
		if (blockType == -1)
			throw new EndOfFileException();

		int blockLength = readBlockLength(is);
		if (blockLength == -1)
			throw new EndOfFileException();

		byte[] body = new byte[blockLength];
		int n = is.read(body);
		if (n < 0)
			throw new IOException("failed to read block");

		StructuredInputStream ds = new StructuredInputStream(new ByteArrayInputStream(body));
		switch (blockType) {
		case HeaderBlock.BLOCK_ID:
			headerBlock = new HeaderBlock(ds);
			log.info("header block seen", headerBlock);
			break;
		case CopyrightBlock.BLOCK_ID:
			log.info("copyright block");
			copyrightBlock = new CopyrightBlock(ds);
			break;
		case OverviewMapBlock.BLOCK_ID:
			overviewMapBlock = new OverviewMapBlock(ds);
			log.info("overview block", overviewMapBlock);
			break;
		case DetailMapBlock.BLOCK_ID:
			DetailMapBlock db = new DetailMapBlock(ds);
			log.info("detail block", db);
			detailBlocks.add(db);
			break;
		default:
			log.warn("Unknown block in tdb file");
			break;
		}
	}

	private int readBlockLength(InputStream is) throws IOException {
		int b1 = is.read();
		if (b1 < 0)
			return -1;

		int b2 = is.read();
		if (b2 < 0)
			return -1;

		return ((b2 & 0xff) << 8) | (b1 & 0xff);
	}

	public int getTdbVersion() {
		return headerBlock.getTdbVersion();
	}
}

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

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;

/**
 * The header at the very begining of the .img filesystem.  It has the
 * same signature as a DOS partition table, although I don't know
 * exactly how much the partition concepts are used.
 *
 * @author Steve Ratcliffe
 */
class ImgHeader {
	private static final Logger log = Logger.getLogger(ImgHeader.class);

	// Offsets into the header.
	private static final int OFF_XOR = 0x0;
	private static final int OFF_UPDATE_MONTH = 0xa;
	private static final int OFF_UPDATE_YEAR = 0xb; // +1900 for val >= 0x63, +2000 for less
	private static final int OFF_CHECKSUM = 0xf;
	private static final int OFF_SIGNATURE = 0x10;
	private static final int OFF_UNK_1 = 0x17;

	// If this was a real boot sector these would be the meanings
	private static final int OFF_SECTORS = 0x18;
	private static final int OFF_HEADS = 0x1a;
	private static final int OFF_CYLINDERS = 0x1c;

	private static final int OFF_CREATION_YEAR = 0x39;
	//	private static final int OFF_CREATION_MONTH = 0x3b;
	//	private static final int OFF_CREATION_DAY = 0x3c;
	//	private static final int OFF_CREATION_HOUR = 0x3d;
	//	private static final int OFF_CREATION_MINUTE = 0x3e;
	//	private static final int OFF_CREATION_SECOND = 0x3f;

	// The block number where the directory starts.
	private static final int OFF_DIRECTORY_START_BLOCK = 0x40;

	private static final int OFF_MAP_FILE_INTENTIFIER = 0x41;
	private static final int OFF_MAP_DESCRIPTION = 0x49; // 0x20 padded

	private static final int OFF_HEADS2 = 0x5d;
	private static final int OFF_SECTORS2 = 0x5f;

	private static final int OFF_BLOCK_SIZE_EXPONENT1 = 0x61;
	private static final int OFF_BLOCK_SIZE_EXPONENT2 = 0x62;
	private static final int OFF_BLOCK_SIZE = 0x63;

	//	private static final int OFF_UKN_3 = 0x63;

	private static final int OFF_MAP_NAME_CONT = 0x65;

	// 'Partition table' offsets.
	private static final int OFF_START_HEAD = 0x1bf;
	private static final int OFF_START_SECTOR = 0x1c0;
	private static final int OFF_START_CYLINDER = 0x1c1;
	private static final int OFF_SYSTEM_TYPE = 0x1c2;
	private static final int OFF_END_HEAD = 0x1c3;
	private static final int OFF_END_SECTOR = 0x1c4;
	private static final int OFF_END_CYLINDER = 0x1c5;
	private static final int OFF_REL_SECTORS = 0x1c6;
	private static final int OFF_NUMBER_OF_SECTORS = 0x1ca;


	private static final int OFF_PARTITION_SIG = 0x1fe;

	private FileSystemParam fsParams;

	private final ByteBuffer header = ByteBuffer.allocate(512);

	private ImgChannel file;
	private Date creationTime;

	// Signatures.
	private static final byte[] FILE_ID = new byte[]{
			'G', 'A', 'R', 'M', 'I', 'N', '\0'};

	private static final byte[] SIGNATURE = new byte[]{
			'D', 'S', 'K', 'I', 'M', 'G', '\0'};

	ImgHeader(ImgChannel chan) {
		this.file = chan;
		header.order(ByteOrder.LITTLE_ENDIAN);
	}

	/**
	 * Create a header from scratch.
	 * @param params File system parameters.
	 */
	void createHeader(FileSystemParam params) {
		this.fsParams = params;

		header.put(OFF_XOR, (byte) 0);

		// Set the block size.  2^(E1+E2) where E1 is always 9.
		int exp = 9;

		int bs = params.getBlockSize();
		for (int i = 0; i < 32; i++) {
			bs >>>= 1;
			if (bs == 0) {
				exp = i;
				break;
			}
		}

		if (exp < 9)
			throw new IllegalArgumentException("block size too small");

		header.put(OFF_BLOCK_SIZE_EXPONENT1, (byte) 0x9);
		header.put(OFF_BLOCK_SIZE_EXPONENT2, (byte) (exp - 9));

		header.position(OFF_SIGNATURE);
		header.put(SIGNATURE);

		header.position(OFF_MAP_FILE_INTENTIFIER);
		header.put(FILE_ID);

		header.put(OFF_UNK_1, (byte) 0x2);

		// Acutally this may not be the directory start block, I am guessing -
		// always assume it is 2 anyway.
		header.put(OFF_DIRECTORY_START_BLOCK, (byte) fsParams.getDirectoryStartBlock());

		// This secotors, heads, cylinders stuff is probably just 'unknown'
		int sectors = 4;
		int heads = 0x10;
		int cylinders = 0x20;
		header.putShort(OFF_SECTORS, (short) sectors);
		header.putShort(OFF_HEADS, (short) heads);
		header.putShort(OFF_CYLINDERS, (short) cylinders);
		header.putShort(OFF_HEADS2, (short) heads);
		header.putShort(OFF_SECTORS2, (short) sectors);

		header.position(OFF_CREATION_YEAR);
		Utils.setCreationTime(header, creationTime);

		char blocks = (char) (heads * sectors * cylinders / (1 << exp - 9));
		header.putChar(OFF_BLOCK_SIZE, blocks);

		header.put(OFF_PARTITION_SIG, (byte) 0x55);
		header.put(OFF_PARTITION_SIG+1, (byte) 0xaa);

		header.put(OFF_START_HEAD, (byte) 0);
		header.put(OFF_START_SECTOR, (byte) 1);
		header.put(OFF_START_CYLINDER, (byte) 0);
		header.put(OFF_SYSTEM_TYPE, (byte) 0);
		header.put(OFF_END_HEAD, (byte) (heads - 1));
		header.put(OFF_END_SECTOR, (byte) sectors);
		header.put(OFF_END_CYLINDER, (byte) (cylinders - 1));
		header.putInt(OFF_REL_SECTORS, 0);
		header.putInt(OFF_NUMBER_OF_SECTORS, (blocks * (1 << (exp - 9))));

		setDirectoryStartBlock(params.getDirectoryStartBlock());

		// Set the times.
		Date date = new Date();
		setCreationTime(date);
		setUpdateTime(date);
		setDescription(params.getMapDescription());

		// Checksum is not checked.
		int check = 0;
		header.put(OFF_CHECKSUM, (byte) check);
	}

	void setHeader(ByteBuffer buf)  {
		buf.flip();
		header.put(buf);

		byte exp1 = header.get(OFF_BLOCK_SIZE_EXPONENT1);
		byte exp2 = header.get(OFF_BLOCK_SIZE_EXPONENT2);
		log.debug("header exponent", exp1, exp2);

		fsParams = new FileSystemParam();
		fsParams.setBlockSize(1 << (exp1 + exp2));
		fsParams.setDirectoryStartBlock(header.get(OFF_DIRECTORY_START_BLOCK));

		// ... more to do
	}

	void setFile(ImgChannel file) {
		this.file = file;
	}
	
	FileSystemParam getParams() {
		return fsParams;
	}

	/**
	 * Sync the header to disk.
	 * @throws IOException If an error occurs during writing.
	 */
	public void sync() throws IOException {
		setUpdateTime(new Date());

		header.rewind();
		file.position(0);
		file.write(header);
		file.position(fsParams.getDirectoryStartBlock() * (long) 512);
	}

	/**
	 * Set the update time.
	 * @param date The date to use.
	 */
	protected void setUpdateTime(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);

		header.put(OFF_UPDATE_YEAR, toYearCode(cal.get(Calendar.YEAR)));
		header.put(OFF_UPDATE_MONTH, (byte) (cal.get(Calendar.MONTH)+1));
	}

	/**
	 * Set the description.  It is spread across two areas in the header.
	 * @param desc The description.
	 */
	protected void setDescription(String desc) {
		header.position(OFF_MAP_DESCRIPTION);
		int len = desc.length();
		if (len > 50)
			throw new IllegalArgumentException("Description is too long (max 50)");
		String part1, part2;
		if (len > 20) {
			part1 = desc.substring(0, 20);
			part2 = desc.substring(20, len);
		} else {
			part1 = desc.substring(0, len);
			part2 = "";
		}

		header.put(toByte(part1));
		for (int i = len; i < 20; i++) {
			header.put((byte) ' ');
		}

		header.position(OFF_MAP_NAME_CONT);
		header.put(toByte(part2));
		int start = len - 20;
		if (start < 0)
			start = 0;
		for (int i = start; i < 30; i++)
			header.put((byte) ' ');
		header.put((byte) 0);
	}

	/**
	 * Convert a string to a byte array.
	 * @param s The string
	 * @return A byte array.
	 */
	private byte[] toByte(String s) {
		// NB: what character set should be used?
		return s.getBytes();
	}

	/**
	 * Convert to the one byte code that is used for the year.
	 * If the year is in the 1900, then subtract 1900 and add the result to 0x63,
	 * else subtract 2000.
	 * Actually looks simpler, just subtract 1900..
	 * @param y The year in real-world format eg 2006.
	 * @return A one byte code representing the year.
	 */
	private byte toYearCode(int y) {
		return (byte) (y - 1900);
	}

	public int getBlockSize() {
		return fsParams.getBlockSize();
	}

	/**
	 * Set the block size.  This may only work if creating the file
	 * from scratch.
	 * @param blockSize The new block size to use.
	 */
	public void setBlockSize(int blockSize) {
		header.put(OFF_BLOCK_SIZE, (byte) blockSize);
		fsParams.setBlockSize(blockSize);
	}

	public int getDirectoryStartBlock() {
		return fsParams.getDirectoryStartBlock();
	}

	protected void setDirectoryStartBlock(int directoryStartBlock) {
		header.put(OFF_DIRECTORY_START_BLOCK, (byte) directoryStartBlock);
		fsParams.setDirectoryStartBlock(directoryStartBlock);
	}

	protected void setCreationTime(Date date) {
		this.creationTime = date;
	}
}

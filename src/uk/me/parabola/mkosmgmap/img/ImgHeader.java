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

import java.util.Date;
import java.util.Calendar;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.io.IOException;

/**
 * The header at the very begining of the .img filesystem.  It has the
 * same signature as a DOS partition table, although I don't know
 * exactly how much the partition concepts are used.
 *
 * @author Steve Ratcliffe <sr@parabola.me.uk>
 */
public class ImgHeader {
	static protected Logger log = Logger.getLogger(ImgHeader.class);
	
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
	private static final int OFF_CREATION_MONTH = 0x3b;
	private static final int OFF_CREATION_DAY = 0x3c;
	private static final int OFF_CREATION_HOUR = 0x3d;
	private static final int OFF_CREATION_MINUTE = 0x3e;
	private static final int OFF_CREATION_SECOND = 0x3f;

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

	private static final int HEADER_SIZE = 0x600;

	// Variables for this file system.
	private int directoryStartBlock = 2;
	private int blockSize = 512;

	private ByteBuffer header = ByteBuffer.allocateDirect(512);
	private static final byte[] FILE_ID = new byte[]{
			'G', 'A', 'R', 'M', 'I', 'N', '\0'};
	private static final byte[] SIGNATURE = new byte[]{
			'D', 'S', 'K', 'I', 'M', 'G', '\0'};
	private FileChannel file;


	public ImgHeader() {
		header.order(ByteOrder.LITTLE_ENDIAN);
		createFS();
	}

	/**
	 * Create a file system from scratch.
	 */
	private void createFS() {
		header.put(OFF_XOR, (byte) 0);

		// Set the block size.  2^(E1+E2) where E1 is always 9.
		int exp = getBlockExponent();
		if (exp < 9)
			throw new IllegalArgumentException("block size too small");
		header.put(OFF_BLOCK_SIZE_EXPONENT1, (byte) 0x9);
		header.put(OFF_BLOCK_SIZE_EXPONENT2, (byte) (exp - 9));

		header.position(OFF_SIGNATURE);
		header.put(SIGNATURE);

		header.position(OFF_MAP_FILE_INTENTIFIER);
		header.put(FILE_ID);

		header.put(OFF_UNK_1, (byte) 0x2);
		header.put(OFF_DIRECTORY_START_BLOCK, (byte) directoryStartBlock);

		// This secotors, heads, cylinders stuff is probably just 'unknown'
		int sectors = 4;
		int heads = 0x10;
		int cylinders = 0x20;
		header.putShort(OFF_SECTORS, (short) sectors);
		header.putShort(OFF_HEADS, (short) heads);
		header.putShort(OFF_CYLINDERS, (short) cylinders);
		header.putShort(OFF_HEADS2, (short) heads);
		header.putShort(OFF_SECTORS2, (short) sectors);

		char blocks = (char) (heads * sectors
				* cylinders / (1 << exp - 9));
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
		header.put(OFF_REL_SECTORS, (byte) 0);
		header.put(OFF_NUMBER_OF_SECTORS,
				(byte) (blocks * (2 ^ (exp - 9)) * sectors * cylinders));

		// Checksum is not checked.
		int check = 0;
		header.put(OFF_CHECKSUM, (byte) check);
	}

	public ImgHeader(FileChannel fileChannel) {
		this();
		this.file = fileChannel;
	}

	/**
	 * Sync the header to disk.
	 * @throws IOException If an error occurs during writing.
	 */
	public void sync() throws IOException {
		header.rewind();
		file.write(header);
	}

	/**
	 * Set the creation date.  Note that the year is encoded specially.
	 * @param date The date to set.
	 */
	public void setCreationTime(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);

		header.putChar(OFF_CREATION_YEAR, (char) cal.get(Calendar.YEAR));
		header.put(OFF_CREATION_MONTH, (byte) (cal.get(Calendar.MONTH)));
		header.put(OFF_CREATION_DAY, (byte) cal.get(Calendar.DAY_OF_MONTH));
		header.put(OFF_CREATION_HOUR, (byte) cal.get(Calendar.HOUR));
		header.put(OFF_CREATION_MINUTE, (byte) cal.get(Calendar.MINUTE));
		header.put(OFF_CREATION_SECOND, (byte) cal.get(Calendar.SECOND));
	}

	/**
	 * Set the update time.
	 * @param date The date to use.
	 */
	public void setUpdateTime(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);

		header.put(OFF_UPDATE_YEAR, toYearCode(cal.get(Calendar.YEAR)));
		header.put(OFF_UPDATE_MONTH, (byte) cal.get(Calendar.MONTH));
	}

	/**
	 * Set the description.  It is spread across two areas in the header.
	 * @param desc The description.
	 */
	public void setDescription(String desc) {
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
	 * Get the exponent for the block size.
	 * @return The power of two that the block size is.
	 */
	private int getBlockExponent() {
		int bs = blockSize;
		for (int i = 0; i < 32; i++) {
			bs >>>= 1;
			if (bs == 0)
				return i;
		}

		// This cant really happen, as there are 32 bits in an int
		throw new IllegalArgumentException("block size too large");
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
	 * @param y The year in real-world format eg 2006.
	 * @return A one byte code representing the year.
	 */
	private byte toYearCode(int y) {
		if (y >= 2000)
			return (byte) (y - 2000);
		else
			return (byte) (y - 1900 + 0x63);
	}
}

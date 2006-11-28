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
	public static final int OFF_XOR = 0x0;
	public static final int OFF_UPDATE_MONTH = 0xa;
	public static final int OFF_UPDATE_YEAR = 0xb; // +1900 for val >= 0x63, +2000 for less
	public static final int OFF_CHECKSUM = 0xf;
	public static final int OFF_SIGNATURE = 0x10;
	public static final int OFF_UNK_1 = 0x17;

	// If this was a real boot sector these would be the meanings
	public static final int OFF_SECTORS = 0x18;
	public static final int OFF_HEADS = 0x1a;
	public static final int OFF_CYLINDERS = 0x1c;

	public static final int OFF_CREATION_YEAR = 0x39;
	public static final int OFF_CREATION_MONTH = 0x3b;
	public static final int OFF_CREATION_DAY = 0x3c;
	public static final int OFF_CREATION_HOUR = 0x3d;
	public static final int OFF_CREATION_MINUTE = 0x3e;
	public static final int OFF_CREATION_SECOND = 0x3f;

	public static final int OFF_UNK_2 = 0x40;

	public static final int OFF_MAP_FILE_INTENTIFIER = 0x41;
	public static final int OFF_MAP_DESCRIPTION = 0x49; // 0x20 padded

	public static final int OFF_HEADS2 = 0x5d;
	public static final int OFF_SECTORS2 = 0x5f;

	public static final int OFF_BLOCK_SIZE_EXPONENT1 = 0x61;
	public static final int OFF_BLOCK_SIZE_EXPONENT2 = 0x62;
	public static final int OFF_BLOCK_SIZE = 0x63;

	public static final int OFF_UKN_3 = 0x63;

	public static final int OFF_MAP_NAME_CONT = 0x65;

	public static final int OFF_PARTITION_SIG = 0x1fe;

	public static final int HEADER_SIZE = 0x600;

	private ByteBuffer header = ByteBuffer.allocateDirect(512);
	private static final byte[] FILE_ID = new byte[]{
			'G', 'A', 'R', 'M', 'I', 'N', '\0'};
	private static final byte[] SIGNATURE = new byte[]{
			'D', 'S', 'K', 'I', 'M', 'G', '\0'};
	private FileChannel file;


	public ImgHeader() {
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		// Some things are always the same as far as we know, so set
		// them here.
		header.put(OFF_XOR, (byte) 0);
		header.put(OFF_BLOCK_SIZE_EXPONENT1, (byte) 0x9);
		header.put(OFF_BLOCK_SIZE_EXPONENT2, (byte) 0);

		header.position(OFF_SIGNATURE);
		header.put(SIGNATURE);

		header.position(OFF_MAP_FILE_INTENTIFIER);
		header.put(FILE_ID);

		header.put(OFF_UNK_1, (byte) 0x2);
		header.put(OFF_UNK_2, (byte) 0x2);

		header.putShort(OFF_SECTORS, (short) 0x4);
		header.putShort(OFF_HEADS, (short) 0x10);
		header.putShort(OFF_CYLINDERS, (short) 0x20);
		header.putShort(OFF_HEADS2, (short) 0x10);
		header.putShort(OFF_SECTORS2, (short) 0x4);

		header.putChar(OFF_BLOCK_SIZE, (char) (header.get(OFF_HEADS)
				* header.get(OFF_SECTORS)
				* header.get(OFF_CYLINDERS)
				/ (1 << (header.get(OFF_BLOCK_SIZE_EXPONENT2)))));

		header.put(OFF_PARTITION_SIG, (byte) 0x55);
		header.put(OFF_PARTITION_SIG+1, (byte) 0xaa);
		
		log.debug("is array is " + header.hasArray());
	}

	public ImgHeader(FileChannel fileChannel) {
		this();
		this.file = fileChannel;
	}

	/**
	 * Set the creation date.  Note that the year is encoded specially.
	 * @param date The date to set.
	 */
	public void setCreationTime(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);

		log.debug("mon" + cal.get(Calendar.MONTH));

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

	public ByteBuffer getBuffer() {
		header.rewind();
		return header;
	}

	public void sync() throws IOException {
		header.rewind();
		file.write(header);
	}
}

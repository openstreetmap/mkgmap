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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

import static java.util.Arrays.asList;

/**
 * The header at the very beginning of the .img filesystem.  It has the
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
	private static final int OFF_SUPP = 0xe;		// Appears to be set for gmapsupp files
	private static final int OFF_CHECKSUM = 0xf;
	private static final int OFF_SIGNATURE = 0x10;
	private static final int OFF_UNK_1 = 0x17;

	// If this was a real boot sector these would be the meanings
	private static final int OFF_SECTORS = 0x18;
	private static final int OFF_HEADS = 0x1a;
	private static final int OFF_CYLINDERS = 0x1c;

	private static final int OFF_CREATION_DATE = 0x39;

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

	// Lengths of some of the fields
	private static final int LEN_MAP_NAME_CONT = 30;
	private static final int LEN_MAP_DESCRIPTION = 20;

	private FileSystemParam fsParams;

	private final ByteBuffer header = ByteBuffer.allocate(512);

	private ImgChannel file;
	private Date creationTime;

	private int sectorsPerTrack;
	private int headsPerCylinder;

	// Signatures.
	private static final byte[] FILE_ID = {
			'G', 'A', 'R', 'M', 'I', 'N', '\0'};

	private static final byte[] SIGNATURE = {
			'D', 'S', 'K', 'I', 'M', 'G', '\0'};

	private int numBlocks;

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

		// Actually this may not be the directory start block, I am guessing -
		// always assume it is 2 anyway.
		header.put(OFF_DIRECTORY_START_BLOCK, (byte) fsParams.getDirectoryStartEntry());

		header.position(OFF_CREATION_DATE);
		Utils.setCreationTime(header, creationTime);

		setDirectoryStartEntry(params.getDirectoryStartEntry());

		// Set the times.
		Date date = new Date();
		setCreationTime(date);
		setUpdateTime(date);
		setDescription(params.getMapDescription());

		header.put(OFF_SUPP, (byte) (fsParams.isGmapsupp()? 1: 0));

		// Checksum is not checked.
		header.put(OFF_CHECKSUM, (byte) 0);
	}

	/**
	 * Write out the values associated with the partition sizes.
	 *
	 * @param blockSize Block size.
	 */
	private void writeSizeValues(int blockSize) {
		int endSector = (int) (((numBlocks+1L) * blockSize + 511) / 512);
		//System.out.printf("end sector %d %x\n", endSector, endSector);

		// We have three maximum values for sectors, heads and cylinders.  We attempt to find values
		// for them that are larger than the 
		sectorsPerTrack = 32;   // 6 bit value
		headsPerCylinder = 128;
		int cyls = 0x400;

		// Try out various values of h, s and c until we find a combination that is large enough.
		// I'm not entirely sure about the valid values, but it seems that only certain values work
		// which is why we use values from a list.
		// See: http://www.win.tue.nl/~aeb/partitions/partition_types-2.html for justification for the h list
		out:
		for (int h : asList(16, 32, 64, 128, 256)) {
			for (int s : asList(4, 8, 16, 32)) {
				for (int c : asList(0x20, 0x40, 0x80, 0x100, 0x200, 0x3ff)) {
					log.info("shc=", s + "," + h + "," + c, "end=", endSector);
					//System.out.println("shc=" + s + "," + h + "," + c + "end=" + endSector);
					if (s * h * c > endSector) {
						headsPerCylinder = h;
						sectorsPerTrack = s;
						cyls = c;
						break out;
					}
				}
			}
		}

		// This sectors, head, cylinders stuff appears to be used by mapsource
		// and they have to be larger than the actual size of the map.  It
		// doesn't appear to have any effect on a garmin device or other software.
		header.putShort(OFF_SECTORS, (short) sectorsPerTrack);
		header.putShort(OFF_SECTORS2, (short) sectorsPerTrack);
		header.putShort(OFF_HEADS, (short) headsPerCylinder);
		header.putShort(OFF_HEADS2, (short) headsPerCylinder);
		header.putShort(OFF_CYLINDERS, (short) cyls);

		// Since there are only 2 bytes here it can overflow, if it
		// does we replace it with 0xffff.
		int blocks = (int) (endSector * 512L / blockSize);
		char shortBlocks = blocks > 0xffff ? 0xffff : (char) blocks;
		header.putChar(OFF_BLOCK_SIZE, shortBlocks);

		header.put(OFF_PARTITION_SIG, (byte) 0x55);
		header.put(OFF_PARTITION_SIG + 1, (byte) 0xaa);

		// Partition starts at zero. This is 0,0,1 in CHS terms.
		header.put(OFF_START_HEAD, (byte) 0);
		header.put(OFF_START_SECTOR, (byte) 1);
		header.put(OFF_START_CYLINDER, (byte) 0);

		header.put(OFF_SYSTEM_TYPE, (byte) 0);

		// Now calculate the CHS address of the last sector of the partition.
		CHS chs = new CHS(endSector - 1);

		header.put(OFF_END_HEAD, (byte) (chs.h));
		header.put(OFF_END_SECTOR, (byte) ((chs.s) | ((chs.c >> 2) & 0xc0)));
		header.put(OFF_END_CYLINDER, (byte) (chs.c & 0xff));

		// Write the LBA block address of the beginning and end of the partition.
		header.putInt(OFF_REL_SECTORS, 0);
		header.putInt(OFF_NUMBER_OF_SECTORS, endSector);
		log.info("number of blocks", endSector - 1);
	}

	void setHeader(ByteBuffer buf)  {
		buf.flip();
		header.put(buf);

		byte exp1 = header.get(OFF_BLOCK_SIZE_EXPONENT1);
		byte exp2 = header.get(OFF_BLOCK_SIZE_EXPONENT2);
		log.debug("header exponent", exp1, exp2);

		fsParams = new FileSystemParam();
		fsParams.setBlockSize(1 << (exp1 + exp2));
		fsParams.setDirectoryStartEntry(header.get(OFF_DIRECTORY_START_BLOCK));

		StringBuffer sb = new StringBuffer();
		sb.append(Utils.bytesToString(buf, OFF_MAP_DESCRIPTION, LEN_MAP_DESCRIPTION));
		sb.append(Utils.bytesToString(buf, OFF_MAP_NAME_CONT, LEN_MAP_NAME_CONT));

		fsParams.setMapDescription(sb.toString().trim());

		byte h = header.get(OFF_END_HEAD);
		byte sc1 = header.get(OFF_END_SECTOR);
		byte sc2 = header.get(OFF_END_CYLINDER);
		CHS chs = new CHS();
		chs.setFromPartition(h, sc1, sc2);
		int lba = chs.toLba();
		log.info("partition sectors", lba);
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

		writeSizeValues(fsParams.getBlockSize());
		
		header.rewind();
		file.position(0);
		file.write(header);
		file.position(fsParams.getDirectoryStartEntry() * 512L);
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
		int len = desc.length();
		if (len > 50)
			throw new IllegalArgumentException("Description is too long (max 50)");
		String part1, part2;
		if (len > LEN_MAP_DESCRIPTION) {
			part1 = desc.substring(0, LEN_MAP_DESCRIPTION);
			part2 = desc.substring(LEN_MAP_DESCRIPTION, len);
		} else {
			part1 = desc.substring(0, len);
			part2 = "";
		}

		header.position(OFF_MAP_DESCRIPTION);
		header.put(toByte(part1));
		for (int i = len; i < LEN_MAP_DESCRIPTION; i++)
			header.put((byte) ' ');

		header.position(OFF_MAP_NAME_CONT);
		header.put(toByte(part2));
		for (int i = Math.max(len - LEN_MAP_DESCRIPTION, 0); i < LEN_MAP_NAME_CONT; i++)
			header.put((byte) ' ');

		header.put((byte) 0); // really?
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

	protected void setDirectoryStartEntry(int directoryStartEntry) {
		header.put(OFF_DIRECTORY_START_BLOCK, (byte) directoryStartEntry);
		fsParams.setDirectoryStartEntry(directoryStartEntry);
	}

	protected void setCreationTime(Date date) {
		this.creationTime = date;
	}

	public void setNumBlocks(int numBlocks) {
		this.numBlocks = numBlocks;
	}

	/**
	 * Represent a block number in the chs format.
	 *
	 * Note that this class uses the headsPerCylinder and sectorsPerTrack values
	 * from the enclosing class.
	 *
	 * @see <a href="http://en.wikipedia.org/wiki/Logical_Block_Addressing">Logical block addressing</a>
	 */
	private class CHS {
		private int h;
		private int s;
		private int c;

		private CHS() {
		}

		public CHS(int lba) {
			toChs(lba);
		}

		/**
		 * Calculate the CHS values from the the given logical block address.
		 * @param lba Input logical block address.
		 */
		private void toChs(int lba) {
			h = (lba / sectorsPerTrack) % headsPerCylinder;
			s = (lba % sectorsPerTrack) + 1;
			c = lba / (sectorsPerTrack * headsPerCylinder);
		}

		/**
		 * Set from a partition table entry.
		 *
		 * The cylinder is 10 bits and is split between the top 2 bit of the sector
		 * value and its own byte.
		 *
		 * @param h The h value.
		 * @param sc1 The s value (6 bits) and top 2 bits of c.
		 * @param sc2 The bottom 8 bits of c.
		 */
		public void setFromPartition(byte h, byte sc1, byte sc2) {
			this.h = h;
			this.s = (sc1 & 0x3f) + ((sc2 >> 2) & 0xc0);
			this.c = sc2 & 0xff;
		}

		public int toLba() {
			return (c * headsPerCylinder + h) * sectorsPerTrack + (s - 1);
		}
	}
}

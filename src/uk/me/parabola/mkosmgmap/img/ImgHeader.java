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

/**
 * The header at the very begining of the .img filesystem.  It has the
 * same signature as a DOS partition table, although I don't know
 * exactly how much the partition concepts are used.
 *
 * @author Steve Ratcliffe <sr@parabola.me.uk>
 */
public class ImgHeader {

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
	public static final int OFF_CREATION_HOUR = 0x3c;
	public static final int OFF_CREATION_MINUTE = 0x3d;
	public static final int OFF_CREATION_SECOND = 0x3e;

	public static final int OFF_UKN_2 = 0x40;

	public static final int OFF_MAP_FILE_INTENTIFIER = 0x41;
	public static final int OFF_MAP_DESCRIPTION = 0x49; // 0x20 padded

	public static final int OFF_BLOCK_SIZE_EXPONENT1 = 0x61;
	public static final int OFF_BLOCK_SIZE_EXPONENT2 = 0x62;

	public static final int OFF_UKN_3 = 0x63;

	public static final int OFF_MAP_NAME_CONT = 0x65;

	public static final int HEADER_SIZE = 0x1be;

	private char[] header = new char[HEADER_SIZE];
}

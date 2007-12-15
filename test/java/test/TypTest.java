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
 * Create date: Dec 14, 2007
 */
package test;

import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.TYPFile;
import uk.me.parabola.imgfmt.app.TYPHeader;
import uk.me.parabola.imgfmt.app.ReadStrategy;
import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * @author Steve Ratcliffe
 */
public class TypTest {
	private static final int FLEN = 10;

	public static void main(String[] args) throws FileNotFoundException {
		String name = args[0];

		RandomAccessFile raf = new RandomAccessFile(name, "r");
		FileChannel channel = raf.getChannel();

		ImgChannel typ = new FileImgChannel(channel);

		TYPFile typFile = new TYPFile(typ, false);
		CommonHeader header = typFile.getHeader();

		printHeader(header);

		ReadStrategy reader = typFile.getReader();
		printBody(reader);
	}

	private static void printBody(ReadStrategy reader) {
		int pos = 91;
		for (; ; pos++) {
			reader.position(pos);
			byte b = reader.get();

			System.out.printf("%#06x  %-10s: ", pos, "???");
			System.out.printf("%#04x", b);
			System.out.printf("\n");

		}
	}

	private static void printHeader(CommonHeader header) {
		System.out.println("Common Header");
		System.out.printf("%-" + FLEN + "s: %s\n", "Type", header.getType());
		System.out.printf("%-10s: %d\n", "Date", header.getHeaderLength());
		System.out.printf("%-10s: %s\n", "Date", header.getCreationDate());

		printHeader((TYPHeader) header);
	}

	// 0x27  Offset of first thing after header, could be up to 4 bytes
	// 0x2f  product id?
	// 0x51  ptr to start of something else, looks like it ends 0xdd or d8

	private static void printHeader(TYPHeader header) {
		byte[] un = header.getUnknown();

		int off = TYPHeader.COMMON_HEADER_LEN;

		for (int i = 0; i < un.length; i++) {
			int b = un[i] & 0xff;
			int c = b;
			if (i < un.length - 2)
				c = b | ((un[i+1] & 0xff) << 8);
			int i3 = c;
			if (i < un.length - 3)
				i3 = c | ((un[i+2] & 0xff) << 16);
			int i4 = i3;
			if (i < un.length - 4)
				i4 = i3 | ((un[i+3] & 0xff) << 24);
			System.out.printf("%#06x  %-10s: %#04x (%3d) %#06x (%5d) %#08x (%8d) %#010x (%10d)\n", 
					off++, "Unknown",
					b, b, c, c, i3, i3, i4, i4);
		}
	}
}

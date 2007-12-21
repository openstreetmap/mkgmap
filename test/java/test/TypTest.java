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

import uk.me.parabola.imgfmt.app.CommonHeader;
import static uk.me.parabola.imgfmt.app.CommonHeader.COMMON_HEADER_LEN;
import uk.me.parabola.imgfmt.app.ReadStrategy;
import uk.me.parabola.imgfmt.app.TYPFile;
import uk.me.parabola.imgfmt.app.TYPHeader;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileImgChannel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

/**
 * @author Steve Ratcliffe
 */
public class TypTest {
	private static long filelen;

	public static void main(String[] args) throws FileNotFoundException {
		String name = args[0];

		File file = new File(name);
		filelen = file.length();

		RandomAccessFile raf = new RandomAccessFile(name, "r");
		FileChannel channel = raf.getChannel();

		ImgChannel typ = new FileImgChannel(channel);

		TYPFile typFile = new TYPFile(typ, false);
		CommonHeader header = typFile.getHeader();

		printHeader(header);

		Section lines = Section.getByName("Lines");
		Section lineControl = Section.getByName("sect3");

		printLines(lineControl, lines);
		System.exit(1);

		ReadStrategy reader = typFile.getReader();
		printBody(reader);
	}

	private static void printLines(Section lineControl, Section lines) {
		int step = lineControl.getItemSize();
	}


	private static void printBody(ReadStrategy reader) {
		int pos = 91;
		while (pos < filelen + 8) {
			pos = printUnknownBodyLine(reader, pos);
		}
	}

	private static int printUnknownBodyLine(ReadStrategy reader, int pos) {
		reader.position(pos);

		int fakestart = pos & ~0xf;
		System.out.printf("%#06x  %-10s:", fakestart, "???");
		int off = fakestart;
		StringBuffer ascii = new StringBuffer();
		for (int count = 0; count < 16; count++, off++) {
			if (count == 8) {
				System.out.print(" ");
				ascii.append(' ');
			}
			if (off < pos) {
				System.out.printf("   ");
				ascii.append(' ');
			} else {
				byte b = reader.get();
				System.out.printf(" %02x", b);
				ascii.append(Character.isLetterOrDigit(b) ? (char)b : '.');
			}
		}
		System.out.printf(" %s\n", ascii);
		return off;
	}

	private static void printHeader(CommonHeader header) {
		System.out.println("Common Header");
		System.out.printf("%-10s: %s\n", "Type", header.getType());
		System.out.printf("%-10s: %d\n", "Length", header.getHeaderLength());
		System.out.printf("%-10s: %s\n", "Date", header.getCreationDate());

		printHeader((TYPHeader) header);
	}

	// 0x27  Offset of first thing after header, could be up to 4 bytes
	// 0x2f  product id?
	// 0x51  ptr to start of something else, looks like it ends 0xdd or d8

	private static void printHeader(TYPHeader header) {
		byte[] un = header.getUnknown();

		int off = COMMON_HEADER_LEN;

		System.out.println("\nFile header");

		int value;
		int size;
		int itemSize;
		off = printUnknown(off, un, 0x17);

		value = getInt(un, 0x17);
		size = getInt(un, 0x1b);

		Section section = Section.addSection("sect5", value, size);
		off = printSection(off, section);

		off = printUnknown(off, un, 0x1f);
		value = getInt(un, 0x1f);
		size = getInt(un, 0x23);
		section = Section.addSection("Lines", value, size);
		off = printSection(off, section);

		value = getInt(un, 0x27);
		size = getInt(un, 0x2b);
		section = Section.addSection("sect1", value, size);
		off = printSection(off, section);

		char cvalue = getShort(un, 0x2f);
		off = printUShort(off, "product", cvalue);
		
		off = printUnknown(off, un, 0x33);

		value = getInt(un, 0x33);
		itemSize = getShort(un, 0x37);
		size = getInt(un, 0x39);
		section = Section.addSection("sect6", value, size, itemSize);
		off = printSection(off, section);

		value = getInt(un, 0x3d);
		itemSize = getShort(un, 0x41);
		size = getInt(un, 0x43);
		section = Section.addSection("sect3", value, size, itemSize);
		off = printSection(off, section);

		value = getInt(un, 0x47);
		itemSize = getShort(un, 0x4b);
		size = getInt(un, 0x4d);
		section = Section.addSection("sect4", value, size, itemSize);
		off = printSection(off, section);

		value = getInt(un, 0x51);
		itemSize = getShort(un, 0x55);
		size = getInt(un, 0x57);
		section = Section.addSection("polygon stack", value, size, itemSize);
		off = printSection(off, section);

		off = printUnknown(off, un, 0x5b);

		System.out.println("end offset is " + Integer.toHexString(off));

		analyseSections();
		printSpeculation(un);
		//System.exit(0);
	}

	/*
	 * Print out things that we believe so that they can be checked.
	 */
	private static void printSpeculation(byte[] un) {
		// OK belive all this now.
		//System.out.println("offset 0043 maybe u sect3 size: " + getInt(un, 0x43));
		//System.out.println("offset 004d maybe u sect4 size: " + getInt(un, 0x4d));
		//System.out.format("offset 33 might be section start 6: %x\n", getInt(un, 0x33));
		//System.out.format("offset 39 might be sect6 size: %x\n", getInt(un, 0x39));
		//System.out.format("offset 1b might be sect5 size: %x\n", getInt(un, 0x1b));
		//System.out.format("offset 57 might be polygon stack size: %x\n", getInt(un, 0x57));
	}

	/**
	 * Print out what we think are sections.  Print out the gaps between
	 * the ones we have identified.
	 */
	private static void analyseSections() {
		List<Section> sects = Section.getList();

		Collections.sort(sects);

		int lastoff = 0;
		for (Section s : sects) {
			int off = s.getOffset();
			if (lastoff != 0) {
				int len = off - lastoff;
				System.out.format("%56s: %8x (%d)\n", "implied len", len, len);
			}
			lastoff = off;
			System.out.println(s);
		}

		int len = (int) (filelen - lastoff);
		System.out.format("%56s: %8x (%d)\n", "implied len", len, len);
		System.out.format("End of file at %#x\n", filelen);
	}

	/**
	 * print a section.  Allows us to print the end offset which may help in
	 * finding other sections.
	 * @param off Offset to print.
	 * @param sect The section to print.
	 * @return The new offset.
	 */
	private static int printSection(int off, Section sect) {
		printOffDesc(off, sect.getDescription());
		System.out.format("Off: %8x", sect.getOffset());
		if (sect.isSizeSet())
			System.out.format(", Next: %8x", sect.getOffset()+sect.getLen());
		if (sect.itemSet)
			System.out.format(", items of %d", sect.itemSize);

		System.out.println();
		int add = 4;
		if (sect.isSizeSet())
			add += 4;
		if (sect.itemSet)
			add += 2;
		return off + add;
	}

	private static int printUnknown(int startoff, byte[] un, int end) {
		int commLen = COMMON_HEADER_LEN;
		int off = startoff;
		for (int i = startoff-commLen; i < end-commLen; i++) {
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
			printOffDesc(off++, "???");
			System.out.printf(
					"%#04x (%3d) %#06x (%5d) %#08x (%8d) %#010x (%10d)\n",
					b, b, c, c, i3, i3, i4, i4);
		}
		return off;
	}

	private static int printInt(int offset, String desc, int value) {
		printOffDesc(offset, desc);
		System.out.printf("%08x (%d)\n", value, value);
		return offset + 4;
	}

	private static int printUShort(int offset, String desc, char cvalue) {
		printOffDesc(offset, desc);
		System.out.printf("%04x (%d)\n", (int) cvalue, (int) cvalue);
		return offset + 2;
	}

	private static void printOffDesc(int offset, String desc) {
		System.out.printf("%#06x:  %-20s: ", offset, desc);
	}

	private static int getInt(byte[] un, int off) {
		int ind = off - COMMON_HEADER_LEN;
		return (un[ind] & 0xff)
				+ (((un[ind+1]) & 0xff) << 8)
				+ (((un[ind+2]) & 0xff) << 16)
				+ (((un[ind+3]) & 0xff) << 24)
				;
	}

	private static char getShort(byte[] un, int off) {
		int ind = off - COMMON_HEADER_LEN;
		return (char) ((un[ind] & 0xff)
				+ (((un[ind+1]) & 0xff) << 8)
		);
	}

	static class Section implements Comparable<Section> {

		private String description;
		private final int offset;
		private final int len;
		private int itemSize;

		private boolean sizeSet;
		private boolean itemSet;

		private static List<Section> list = new ArrayList<Section>();

		private Section(int off, int len) {
			this.offset = off;
			this.len = len;
		}

		public static Section addSection(String desc, int off, int size) {
			Section section = new Section(off, size);
			section.description = desc;
			section.sizeSet = true;

			list.add(section);
			return section;
		}

		/**
		 * Add a section of unknown length.
		 * @param desc The description.
		 * @param off The offset
		 * @return The new section.
		 */
		public static Section addSection(String desc, int off) {
			Section section = new Section(off, 0);
			section.description = desc;
			section.sizeSet = false;

			list.add(section);
			return section;
		}

		public static Section addSection(String desc, int off, int size, int item) {
			Section section = new Section(off, size);
			section.description = desc;
			section.itemSize = item;
			section.sizeSet = true;
			section.itemSet = true;

			list.add(section);
			return section;
		}

		/**
		 * Compares this object with the specified object for order.  Returns a
		 * negative integer, zero, or a positive integer as this object is less than,
		 * equal to, or greater than the specified object.<p>
		 *
		 * @param o the Object to be compared.
		 * @return a negative integer, zero, or a positive integer as this object is
		 *         less than, equal to, or greater than the specified object.
		 * @throws ClassCastException if the specified object's type prevents it from
		 * being compared to this Object.
		 */
		public int compareTo(Section o) {
			if (offset < o.offset)
				return -1;
			else if (offset == o.offset)
				return 0;
			else
				return 1;
		}

		public static List<Section> getList() {
			return list;
		}

		public String getDescription() {
			return description;
		}

		public int getOffset() {
			return offset;
		}

		public int getLen() {
			return len;
		}

		public static Section getByName(String name) {
			for (Section s : list) {
				if (s.description.equals(name)) {
					return s;
				}
			}
			return null;
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();

			Formatter fmt = new Formatter(sb);
			fmt.format("%-20s| Start: %8x", description, offset);
			if (len > 0)
				fmt.format(", End %8x, Len: %8x (%d)", offset + len, len, len);
			if (itemSet)
				fmt.format(", items of %d", itemSize);
			
			return sb.toString();
		}

		public boolean isSizeSet() {
			return sizeSet;
		}

		public int getItemSize() {
			return itemSize;
		}
	}
}

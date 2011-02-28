/*
 * Copyright (C) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.mdr;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;

/**
 * A header for the map index, pointed to from MDR 1.
 * There is one of these for each map.
 * 
 * @author Steve Ratcliffe
 */
public class Mdr1SubHeader {
	// The number of subsections, this starts at 1.  Larger numbers are possible
	// for larger MDR header sizes.
	private static final int MAX_SECTION = 9;

	// Each sub header requires 8 bytes apart from sub2 which only needs 4.
	private static final int HEADER_SIZE = 70;

	private final Section[] sections = new Section[MAX_SECTION+1];

	public Mdr1SubHeader() {
		// Initialise the sections.
		for (int n = 1; n <= MAX_SECTION; n++) {
			Section prev = sections[n - 1];
			sections[n] = new Section(prev);
		}

		sections[1].setPosition(HEADER_SIZE);
	}

	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putChar((char) HEADER_SIZE);
		for (int n = 1; n <= MAX_SECTION; n++) {
			Section section = sections[n];

			// The second subsection does not have a length, because it always
			// has the same length as subsection 1.
			if (n == 2)
				writer.putInt(section.getPosition());
			else {
				//section.writeSectionInfo(writer);
				writer.putInt(section.getPosition());
				int size = section.getSize();
				if (size == 0)
					writer.putInt(0);
				else
					writer.putInt(size / section.getItemSize());
			}
		}
	}

	public void setEndSubsection(int sub, int pos) {
		sections[sub].setSize(pos - sections[sub].getPosition());
	}

	public long getHeaderLen() {
		return HEADER_SIZE;
	}

	public void setItemSize(int sectionNumber, int itemSize) {
		sections[sectionNumber].setItemSize((char) itemSize);
	}
}

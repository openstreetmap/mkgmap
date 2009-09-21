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
 * A sub header for section 1.
 * There are a number of subsections as follows:
 *
 * sub1 points into MDR 11 (POIs)
 * sub2 points into MDR 10 (POI types)
 * sub3 points into MDR 7 (street names)
 * sub4 points into MDR 5 (cities)
 * sub5
 * sub6
 * sub7
 * sub8
 *
 * @author Steve Ratcliffe
 */
public class Mdr1SubHeader {
	// The number of subsections, this starts at 1.  Larger numbers are possible
	// for larger MDR header sizes.
	private static final int MAX_SECTION = 9;

	// Each sub header requires 8 bytes apart from sub2 which only needs 4.
	private static final int HEADER_SIZE = 62;

	private final Section[] sections = new Section[MAX_SECTION];

	public Mdr1SubHeader() {
		// Initialise the sections.
		for (int n = 1; n < MAX_SECTION; n++) {
			Section prev = sections[n - 1];
			sections[n] = new Section(prev);
		}

		sections[1].setPosition(HEADER_SIZE);
	}

	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putChar((char) HEADER_SIZE);
		for (int n = 1; n < MAX_SECTION; n++) {
			Section section = sections[n];

			// The second subsection does not have a length, because it always
			// has the same length as subsection 1.
			if (n == 2)
				writer.putInt(section.getPosition());
			else
				section.writeSectionInfo(writer);
		}
	}
}

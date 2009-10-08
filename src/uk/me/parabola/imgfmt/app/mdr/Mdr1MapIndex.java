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

import java.nio.ByteBuffer;

import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Each map has one of these and it is used to provide a list of records
 * in the various sections that actually belong to this map.
 *
 * @author Steve Ratcliffe
 */
public class Mdr1MapIndex {
	private final Mdr1SubHeader subHeader = new Mdr1SubHeader();
	private final BufferedImgFileWriter subWriter = new BufferedImgFileWriter(null);

	private int pointerSize;

	public Mdr1MapIndex() {
		// skip over where the header will be
		this.subWriter.position(subHeader.getHeaderLen());
	}

	public void startSection(int n) {
		//int sn = sectionToSubsection(n);
		//if (sn != 0)
		//	subHeader.setStartSubsection(sn, subWriter.position());
	}

	public void endSection(int n) {
		int sn = sectionToSubsection(n);
		if (sn != 0)
			subHeader.setEndSubsection(sn, subWriter.position());
	}

	public void addPointer(int recordNumber) {
		switch (pointerSize) {
		case 3:
			subWriter.put3(recordNumber);
			break;
		case 2:
			subWriter.putChar((char) recordNumber);
			break;
		case 1:
			subWriter.put((byte) recordNumber);
			break;
		}
	}
	
	private int sectionToSubsection(int n) {
		int sn;
		switch (n) {
		case 11: sn = 1; break;
		case 10: sn = 2; break;
		case 7:  sn = 3; break;
		case 5:  sn = 4; break;
		case 6:  sn = 5; break;
		default: sn = 0; break;
		}
		return sn;
	}

	public void writeSubSection(ImgFileWriter writer) {
		subHeader.writeFileHeader(writer);

		ByteBuffer buffer = subWriter.getBuffer();
		byte[] bytes = buffer.array();
		int hl = (int) subHeader.getHeaderLen();
		writer.put(bytes, hl, buffer.position() - hl);
	}

	public void setPointerSize(int sectionNumber, int pointerSize) {
		this.pointerSize = pointerSize;
		int sn = sectionToSubsection(sectionNumber);
		if (sn != 0)
			subHeader.setItemSize(sn, pointerSize);
	}
}

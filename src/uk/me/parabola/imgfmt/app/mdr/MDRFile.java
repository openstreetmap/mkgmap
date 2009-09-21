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

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The MDR file.  This is embedded into a .img file, either its own
 * separate one, or as one file in the gmapsupp.img.
 *
 * @author Steve Ratcliffe
 */
public class MDRFile extends ImgFile {

	private final MDRHeader mdrHeader;

	// The sections
	private final Mdr1 mdr1;
	private final Mdr5 mdr5;
	private final Mdr13 mdr13;
	private final Mdr14 mdr14;
	private final Mdr15 mdr15;

	public MDRFile(ImgChannel chan, MdrConfig config) {
		mdrHeader = new MDRHeader(config.getHeaderLen());
		setHeader(mdrHeader);
		if (config.isWritable()) {
			setWriter(new BufferedImgFileWriter(chan));

			// Position at the start of the writable area.
			position(mdrHeader.getHeaderLength());
		} else {
			setReader(new BufferedImgFileReader(chan));
			mdrHeader.readHeader(getReader());
		}

		mdr1 = new Mdr1(config);
		mdr5 = new Mdr5(config);
		mdr13 = new Mdr13(config);
		mdr14 = new Mdr14(config);
		mdr15 = new Mdr15(config);
	}

	public void addMap(int mapName) {
		mdr1.addMap(mapName);
	}

	public void addCountry(int mapIndex, int countryIndex, String name) {
		int strOff = createString(name);
		mdr14.addCountry(mapIndex, countryIndex, strOff);
	}

	public void addRegion(int mapIndex, int regionIndex, String name) {
		int strOff = createString(name);
		mdr13.addRegion(mapIndex, regionIndex, strOff);
	}

	public void addCity(int mapIndex, int cityIndex, String name) {
		int strOff = createString(name);
		mdr5.addCity(mapIndex, cityIndex, 0, strOff);
	}

	public void write() {
		ImgFileWriter writer = getWriter();
		writeSections(writer);

		// Now refresh the header
		position(0);
		getHeader().writeHeader(writer);
	}

	private void writeSections(ImgFileWriter writer) {

		mdr1.writeSubSections(writer);
		mdrHeader.setPosition(1, writer.position());

		mdr1.writeSectData(writer);
		mdrHeader.setItemSize(1, mdr1.getItemSize());
		mdrHeader.setEnd(1, writer.position());

		mdr5.writeSectData(writer);
		mdrHeader.setItemSize(5, mdr5.getItemSize());
		mdrHeader.setEnd(5, writer.position());

		writeSection(writer, 13, mdr13);
		writeSection(writer, 14, mdr14);
		writeSection(writer, 15, mdr15);

		//mdr14.writeSectData(writer);
		//mdrHeader.setEnd(14, writer.position());
		//
		//mdr15.writeSectData(writer);
		//mdrHeader.setEnd(15, writer.position());
	}

	private void writeSection(ImgFileWriter writer, int sectionNumber, MdrSection section) {
		section.writeSectData(writer);
		mdrHeader.setEnd(sectionNumber, writer.position());
	}

	private int createString(String str) {
		return mdr15.createString(str);
	}
}

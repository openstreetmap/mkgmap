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
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.trergn.Point;
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
	private final Mdr4 mdr4;
	private final Mdr5 mdr5;
	private final Mdr9 mdr9;
	private final Mdr10 mdr10;
	private final Mdr11 mdr11;
	private final Mdr13 mdr13;
	private final Mdr14 mdr14;
	private final Mdr15 mdr15;

	private int currentMap;

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

		// Initialise the sections
		mdr1 = new Mdr1(config);
		mdr4 = new Mdr4(config);
		mdr5 = new Mdr5(config);
		mdr9 = new Mdr9(config);
		mdr10 = new Mdr10(config);
		mdr11 = new Mdr11(config);
		mdr13 = new Mdr13(config);
		mdr14 = new Mdr14(config);
		mdr15 = new Mdr15(config);
	}

	/**
	 * Add a map to the index.  You must add the map, then all of the items
	 * that belong to it, before adding the next map.
	 * @param mapName The numeric name of the map.
	 */
	public void addMap(int mapName) {
		currentMap++;
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

	public void addCity(int mapIndex, int cityIndex, String name, int subdiv, int lblOffset) {
		//int strOff = createString(name);
		//mdr5.addCity(mapIndex, cityIndex, lblOffset, strOff);
		//
		//Mdr11Record poi = mdr11.addPoi(mapIndex, 1, subdiv, lblOffset, cityIndex, strOff);
		//mdr10.addPoiType(0xb, poi);
	}

	public void addPoint(Point point, boolean indexed) {
		assert currentMap > 0;

		Label label = point.getLabel();
		String name = label.getText();
		int strOff = createString(name);

		Mdr11Record poi = mdr11.addPoi(currentMap,
				point, name, strOff);

		mdr10.addPoiType(point.getType(), poi, indexed);

		mdr4.addType(point.getType());
	}

	public void write() {
		ImgFileWriter writer = getWriter();
		writeSections(writer);

		// Now refresh the header
		position(0);
		getHeader().writeHeader(writer);
	}

	private void writeSections(ImgFileWriter writer) {
		mdr10.setNumberOfPois(mdr11.getNumberOfPois());

		mdr1.writeSubSections(writer);
		mdrHeader.setPosition(1, writer.position());

		mdr1.writeSectData(writer);
		mdrHeader.setItemSize(1, mdr1.getItemSize());
		mdrHeader.setEnd(1, writer.position());

		writeSection(writer, 4, mdr4);
		//writeSection(writer, 5, mdr5);

		writeSection(writer, 9, mdr9);

		// We do 11 before 10, because 10 needs information that is only available
		// after 11 has run.
		writeSection(writer, 11, mdr11);
		writeSection(writer, 10, mdr10);

		//writeSection(writer, 13, mdr13);
		//writeSection(writer, 14, mdr14);
		writeSection(writer, 15, mdr15);
	}

	private void writeSection(ImgFileWriter writer, int sectionNumber, MdrSection section) {
		mdrHeader.setPosition(sectionNumber, writer.position());
		section.writeSectData(writer);
		int itemSize = section.getItemSize();
		if (itemSize > 0)
			mdrHeader.setItemSize(sectionNumber, itemSize);
		mdrHeader.setEnd(sectionNumber, writer.position());
	}

	private int createString(String str) {
		return mdr15.createString(str.toUpperCase());
	}
}

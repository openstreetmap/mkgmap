/*
 * Copyright (C) 2011.
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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.lbl.Zip;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * Section containing zip codes.
 *
 * We need: map number, zip index in map, pointer into MDR 15 for the string name.
 * 
 * @author WanMil
 */
public class Mdr6 extends MdrMapSection {

	private final List<Mdr6Record> zips = new ArrayList<Mdr6Record>();


	public Mdr6(MdrConfig config) {
		setConfig(config);
	}

	public void addZip(int mapIndex, Zip zip, int strOff) {
		Mdr6Record record = new Mdr6Record(zip);
		record.setMapIndex(mapIndex);
		record.setStringOffset(strOff);
		zips.add(record);
	}
	

	public void writeSectData(ImgFileWriter writer) {
		int zipSize = getPointerSize(); 
		
		List<SortKey<Mdr6Record>> sortKeys = MdrUtils.sortList(getConfig().getSort(), zips);
		for (SortKey<Mdr6Record> key : sortKeys) {
			Mdr6Record z = key.getObject();
			putMapIndex(writer, z.getMapIndex());
			putN(writer, zipSize, z.getZipIndex());
			putStringOffset(writer, z.getStringOffset());
		}
	}

	/**
	 * Enough bytes to represent the map number
	 * and the zip index and the string offset.
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize() + getPointerSize() + sizes.getStrOffSize();
	}

	public int getNumberOfItems() {
		return zips.size();
	}


	/**
	 * Known structure:
	 * bits 0-1: size of local zip index - 1 (all values appear to work)
	 * bits 2: if MDR 15 available
	 * @return The value to be placed in the header.
	 */
	public int getExtraValue() {
		return  ((getPointerSize()-1)&0x03) | 0x04;
	}
}

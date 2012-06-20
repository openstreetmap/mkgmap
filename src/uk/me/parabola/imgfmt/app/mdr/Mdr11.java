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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.imgfmt.app.trergn.Point;

/**
 * Holds all the POIs, including cities.  Arranged alphabetically by
 * the name.
 *
 * @author Steve Ratcliffe
 */
public class Mdr11 extends MdrMapSection {
	private List<Mdr11Record> pois = new ArrayList<Mdr11Record>();
	private Mdr10 mdr10;

	public Mdr11(MdrConfig config) {
		setConfig(config);
	}

	public Mdr11Record addPoi(int mapIndex, Point point, String name, int strOff) {
		Mdr11Record poi = new Mdr11Record();
		poi.setMapIndex(mapIndex);
		poi.setPointIndex(point.getNumber());
		poi.setSubdiv(point.getSubdiv().getNumber());
		poi.setLblOffset(point.getLabel().getOffset());
		poi.setName(name);
		poi.setStrOffset(strOff);

		pois.add(poi);
		return poi;
	}

	/**
	 * Sort and fill in the mdr10 information.
	 *
	 * The POI index contains individual references to POI by subdiv and index, so they are not
	 * de-duplicated in the index in the same way that streets and cities are.
	 */
	protected void preWriteImpl() {
		List<SortKey<Mdr11Record>> keys = MdrUtils.sortList(getConfig().getSort(), pois);

		pois.clear();
		for (SortKey<Mdr11Record> sk : keys) {
			Mdr11Record poi = sk.getObject();

			mdr10.addPoiType(poi);
			pois.add(poi);
		}
	}

	public void writeSectData(ImgFileWriter writer) {
		int count = 1;
		boolean hasStrings = hasFlag(2);
		for (Mdr11Record poi : pois) {
			addIndexPointer(poi.getMapIndex(), count);
			poi.setRecordNumber(count++);

			putMapIndex(writer, poi.getMapIndex());
			writer.put((byte) poi.getPointIndex());
			writer.putChar((char) poi.getSubdiv());
			writer.put3(poi.getLblOffset());
			if (poi.isCity())
				putRegionIndex(writer, poi.getRegionIndex());
			else
				putCityIndex(writer, poi.getCityIndex(), true);
			if (hasStrings)
				putStringOffset(writer, poi.getStrOffset());
		}
	}

	public int getItemSize() {
		PointerSizes sizes = getSizes();
		int size = sizes.getMapSize() + 6 + sizes.getCitySizeFlagged();
		if (hasFlag(0x2))
			size += sizes.getStrOffSize();
		return size;
	}

	protected int numberOfItems() {
		return pois.size();
	}

	public int getNumberOfPois() {
		return getNumberOfItems();
	}

	public int getExtraValue() {
		int mdr11flags = 0x11;
		PointerSizes sizes = getSizes();

		// two bit field for city bytes.  minimum size of 2
		int citySize = sizes.getCitySizeFlagged();
		if (citySize > 2)
			mdr11flags |= (citySize-2) << 2;

		if (isForDevice()) 
			mdr11flags |= 0x80;
		else 
			mdr11flags |= 0x2;
		
		return mdr11flags;
	}

	public List<Mdr8Record> getIndex() {
		List<Mdr8Record> list = new ArrayList<Mdr8Record>();
		for (int number = 1; number <= pois.size(); number += 10240) {
			String prefix = getPrefixForRecord(number);

			// need to step back to find the first...
			int rec = number;
			while (rec > 1) {
				String p = getPrefixForRecord(rec);
				if (!p.equals(prefix)) {
					rec++;
					break;
				}
				rec--;
			}

			Mdr12Record indexRecord = new Mdr12Record();
			indexRecord.setPrefix(prefix);
			indexRecord.setRecordNumber(rec);
			list.add(indexRecord);
		}
		return list;
	}

	/**
	 * Get the prefix of the name at the given record.
	 * @param number The record number.
	 * @return The first 4 (or whatever value is set) characters of the street
	 * name.
	 */
	private String getPrefixForRecord(int number) {
		Mdr11Record record = pois.get(number-1);
		int endIndex = MdrUtils.POI_INDEX_PREFIX_LEN;
		String name = record.getName();
		if (endIndex > name.length()) {
			StringBuilder sb = new StringBuilder(name);
			while (sb.length() < endIndex)
				sb.append('\0');
			name = sb.toString();
		}
		return name.substring(0, endIndex);
	}

	public void setMdr10(Mdr10 mdr10) {
		this.mdr10 = mdr10;
	}

	public void releaseMemory() {
		pois = null;
		mdr10 = null;
	}

	public List<Mdr11Record> getPois() {
		return new ArrayList<Mdr11Record>(pois);
	}
}

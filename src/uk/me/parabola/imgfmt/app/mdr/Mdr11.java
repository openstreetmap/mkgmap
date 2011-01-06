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
	private final List<Mdr11Record> pois = new ArrayList<Mdr11Record>();
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

	public void writeSectData(ImgFileWriter writer) {
		List<SortKey<Mdr11Record>> keys = MdrUtils.sortList(getConfig().getSort(), pois);

		// De-duplicate the street names so that there is only one entry
		// per map for the same name.
		pois.clear();
		Mdr11Record last = new Mdr11Record();
		for (SortKey<Mdr11Record> sk : keys) {
			Mdr11Record r = sk.getObject();
			if (r.getMapIndex() == last.getMapIndex() && r.getLblOffset() == last.getLblOffset())
				continue;
			last = r;
			pois.add(r);
		}

		int count = 1;
		for (Mdr11Record poi : pois) {
			addIndexPointer(poi.getMapIndex(), count);
			poi.setRecordNumber(count++);

			mdr10.addPoiType(poi);

			putMapIndex(writer, poi.getMapIndex());
			writer.put((byte) poi.getPointIndex());
			writer.putChar((char) poi.getSubdiv());
			writer.put3(poi.getLblOffset());
			if (poi.isCity())
				putRegionIndex(writer, poi.getRegionIndex());
			else
				putCityIndex(writer, poi.getCityIndex(), true);
			putStringOffset(writer, poi.getStrOffset());
		}
	}

	public int getItemSize() {
		PointerSizes sizes = getSizes();
		return sizes.getMapSize() + 6 + sizes.getCitySize() + sizes.getStrOffSize();
	}

	public int getNumberOfItems() {
		return pois.size();
	}

	public int getNumberOfPois() {
		return pois.size();
	}

	public int getExtraValue() {
		int mdr11flags = 0x13;
		PointerSizes sizes = getSizes();

		// two bit field for city bytes.  minimum size of 2
		int citySize = sizes.getCitySize();
		if (citySize > 2)
			mdr11flags |= (citySize-2) << 2;

		return mdr11flags;
	}

	public List<Mdr8Record> getIndex() {
		List<Mdr8Record> list = new ArrayList<Mdr8Record>();
		for (int number = 1; number <= pois.size(); number += 10240) {
			String prefix = getPrefixForRecord(number);

			// need to step back to find the first...
			int rec = number;
			while (--rec > 1) {
				String p = getPrefixForRecord(rec);
				if (!p.equals(prefix)) {
					rec++;
					break;
				}
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
}

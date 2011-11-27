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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * POIs ordered by type. Section 18 is the index into this.
 *
 * @author Steve Ratcliffe
 */
public class Mdr19 extends MdrSection implements HasHeaderFlags {
	private List<Mdr11Record> pois;
	private final List<Mdr18Record> poiTypes = new ArrayList<Mdr18Record>();

	public Mdr19(MdrConfig config) {
		setConfig(config);
	}

	/**
	 * Sort the pois by type.
	 */
	public void preWriteImpl() {
		Collections.sort(pois, new Comparator<Mdr11Record>() {
			public int compare(Mdr11Record o1, Mdr11Record o2) {
				int t1 = o1.getType();
				int t2 = o2.getType();
				if (t1 == t2)
					return 0;
				else if (t1 < t2)
					return -1;
				else
					return 1;
			}
		});
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		int n = getSizes().getPoiSizeFlagged();
		int flag = getSizes().getPoiFlag();
		
		String lastName = null;
		int lastType = -1;
		int record = 1;
		for (Mdr11Record p : pois) {
			int index = p.getRecordNumber();
			String name = p.getName();
			if (!name.equals(lastName)) {
				index |= flag;
				lastName = name;
			}
			putN(writer, n, index);

			int type = MdrUtils.fullTypeToNaturalType(p.getType());
			if (type != lastType) {
				Mdr18Record mdr18 = new Mdr18Record();
				mdr18.setType(type);
				mdr18.setRecord(record);
				poiTypes.add(mdr18);
				lastType = type;
			}
			record++;
		}

		Mdr18Record m18 = new Mdr18Record();
		m18.setRecord(record);
		m18.setType(~0);
		poiTypes.add(m18);
	}

	/**
	 * Release the copy of the pois. The other index is small and not worth
	 * worrying about.
	 */
	protected void releaseMemory() {
		pois = null;
	}

	/**
	 * Records in this section are record numbers into mdr 11 with a flag
	 * so has to be large enough for a flagged index.
	 *
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		return getSizes().getPoiSizeFlagged();
	}

	/**
	 * Method to be implemented by subclasses to return the number of items in the section. This will only be valid after
	 * the section is completely finished etc.
	 *
	 * @return The number of items in the section.
	 */
	protected int numberOfItems() {
		return pois.size();
	}

	/**
	 * Not yet known.
	 * 
	 * @return The correct value based on the contents of the section.  Zero if nothing needs to be done.
	 */
	public int getExtraValue() {
		return getSizes().getSize(19) - 1;
	}

	public void setPois(List<Mdr11Record> pois) {
		this.pois = pois;
	}

	public List<Mdr18Record> getPoiTypes() {
		return poiTypes;
	}
}

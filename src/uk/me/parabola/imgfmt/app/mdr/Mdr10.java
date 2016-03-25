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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * POI subtype with a reference to MDR11.
 * These are sorted into groups based on the type, and contain the
 * subtype.
 *
 * The mdr9 section contains an index to this section based on the
 * the type groups.
 *
 * @author Steve Ratcliffe
 */
public class Mdr10 extends MdrMapSection {
	// The maximum group number.  Note that this is 1 based, not 0 based.
	private static final int MAX_GROUP_NUMBER = 9;

	@SuppressWarnings({"unchecked"})
	private List<Mdr10Record>[] poiTypes = new ArrayList[MAX_GROUP_NUMBER+1];

	private int numberOfPois;

	public Mdr10(MdrConfig config) {
		setConfig(config);

		for (int i = 1; i <= MAX_GROUP_NUMBER; i++) {
			poiTypes[i] = new ArrayList<>();
		}
	}

	public void addPoiType(Mdr11Record poi) {
		Mdr10Record t = new Mdr10Record();

		int type = poi.getType();
		t.setSubtype(MdrUtils.getSubtypeOrTypeFromFullType(type));
		t.setMdr11ref(poi);

		int group = MdrUtils.getGroupForPoi(type);
		if (group == 0)
			return;
		poiTypes[group].add(t);
	}

	public void writeSectData(ImgFileWriter writer) {
		int count = 0;
		for (List<Mdr10Record> poiGroup : poiTypes) {
			if (poiGroup == null)
				continue;
			
			Collections.sort(poiGroup);

			String lastName = null;
			int lastSub = -1;
			for (Mdr10Record t : poiGroup) {

				count++;
				Mdr11Record mdr11ref = t.getMdr11ref();
				addIndexPointer(mdr11ref.getMapIndex(), count);
				
				writer.put((byte) t.getSubtype());
				int offset = mdr11ref.getRecordNumber();

				// Top bit actually represents a non-repeated name.  ie if
				// the bit is not set, then the name is the same as the previous
				// record.
				String name = mdr11ref.getName();
				boolean isNew = !(name.equals(lastName) && (t.getSubtype() == lastSub));
				putPoiIndex(writer, offset, isNew);
				lastName = name;
				lastSub = t.getSubtype();
			}
		}
	}

	/**
	 * Get a list of the group sizes along with the group index number.
	 * @return A map that is guaranteed to iterate in the correct order for
	 * writing mdr9. The key is the group number and the value is the
	 * number of entries in that group.
	 */
	public Map<Integer, Integer> getGroupSizes() {
		Map<Integer, Integer> m = new LinkedHashMap<>();

		for (int i = 1; i < MAX_GROUP_NUMBER; i++) {
			List<Mdr10Record> poiGroup = poiTypes[i];
			if (!poiGroup.isEmpty())
				m.put(i, poiGroup.size());
		}
		return m;
	}

	/**
	 * This does not have a record size.
	 * @return Always zero to indicate that there is not a record size.
	 */
	public int getItemSize() {
		return 0;
	}

	protected int numberOfItems() {
		return numberOfPois;
	}

	public void setNumberOfPois(int numberOfPois) {
		this.numberOfPois = numberOfPois;
	}

	protected void releaseMemory() {
		poiTypes = null;
	}

	public int getExtraValue() {
		// Nothing to do here
		return 0;
	}
}

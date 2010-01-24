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

import java.util.LinkedHashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * An index into mdr10.  There is a single byte group number followed by
 * the first record in mdr10 that belongs to that group.
 *
 * @author Steve Ratcliffe
 */
public class Mdr9 extends MdrSection {
	private final Map<Integer, Integer> index = new LinkedHashMap<Integer, Integer>();

	public Mdr9(MdrConfig config) {
		setConfig(config);
	}

	public void writeSectData(ImgFileWriter writer) {
		for (Map.Entry<Integer, Integer> ent : index.entrySet()) {
			int group = ent.getKey();
			writer.put((byte) group);
			writer.put3(ent.getValue());
		}
	}

	/**
	 * The item size is always 4 as far as we know.  For the non-device
	 * version anyway.
	 * @return The record size, which is always 4.   // XXX check for the device case
	 */
	public int getItemSize() {
		return 4;
	}

	public void setGroups(Map<Integer, Integer> groupSizes) {
		int offset = 1;
		for (Map.Entry<Integer, Integer> ent : groupSizes.entrySet()) {
			index.put(ent.getKey(), offset);
			offset += ent.getValue();
		}
	}
}
/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Dec 15, 2007
 */
package uk.me.parabola.imgfmt.app.typ;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Holds the shape stacking section.
 *
 * Deals with sorting everything correctly, so no need to sort in the input file.
 * 
 * @author Steve Ratcliffe
 */
public class ShapeStacking {
	private final SortedMap<Integer, DrawOrder> bar = new TreeMap<Integer, DrawOrder>();

	public void addPolygon(int level, int type, int subtype) {
		int levelType = (level << 16) + type;
		DrawOrder order = bar.get(levelType);
		if (order == null) {
			order = new DrawOrder((byte) type);
			bar.put(levelType, order);
		}
		
		order.addSubtype(subtype);
	}

	public void write(ImgFileWriter writer) {
		int lastLevel = 1;

		DrawOrder empty = new DrawOrder(0);

		for (Map.Entry<Integer, DrawOrder> ent : bar.entrySet()) {
			int level = (ent.getKey() >> 16) & 0xffff;
			DrawOrder order = ent.getValue();

			if (level != lastLevel) {
				empty.write(writer);
				lastLevel = level;
			}

			order.write(writer);
		}
	}

}

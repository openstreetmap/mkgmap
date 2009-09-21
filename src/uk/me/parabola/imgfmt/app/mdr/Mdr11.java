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

/**
 * Holds all the POIs.
 *
 * @author Steve Ratcliffe
 */
public class Mdr11 extends MdrSection {
	private final List<Mdr11Record> pois = new ArrayList<Mdr11Record>();

	public Mdr11(MdrConfig config) {
		setConfig(config);
	}

	public Mdr11Record addPoi(int mapIndex, int pointIndex, int subdiv,
		int lblOffset, int cityIndex, int strOff)
	{
		Mdr11Record poi = new Mdr11Record();
		poi.setMapIndex(mapIndex);
		poi.setPointIndex(pointIndex);
		poi.setSubdiv(subdiv);
		poi.setLblOffset(lblOffset);
		poi.setCityIndex(cityIndex);
		poi.setStrOffset(strOff);

		pois.add(poi);
		return poi;
	}

	public void writeSectData(ImgFileWriter writer) {
		for (Mdr11Record poi : pois) {
			putMapIndex(writer, poi.getMapIndex());
			putPointIndex(writer, poi.getPointIndex());
			writer.putChar((char) poi.getSubdiv());
			writer.put3(poi.getLblOffset());

			int index = poi.getCityIndex();
			if (index > 0)
				writer.putChar((char) (index | 0x8000));
			else
				writer.putChar((char) 0);
			writer.put3(poi.getStrOffset());
		}
	}

	private void putPointIndex(ImgFileWriter writer, int pointIndex) {
		writer.put((byte) pointIndex);
	}

	public int getItemSize() {
		return 12; // XXX it is variable
	}
}
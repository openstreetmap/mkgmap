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
package uk.me.parabola.imgfmt.mdxfmt;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.fs.ImgChannel;


/**
 * For reading the MDX file.
 *
 * @author Steve Ratcliffe
 */
public class MdxFileReader {
	private final ImgFileReader reader;
	private int numberOfMaps;

	private final List<MapInfo> maps = new ArrayList<MapInfo>();

	public MdxFileReader(ImgChannel chan) {
		this.reader = new BufferedImgFileReader(chan);

		readHeader();
		readMaps();
	}

	private void readMaps() {
		for (int i = 0; i < numberOfMaps; i++) {
			MapInfo info = new MapInfo();
			info.setHexMapname(reader.get4());
			info.setProductId((char) reader.get2u());
			info.setFamilyId((char) reader.get2u());
			info.setMapname(reader.get4());
			maps.add(info);
		}
	}

	private void readHeader() {
		reader.get4();
		reader.get2u();
		reader.get4();
		numberOfMaps = reader.get4();
	}

	public List<MapInfo> getMaps() {
		return maps;
	}
}

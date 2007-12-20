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
 * Create date: Dec 19, 2007
 */
package uk.me.parabola.imgfmt.mps;

import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

/**
 * This file is a description of the map set that is loaded into the
 * gmapsupp.img file and an index of the maps that it contains.
 *
 * It is different than all the other files that fit inside the gmapsupp file
 * in that it doesn't contain the common header.  So it does not extend ImgFile.
 *
 * @author Steve Ratcliffe
 */
public class MpsFile {
	private int productId;

	private ProductBlock product;
	private MapsetBlock mapset;
	private List<MapBlock> maps = new ArrayList<MapBlock>();

	private ImgChannel chan;

	public MpsFile(ImgChannel chan) {
		this.chan = chan;
	}

	public void sync() throws IOException {
		for (MapBlock map : maps) {
			map.write(chan);
		}

		mapset.write(chan);
		product.write(chan);
	}

	public void addMap(MapBlock map) {
		maps.add(map);
	}

	public void setProductInfo(int productId, int productVersion,
			String seriesName, String familyName)
	{
	}

	
}

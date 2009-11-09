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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.fs.ImgChannel;

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
	private String mapsetName = "OSM map set";

	private final List<ProductBlock> products = new ArrayList<ProductBlock>();
	private final List<MapBlock> maps = new ArrayList<MapBlock>();

	private final ImgChannel chan;

	public MpsFile(ImgChannel chan) {
		this.chan = chan;
	}

	public void sync() throws IOException {
		for (MapBlock map : maps)
			map.write(chan);

		for (ProductBlock block : products)
			block.write(chan);

		MapsetBlock mapset = new MapsetBlock();
		mapset.setName(mapsetName);
		mapset.write(chan);
	}

	public void addMap(MapBlock map) {
		maps.add(map);
	}

	public void addProduct(ProductBlock pb) {
		products.add(pb);
	}

	public void setMapsetName(String mapsetName) {
		this.mapsetName = mapsetName;
	}

	public void close() throws IOException {
		chan.close();
	}
}

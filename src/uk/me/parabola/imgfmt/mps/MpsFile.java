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
	//private int productVersion = 1;
	private String seriesName = "OSM map sets";
	private String familyName = "OSM maps";

	private final ProductBlock product = new ProductBlock();
	private final MapsetBlock mapset = new MapsetBlock();
	private final List<MapBlock> maps = new ArrayList<MapBlock>();

	private final ImgChannel chan;

	public MpsFile(ImgChannel chan) {
		this.chan = chan;
	}

	public void sync() throws IOException {
		product.setProduct(productId);

		product.setDescription(familyName); // XXX or seriesName
		mapset.setName(seriesName); // XXX or family

		for (MapBlock map : maps) {
			map.write(chan);
		}

		mapset.write(chan);
		product.write(chan);
	}

	public void addMap(MapBlock map) {
		maps.add(map);
	}

	public void setProductInfo(int productId, String seriesName, String familyName)
	{
		this.productId = productId;
		//this.productVersion = productVersion;
		this.seriesName = seriesName;
		this.familyName = familyName;
	}

	public void setProductId(int productId) {
		this.productId = productId;
	}

	//public void setProductVersion(int productVersion) {
	//	this.productVersion = productVersion;
	//}

	public void setSeriesName(String seriesName) {
		this.seriesName = seriesName;
	}

	public void setFamilyName(String familyName) {
		this.familyName = familyName;
	}

	public void close() throws IOException {
		chan.close();
	}
}

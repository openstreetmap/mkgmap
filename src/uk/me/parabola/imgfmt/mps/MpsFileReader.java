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

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
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
public class MpsFileReader {

	private final List<MapBlock> maps = new ArrayList<MapBlock>();
	private final List<ProductBlock> products = new ArrayList<ProductBlock>();

	private final ImgChannel chan;
	private final ImgFileReader reader;

	public MpsFileReader(ImgChannel chan) {
		this.chan = chan;
		this.reader = new BufferedImgFileReader(chan);

		readBlocks();
	}

	private void readBlocks() {
		byte type;
		while ((type = reader.get()) > 0) {
			int len = reader.getChar();

			switch (type) {
			case 0x4c:
				readMapBlock();
				break;
			case 0x46:
				readProductBlock();
				break;
			default:
				// We always know the length, so just read over it
				reader.get(len);
				break;
			}
		}
	}

	private void readMapBlock() {
		MapBlock block = new MapBlock();
		int val = reader.getInt();
		block.setIds(val >>> 16, val & 0xffff);
		block.setMapNumber(reader.getInt());
		block.setSeriesName(reader.getZString());
		block.setMapDescription(reader.getZString());
		block.setAreaName(reader.getZString());
		block.setHexNumber(reader.getInt());
		reader.getInt();
		maps.add(block);
	}

	private void readProductBlock() {
		ProductBlock block = new ProductBlock();
		block.setProductId(reader.getChar());
		block.setFamilyId(reader.getChar());
		block.setDescription(reader.getZString());
		products.add(block);
	}

	public List<MapBlock> getMaps() {
		return maps;
	}

	public List<ProductBlock> getProducts() {
		return products;
	}

	public void close() throws IOException {
		chan.close();
	}
}

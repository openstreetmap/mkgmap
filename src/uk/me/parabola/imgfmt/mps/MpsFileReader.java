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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.labelenc.CharacterDecoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
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
public class MpsFileReader implements Closeable {

	private final List<MapBlock> maps = new ArrayList<>();
	private final List<ProductBlock> products = new ArrayList<>();

	private final ImgChannel chan;
	private final ImgFileReader reader;
	private final CharacterDecoder decoder;
	private final int codePage;

	public MpsFileReader(ImgChannel chan, int codePage) {
		this.chan = chan;
		this.reader = new BufferedImgFileReader(chan);

		this.codePage = codePage;
		CodeFunctions funcs = CodeFunctions.createEncoderForLBL(0, codePage);
		decoder = funcs.getDecoder();

		readBlocks();
	}

	private void readBlocks() {
		byte type;
		while ((type = reader.get()) > 0) {
			int len = reader.get2u();

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
		MapBlock block = new MapBlock(codePage);
		int val = reader.get4();
		block.setIds(val >>> 16, val & 0xffff);
		block.setMapNumber(reader.get4());

		byte[] zString = reader.getZString();
		block.setSeriesName(decodeToString(zString));
		block.setMapDescription(decodeToString(reader.getZString()));
		block.setAreaName(decodeToString(reader.getZString()));
		block.setHexNumber(reader.get4());
		reader.get4();
		maps.add(block);
	}

	private void readProductBlock() {
		ProductBlock block = new ProductBlock(codePage);
		block.setProductId(reader.get2u());
		block.setFamilyId(reader.get2u());
		block.setDescription(decodeToString(reader.getZString()));
		products.add(block);
	}

	private String decodeToString(byte[] zString) {
		decoder.reset();
		for (byte b : zString)
			decoder.addByte(b);

		return decoder.getText().getText();
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

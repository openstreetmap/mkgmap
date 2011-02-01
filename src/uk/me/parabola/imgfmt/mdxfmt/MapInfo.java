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
package uk.me.parabola.imgfmt.mdxfmt;

import java.nio.ByteBuffer;

/**
 * Represents an individual file in the MDX file.
 *
 * I don't really understand the difference between what I call hex mapname
 * and mapname.  We shall always make them equal.
 *
 * There is no good reason to call it 'hexMapname' its just a name that stuck
 * I still don't know what the difference is. We always make them the same
 * but they can differ.
 */
public class MapInfo {
	private int hexMapname;
	private int mapname;
	private char familyId;
	private char productId;

	// XXX temp
	private String filename;
	private String innername;

	void write(ByteBuffer os)  {
		os.putInt(hexMapname);
		os.putChar(productId);
		os.putChar(familyId);
		os.putInt(mapname);
	}

	public int getHexMapname() {
		return hexMapname;
	}

	public void setHexMapname(int hexMapname) {
		this.hexMapname = hexMapname;
	}

	public void setMapname(int mapname) {
		this.mapname = mapname;
	}

	public void setFamilyId(char familyId) {
		this.familyId = familyId;
	}

	public void setProductId(char productId) {
		this.productId = productId;
	}

	public int getMapname() {
		return mapname;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public String getInnername() {
		return innername;
	}

	public void setInnername(String innername) {
		this.innername = innername;
	}
}

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

import uk.me.parabola.imgfmt.app.lbl.Zip;

/**
 * Holds information about a zip that will make its way into mdr 6.
 * 
 * @author WanMil
 */
public class Mdr6Record extends RecordBase implements NamedRecord {
	/** The zip index within its own map */
	private final int zipIndex;
	
	private final String name;
	private int stringOffset;

	public Mdr6Record(Zip zip) {
		zipIndex = zip.getIndex();
		name = zip.getLabel().getText();
	}

	public int getZipIndex() {
		return zipIndex;
	}

	public String getName() {
		return name;
	}
	
	public int getStringOffset() {
		return stringOffset;
	}

	public void setStringOffset(int stringOffset) {
		this.stringOffset = stringOffset;
	}
}

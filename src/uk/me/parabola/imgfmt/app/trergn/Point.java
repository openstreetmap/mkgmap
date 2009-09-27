/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 09-Dec-2006
 */
package uk.me.parabola.imgfmt.app.trergn;

import java.io.IOException;
import java.io.OutputStream;

import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.lbl.POIRecord;

/**
 * Represents a particular point object on a map.  A point has a type (town
 * restaurant etc) and a location as well as a name.
 *
 * A point belongs to a particular subdivision and cannot be interpreted without
 * it as all details are relative to the subdivision.
 *
 * @author Steve Ratcliffe
 */
public class Point extends MapObject {
	// Points can link to a POIRecord
	private POIRecord poi;

	public Point(Subdivision div) {
		setSubdiv(div);
	}

	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFileWriter file) {
		boolean hasSubtype = false;
		int type = getType();
		byte subtype = 0;
		if (type > 0xff) {
			if((type & 0xff) != 0) {
			    hasSubtype = true;
			    subtype = (byte) type;
			}
			type >>= 8;
		}

		file.put((byte) type);

		int off = getLabel().getOffset();
		if (poi != null) {
			off = poi.getOffset();
			off |= 0x400000;
		}
		if (hasSubtype)
			off |= 0x800000;

		file.put3(off);
		file.putChar((char) getDeltaLong());
		file.putChar((char) getDeltaLat());
		if (hasSubtype)
			file.put(subtype);
	}

	@Deprecated // get rid of this again
	public void read(ImgFileReader file) {
		byte t = file.get();
		int off = file.get3();
		boolean hasSubtype = false;
		if ((off & 0x800000) != 0)
			hasSubtype = true;

		//Label l =
		//setLabel();
		setDeltaLong(file.getChar());
		setDeltaLat(file.getChar());
		
		if (hasSubtype) {
			byte st = file.get();
			setType(((t & 0xff) << 8) | (st & 0xff));
		} else {
			setType(t & 0xffff);
		}
	}

	/*
	 * write the point to an OutputStream - only use for outputting
	 * points with extended (3 byte) types.
	 *
	 */
	public void write(OutputStream stream) throws IOException {
		assert hasExtendedType();
		int type = getType();
		int labelOff = getLabel().getOffset();
		byte[] extraBytes = getExtTypeExtraBytes();

		if (poi != null) {
			labelOff = poi.getOffset();
			labelOff |= 0x400000;
		}
		if(labelOff != 0)
			type |= 0x20;		// has label
		if(extraBytes != null)
			type |= 0x80;		// has extra bytes
		stream.write(type >> 8);
		stream.write(type);

		int deltaLong = getDeltaLong();
		int deltaLat = getDeltaLat();
		stream.write(deltaLong);
		stream.write(deltaLong >> 8);
		stream.write(deltaLat);
		stream.write(deltaLat >> 8);

		if(labelOff != 0) {
			stream.write(labelOff);
			stream.write(labelOff >> 8);
			stream.write(labelOff >> 16);
		}

		if(extraBytes != null)
			stream.write(extraBytes);
	}

	public void setPOIRecord(POIRecord poirecord) {
		this.poi = poirecord;
	}
}

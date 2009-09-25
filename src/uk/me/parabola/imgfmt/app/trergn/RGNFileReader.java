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
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgReader;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * The region file.  Holds actual details of points and lines etc.
 *
 * This is the view of the file when it is being read.  Use {@link RGNFile}
 * for writing the file.
 *
 * The main focus of mkgmap is creating files, there are plenty of applications
 * that read and display the data, reading is implemented only to the
 * extent required to support creating the various auxiliary files etc.
 * 
 * @author Steve Ratcliffe
 */
public class RGNFileReader extends ImgReader {
	private static final Logger log = Logger.getLogger(RGNFileReader.class);

	private static final int HEADER_LEN = RGNHeader.HEADER_LEN;

	private final RGNHeader header = new RGNHeader();

	private Subdivision currentDivision;
	private int indPointPtrOff;
	private int polylinePtrOff;
	private int polygonPtrOff;
	private EnhancedProperties config;

	public RGNFileReader(ImgChannel chan) {
		setHeader(header);

		setReader(new BufferedImgFileReader(chan));
		header.readHeader(getReader());
	}

	public void config(EnhancedProperties props) {
		config = props;
	}
}
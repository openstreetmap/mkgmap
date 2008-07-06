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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * The header for the TYP file.
 * 
 * @author Steve Ratcliffe
 */
public class TYPHeader extends CommonHeader {
	public static final int HEADER_LEN = 91;  // 0x5b

	private byte[] unknown;

	// Not clear what a lot of this does, even things that are confidently named
	// may turn out to be wrong...
	private char headerId;
	private char productId;
	private final Section lineData = new Section();
	private final Section sect5 = new Section();
	private final Section sect1 = new Section();
	private final Section sect6 = new Section();
	private final Section sect3 = new Section();
	private final Section sect4 = new Section();
	private final Section shapeStacking = new Section();
	private char unk5;

	public TYPHeader() {
		super(HEADER_LEN, "GARMIN TYP");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) {
		// to compare that we have got it right.
		unknown = reader.get(HEADER_LEN - COMMON_HEADER_LEN);

		// Reset position for the real header reading code.
		reader.position(COMMON_HEADER_LEN);

		headerId = reader.getChar();

		sect5.setPosition(reader.getInt());
		sect5.setSize(reader.getInt());

		lineData.setPosition(reader.getInt());
		lineData.setSize(reader.getInt());

		sect1.setPosition(reader.getInt());
		sect1.setSize(reader.getInt());

		productId = reader.getChar();
		unk5 = reader.getChar();

		sect6.setPosition(reader.getInt());
		sect6.setItemSize(reader.getChar());
		sect6.setSize(reader.getInt());

		sect3.setPosition(reader.getInt());
		sect3.setItemSize(reader.getChar());
		sect3.setSize(reader.getInt());

		sect4.setPosition(reader.getInt());
		sect4.setItemSize(reader.getChar());
		sect4.setSize(reader.getInt());

		shapeStacking.setPosition(reader.getInt());
		shapeStacking.setItemSize(reader.getChar());
		shapeStacking.setSize(reader.getInt());
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		System.out.println("in file header write");
		writer.putChar(headerId);
		writer.putInt(sect5.getPosition());
		writer.putInt(sect5.getSize());

		writer.putInt(lineData.getPosition());
		writer.putInt(lineData.getSize());

		writer.putInt(sect1.getPosition());
		writer.putInt(sect1.getSize());

		writer.putChar(productId);
		writer.putChar(unk5);

		writer.putInt(sect6.getPosition());
		writer.putChar(sect6.getItemSize());
		writer.putInt(sect6.getSize());

		writer.putInt(sect3.getPosition());
		writer.putChar(sect3.getItemSize());
		writer.putInt(sect3.getSize());

		writer.putInt(sect4.getPosition());
		writer.putChar(sect4.getItemSize());
		writer.putInt(sect4.getSize());

		writer.putInt(shapeStacking.getPosition());
		writer.putChar(shapeStacking.getItemSize());
		writer.putInt(shapeStacking.getSize());
	}

	public byte[] getUnknown() {
		return unknown;
	}
}

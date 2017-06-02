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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgReader;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.labelenc.CharacterDecoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
import uk.me.parabola.imgfmt.app.labelenc.DecodedText;
import uk.me.parabola.imgfmt.app.lbl.LBLFileReader;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This is the file that contains the overview of the map.  There
 * can be different zoom levels and each level of zoom has an
 * associated set of subdivided areas.  Each of these areas then points
 * into the RGN file.
 *
 * The main focus of mkgmap is creating files, there are plenty of applications
 * that read and display the data, reading is implemented only to the
 * extent required to support creating the various auxiliary files etc.
 *
 * @author Steve Ratcliffe
 */
public class TREFileReader extends ImgReader {
	private Zoom[] mapLevels;
	private Subdivision[][] levelDivs;

	private static final Subdivision[] EMPTY_SUBDIVISIONS = new Subdivision[0];

	private final TREHeader header = new TREHeader();


	public TREFileReader(ImgChannel chan) {
		setHeader(header);

		setReader(new BufferedImgFileReader(chan));
		header.readHeader(getReader());
		readMapLevels();
		readSubdivs();
		readExtTypeOffsetsRecords();
	}

	public Area getBounds() {
		return header.getBounds();
	}

	public Zoom[] getMapLevels() {
		return mapLevels;
	}
	
	/**
	 * Return the subdivisions for the given level.
	 * @param level The level, 0 being the most detailed.  There may not be
	 * a level zero in the map.
	 * @return The subdivisions for the level. Never returns null; a zero length
	 * array is returned if there is no such level.
	 */
	public Subdivision[] subdivForLevel(int level) {
		for (int i = 0; i < mapLevels.length; i++) {
			if (mapLevels[i].getLevel() == level) {
				return levelDivs[i];
			}
		}
		return EMPTY_SUBDIVISIONS;
	}

	/**
	 * Read in the subdivisions.  There are a set of subdivision for each level.
	 */
	private void readSubdivs() {
		ImgFileReader reader = getReader();

		int start = header.getSubdivPos();
		int end = start + header.getSubdivSize();
		reader.position(start);

		int subdivNum = 1;
		int lastRgnOffset = reader.getu3();
		for (int count = 0; count < levelDivs.length && reader.position() < end; count++) {

			Subdivision[] divs = levelDivs[count];
			Zoom zoom = mapLevels[count];
			if (divs == null)
				break;

			for (int i = 0; i < divs.length; i++) {
				int flags = reader.get();
				int lon = reader.get3();
				int lat = reader.get3();
				int width = reader.getChar() & 0x7fff;
				int height = reader.getChar() & 0xffff;

				if (count < levelDivs.length-1)
					reader.getChar();

				int endRgnOffset = reader.getu3();

				SubdivData subdivData = new SubdivData(flags,
						lat, lon, 2*width, 2*height,
						lastRgnOffset, endRgnOffset);

				Subdivision subdiv = Subdivision.readSubdivision(mapLevels[count], subdivData);
				subdiv.setNumber(subdivNum++);
				
				divs[i] = subdiv;
				zoom.addSubdivision(subdiv);

				lastRgnOffset = endRgnOffset;
			}
		}
	}
	
	/**
	 * Read the extended type info for the sub divisions. Corresponds to {@link TREFile#writeExtTypeOffsetsRecords()}.
	 */
	private void readExtTypeOffsetsRecords() {
		ImgFileReader reader = getReader();
		int start = header.getExtTypeOffsetsPos();
		int end = start + header.getExtTypeOffsetsSize();
			
		reader.position(start);
		Subdivision sd = null;
		Subdivision sdPrev = null;
		for (int count = 0; count < levelDivs.length && reader.position() < end; count++) {
			Subdivision[] divs = levelDivs[count];
			if (divs == null)
				break;

			for (int i = 0; i < divs.length; i++) {
				sdPrev = sd;
				sd = divs[i];
				sd.readExtTypeOffsetsRecord(reader, sdPrev, header.getExtTypeSectionSize());
			}
		}
		if(sd != null && reader.position() < end) {
			sd.readLastExtTypeOffsetsRecord(reader, header.getExtTypeSectionSize());
		}
		
	}


	/**
	 * Read the map levels.  This is needed to make sense of the subdivision
	 * data.  Unlike in the write case, we just keep an array of zoom levels
	 * as found, there is no correspondence between the array index and level.
	 */
	private void readMapLevels() {
		ImgFileReader reader = getReader();

		int levelsPos = header.getMapLevelsPos();
		int levelsSize = header.getMapLevelsSize();
		reader.position(levelsPos);

		List<Subdivision[]> levelDivs = new ArrayList<Subdivision[]>();
		List<Zoom> mapLevels = new ArrayList<Zoom>();
		int end = levelsPos + levelsSize;
		while (reader.position() < end) {
			int level = reader.get();
			int nbits = reader.get();
			int ndivs = reader.getChar();

			Subdivision[] divs = new Subdivision[ndivs];
			levelDivs.add(divs);
			level &= 0x7f;

			Zoom z = new Zoom(level, nbits);
			mapLevels.add(z);
		}

		this.levelDivs = levelDivs.toArray(new Subdivision[levelDivs.size()][]);
		this.mapLevels = mapLevels.toArray(new Zoom[mapLevels.size()]);
	}

	public void config(EnhancedProperties props) {
		header.config(props);
	}

	public String[] getMapInfo(int codePage) {
		CodeFunctions funcs = CodeFunctions.createEncoderForLBL(0, codePage);
		CharacterDecoder decoder = funcs.getDecoder();

		// First do the ones in the TRE header gap
		ImgFileReader reader = getReader();
		reader.position(header.getHeaderLength());
		List<String> msgs = new ArrayList<>();
		while (reader.position() < header.getHeaderLength() + header.getMapInfoSize()) {
			byte[] m = reader.getZString();

			decoder.reset();
			for (byte b : m)
				decoder.addByte(b);

			DecodedText text = decoder.getText();
			String text1 = text.getText();

			msgs.add(text1);
		}


		return msgs.toArray(new String[msgs.size()]);
	}

	public String[] getCopyrights(LBLFileReader lblReader) {
		Section sect = header.getCopyrightSection();
		ImgFileReader reader = getReader();
		List<String> msgs = new ArrayList<>();

		long pos = sect.getPosition();
		while (pos < sect.getEndPos()) {
			reader.position(pos);
			int offset = reader.get3();
			Label label = lblReader.fetchLabel(offset);
			if (label != null) {
				msgs.add(label.getText());
			}
		
			pos += sect.getItemSize();
		}
		return msgs.toArray(new String[msgs.size()]);
	}
}

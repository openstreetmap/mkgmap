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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt.app.lbl;

import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.Exit;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.labelenc.BaseEncoder;
import uk.me.parabola.imgfmt.app.labelenc.CharacterEncoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
import uk.me.parabola.imgfmt.app.labelenc.Transliterator;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The file that holds all the labels for the map.
 *
 * Would be quite simple, but there are a number of sections that hold country,
 * region, city, etc. records.
 *
 * To begin with I shall only support regular labels.
 *
 * @author Steve Ratcliffe
 */
public class LBLFile extends ImgFile {
	private static final Logger log = Logger.getLogger(LBLFile.class);

	private CharacterEncoder textEncoder = CodeFunctions.getDefaultEncoder();
	private Transliterator transliterator = CodeFunctions.getDefaultTransliterator();

	private final Map<String, Label> labelCache = new HashMap<String, Label>();

	private final LBLHeader lblHeader = new LBLHeader();

	private final PlacesFile places = new PlacesFile();
	private Sort sort;

	public LBLFile(ImgChannel chan, Sort sort) {
		this.sort = sort;
		lblHeader.setSort(sort);
		setHeader(lblHeader);

		setWriter(new BufferedImgFileWriter(chan));

		position(LBLHeader.HEADER_LEN + lblHeader.getSortDescriptionLength());

		// The zero offset is for no label.
		getWriter().put((byte) 0);

		places.init(this, lblHeader.getPlaceHeader());
	}

	public void write() {
		writeBody();
	}

	public void writePost() {
		// Now that the body is written all the required offsets will be set up
		// inside the header, so we can go back and write it.
		ImgFileWriter writer = getWriter();
		getHeader().writeHeader(writer);

		// Text can be put between the header and the body of the file.
		writer.put(Utils.toBytes(sort.getDescription()));
		writer.put((byte) 0);
		assert writer.position() == LBLHeader.HEADER_LEN + lblHeader.getSortDescriptionLength();
	}

	private void writeBody() {
		// The label section has already been written, but we need to record
		// its size before doing anything else.
		lblHeader.setLabelSize(getWriter().position() - (LBLHeader.HEADER_LEN + lblHeader.getSortDescriptionLength()));
		places.write(getWriter());
	}

	public void setCharacterType(String cs, boolean forceUpper) {
		log.info("encoding type " + cs);
		CodeFunctions cfuncs = CodeFunctions.createEncoderForLBL(cs);
		
		lblHeader.setEncodingType(cfuncs.getEncodingType());
		textEncoder = cfuncs.getEncoder();
		transliterator = cfuncs.getTransliterator();
		if (forceUpper && textEncoder instanceof BaseEncoder) {
			BaseEncoder baseEncoder = (BaseEncoder) textEncoder;
			baseEncoder.setUpperCase(true);
		}
		if (forceUpper)
			transliterator.forceUppercase(true);
	}
	
	/**
	 * Add a new label with the given text.  Labels are shared, so that identical
	 * text is always represented by the same label.
	 *
	 * @param inText The text of the label, it will be in uppercase.
	 * @return A reference to the created label.
	 */
	public Label newLabel(String inText) {
		String text = transliterator.transliterate(inText);
		Label l = labelCache.get(text);
		if (l == null) {
			l = new Label(text);
			labelCache.put(text, l);

			l.setOffset(position() - (LBLHeader.HEADER_LEN + lblHeader.getSortDescriptionLength()));
			l.write(getWriter(), textEncoder);
		}

		return l;
	}

	public POIRecord createPOI(String name) {
		return places.createPOI(name);
	}

	public POIRecord createExitPOI(String name, Exit exit) {
		return places.createExitPOI(name, exit);
	}

	public POIIndex createPOIIndex(String name, int poiIndex, Subdivision group, int type) {
		return places.createPOIIndex(name, poiIndex, group, type);
	}
	
	public Country createCountry(String name, String abbr) {
		return places.createCountry(name, abbr);
	}
	
	public Region createRegion(Country country, String region, String abbr) {
	    return places.createRegion(country, region, abbr);
	}
	
	public City createCity(Region region, String city, boolean unique) {
		return places.createCity(region, city, unique);
	}

	public City createCity(Country country, String city, boolean unique) {
		return places.createCity(country, city, unique);
	}

	public Zip createZip(String code) {
		return places.createZip(code);
	}

	public Highway createHighway(Region region, String name) {
		return places.createHighway(region, name);
	}

	public ExitFacility createExitFacility(int type, char direction, int facilities, String description, boolean last) {
		return places.createExitFacility(type, direction, facilities, description, last);
	}

	public void allPOIsDone() {
		places.allPOIsDone();
	}

	public void setSort(Sort sort) {
		this.sort = sort;
		lblHeader.setSort(sort);
		places.setSort(sort);
	}

	public int numCities() {
		return places.numCities();
	}

	public int numZips() {
		return places.numZips();
	}
}

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

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BufferedWriteStrategy;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.WriteStrategy;
import uk.me.parabola.imgfmt.app.BufferedReadStrategy;
import uk.me.parabola.imgfmt.app.labelenc.AnyCharsetEncoder;
import uk.me.parabola.imgfmt.app.labelenc.CharacterEncoder;
import uk.me.parabola.imgfmt.app.labelenc.EncodedText;
import uk.me.parabola.imgfmt.app.labelenc.Format6Encoder;
import uk.me.parabola.imgfmt.app.labelenc.Latin1Encoder;
import uk.me.parabola.imgfmt.app.labelenc.Simple8Encoder;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

	private CharacterEncoder textEncoder = new Format6Encoder();

	private final Map<String, Label> labelCache = new HashMap<String, Label>();

	private final LBLHeader lblheader = new LBLHeader();

	public LBLFile(ImgChannel chan) {
		this(chan, true);
	}

	public LBLFile(ImgChannel chan, boolean write) {
		setHeader(lblheader);

		WriteStrategy writer = new BufferedWriteStrategy(chan);
		setWriter(writer);

		position(LBLHeader.HEADER_LEN + LBLHeader.INFO_LEN);

		// The zero offset is for no label.
		getWriter().put((byte) 0);
		       if (write) {
          setWriter(new BufferedWriteStrategy(chan));

           position(LBLHeader.HEADER_LEN + LBLHeader.INFO_LEN);

           // The zero offset is for no label.
           getWriter().put((byte) 0);
       } else {
				   setReader(new BufferedReadStrategy(chan));
           lblheader.readHeader(getReader());
           int type = lblheader.getEncodingType();
           setupDecoder(type);
       }

	}

	public void sync() throws IOException {
		log.debug("syncing lbl file");

		lblheader.setCountryPos(position());

		getHeader().writeHeader(getWriter());

		getWriter().put(Utils.toBytes("Some text for the label gap"));
		
		// Sync our writer.
		getWriter().sync();
	}

	public void setCharacterType(String cs) {
		log.info("encoding type " + cs);
		if ("ascii".equals(cs)) {
			lblheader.setEncodingType(LBLHeader.ENCODING_6BIT);
			textEncoder = new Format6Encoder();
		} else if ("latin1".equals(cs)) {
			lblheader.setEncodingType(LBLHeader.ENCODING_8BIT);
			textEncoder = new Latin1Encoder();

		} else if ("latin2".equals(cs)) {
			lblheader.setEncodingType(LBLHeader.ENCODING_8BIT);
//			textEncoder = new Latin2Encoder();
			textEncoder = new Format6Encoder();

		} else if ("simple8".equals(cs)) {
			lblheader.setEncodingType(LBLHeader.ENCODING_8BIT);
			textEncoder = new Simple8Encoder();
		} else {
			lblheader.setEncodingType(LBLHeader.ENCODING_8BIT);
			textEncoder = new AnyCharsetEncoder(cs);
		}
	}
	
	/**
	 * Add a new label with the given text.  Labels are shared, so that identical
	 * text is always represented by the same label.
	 *
	 * @param text The text of the label, it will be uppercased.
	 * @return A reference to the created label.
	 */
	public Label newLabel(String text) {
		Label l;
		EncodedText etext = textEncoder.encodeText(text);
		l = labelCache.get(text);
		if (l == null) {
			l = new Label(etext);
			labelCache.put(text, l);

			l.setOffset(position() - (LBLHeader.HEADER_LEN+ LBLHeader.INFO_LEN));
			l.write(getWriter());

			lblheader.setLabelSize(lblheader.getLabelSize() + l.getLength());
		}

		return l;
	}

	public void setCodePage(int codePage) {
		lblheader.setCodePage(codePage);
	}

	/**
	 * Bit of a shortcut to get a text string from the label file given its
	 * offset.
	 * @param offset Offset in the file.  These offsets are used in the other
	 * map files, such as RGN and NET.
	 * @return The label as a string.  Will be an empty string if there is no
	 * text for the label.  Note that this is particularly the case when the
	 * offset is zero.
	 */
	public String fetchLableString(int offset) {
		// Short cut the simple case of no label
		if (offset == 0)
			return "";  // or null ???

		ReadStrategy reader = getReader();
		reader.position(lblheader.getLabelStart() + offset);

		byte b;
		do {
			b = reader.get();
		} while (!textDecoder.addByte(b)) ;

		EncodedText text = textDecoder.getText();
		return new String(text.getCtext(), 0, text.getLength());
	}

	private void setupDecoder(int type) {
		switch (type) {
		case LBLHeader.ENCODING_8BIT:
			textDecoder = new SimpleDecoder();
			break;
		default:
			log.error("Decoder not implemented yet");
			break;
		}
	}

}

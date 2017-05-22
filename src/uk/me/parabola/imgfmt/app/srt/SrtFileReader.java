/*
 * Copyright (C) 2017.
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
package uk.me.parabola.imgfmt.app.srt;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.labelenc.CharacterDecoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileImgChannel;

/**
 * The sort file.
 * Work in progress.
 * 

 * @author Gerd Petermann
 *
 */
public class SrtFileReader extends ImgFile {
	private Sort sort;
	private final CharacterDecoder decoder;
	private SRTHeader header = new SRTHeader();
	private Section description = new Section();
	private Section tableHeader = new Section();
	private Section characterTable = new Section();
	private Section srt5 = new Section();
	private Section srt6 = new Section();
	private Section srt7 = new Section();
	private Section srt8 = new Section();
	List<CodePosition> expansions = new ArrayList<>();
	List<Map.Entry<Integer, CodePosition>> chars = new ArrayList<>();
	
	public SrtFileReader (ImgChannel chan) {
		CodeFunctions funcs = CodeFunctions.createEncoderForLBL("latin1");
		decoder = funcs.getDecoder();
		sort = new Sort();
		setHeader(header);
		setReader(new BufferedImgFileReader(chan));
		header.readHeader(getReader());
		
		readSrt1();
		readDesc();
		readTableHeader();
	}
	
	private void readTableHeader() {
		ImgFileReader reader = getReader();
		reader.position(tableHeader.getPosition());
		int len = reader.getChar();
		sort.setId1(reader.getChar());
		sort.setId2(reader.getChar());
		sort.setCodepage(reader.getChar());
		reader.getInt(); //?
		characterTable.readSectionInfo(reader, true);
		reader.position(reader.position() + 6); // padding?
		srt5.readSectionInfo(reader, true);
		reader.position(reader.position() + 6); // padding?
		if (len > 0x2c) {
			srt6.readSectionInfo(reader, false);
		}
		if (len > 0x34) {
			reader.getChar();
			int maxCode7 = reader.getInt();
			srt7.readSectionInfo(reader, true);
			reader.position(reader.position() + 6); // padding?
		}
		if (len > 0x44) {
			srt8.readSectionInfo(reader, true);
		}
		readCharacterTable();
		readExpansions();
		fillSort();
	}

	private void fillSort() {
		int posInSrt5 = 0;
		for (int i = 0; i <chars.size(); i++) {
			Entry<Integer, CodePosition> e = chars.get(i);
			int flags = e.getKey();
			int numExp = (flags >> 4) & 0xf;
			if (numExp > 0) {
				List<Integer> toExpand = new ArrayList<>();
//				for (int j = 0; j <= numExp; j++) {
//					CodePosition cp2 = expansions.get(posInSrt5++);
//					toExpand.add(e.)
//				}
//				sort.addExpansion(ch, inFlags, expansionList);
			}
		}
	}

	private void readCharacterTable() {
		ImgFileReader reader = getReader();
		reader.position(characterTable.getPosition());
		int rs = characterTable.getItemSize();
		long start = tableHeader.getPosition() + characterTable.getPosition();
		reader.position(start);
		for (int i = 1; i <= characterTable.getNumItems(); i++) {
			int flags = reader.get() & 0xff;
			CodePosition cp = readCharPosition(rs);
			chars.add(new AbstractMap.SimpleEntry<>(flags, cp));
			sort.add(i, cp.getPrimary(), cp.getSecondary(), cp.getTertiary(), flags);
		}
	}

	/**
	 * Read the sort position information.  The format varies depending on the posLength parameter.
	 *
	 * @param posLength The length of the position information (not the record length, just the
	 * part of it that encodes the positions).
	 */
	private CodePosition readCharPosition(int posLength) {
		ImgFileReader reader = getReader();
		CodePosition cp = new CodePosition();
		int rec;
		if (posLength == 2) {
			rec = reader.getChar();
			cp.setPrimary((char) (rec & 0xff));
			cp.setSecondary((byte) ((rec >> 8) & 0xf));
			cp.setTertiary((byte) ((rec >> 12) & 0xf));
			
		} else if (posLength == 3) {
			rec = reader.get3();
			cp.setPrimary((char) (rec & 0xff));
			cp.setSecondary((byte) ((rec >> 8) & 0xf));
			cp.setTertiary((byte) ((rec >> 12) & 0xf));
		} else if (posLength == 4) {
			rec = reader.getInt();
			cp.setPrimary((char) (rec & 0xffff));
			cp.setSecondary((byte) ((rec >> 16) & 0xf));
			cp.setTertiary((byte) ((rec >> 24) & 0xf));
		} else {
			throw new RuntimeException();
		}
		return cp;
	}

	private void readExpansions() {
		ImgFileReader reader = getReader();
		int reclen = srt5.getItemSize();
		reader.position(tableHeader.getPosition() + srt5.getPosition());
		for (int i = 0; i < srt5.getNumItems(); i++) {
			CodePosition cp = readCharPosition(reclen);
			expansions.add(cp);
		}
	
	}

	private void readSrt1() {
		ImgFileReader reader = getReader();
		reader.position(header.getHeaderLength());
		description.readSectionInfo(reader, false);
		tableHeader.readSectionInfo(reader, false);
	}

	private void readDesc() {
		getReader().position(description.getPosition());
		byte[] zString= getReader().getZString();
		sort.setDescription(decodeToString(zString));
	}
	
	private String decodeToString(byte[] zString) {
		decoder.reset();
		for (byte b : zString)
			decoder.addByte(b);

		return decoder.getText().getText();
	}
	/**
	 * Read in a sort description text file and create a SRT from it.
	 * @param args First arg is the text input file, the second is the name of the output file. The defaults are
	 * in.txt and out.srt.
	 */
	public static void main(String[] args) throws IOException {
		String infile = "we2.srt";
		if (args.length > 0)
			infile = args[0];

		String outfile = "out.srt";
		if (args.length > 1)
			outfile = args[1];
		ImgChannel chan = new FileImgChannel(outfile, "rw");
		ImgChannel inChannel = new FileImgChannel(infile, "r");
		SrtFileReader tr = new SrtFileReader(inChannel);
		SRTFile sf = new SRTFile(chan);
		Sort sort1 = tr.getSort();
		sf.setSort(sort1);
		sf.write();
		sf.close();
		chan.close();
	}

	private Sort getSort() {
		return sort;
	}
}

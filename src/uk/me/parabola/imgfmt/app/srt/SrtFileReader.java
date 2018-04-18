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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.labelenc.CharacterDecoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;

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
	private int countExp; 
	private final Map<Integer, Integer> offsetToBlock = new HashMap<>();
	
	public SrtFileReader (ImgChannel chan) {
		CodeFunctions funcs = CodeFunctions.createEncoderForLBL("latin1");
		decoder = funcs.getDecoder();
		sort = new Sort();
		setHeader(header);
		setReader(new BufferedImgFileReader(chan));
		header.readHeader(getReader());
		sort.setHeaderLen(header.getHeaderLength());
		readSrt1();
		readDesc();
		readTableHeader();
	}
	
	private void readTableHeader() {
		ImgFileReader reader = getReader();
		reader.position(tableHeader.getPosition());
		int len = reader.get2u();
		sort.setHeader3Len(len);
		sort.setId1(reader.get2u());
		sort.setId2(reader.get2u());
		sort.setCodepage(reader.get2u());
		if (sort.getCodepage() == 65001)
			sort.setMulti(true);
		reader.get4(); //?
		characterTable.readSectionInfo(reader, true);
		reader.position(reader.position() + 6); // padding?
		srt5.readSectionInfo(reader, true);
		reader.position(reader.position() + 6); // padding?
		if (len > 0x2c) {
			srt6.readSectionInfo(reader, false);
		}
		if (len > 0x34) {
			reader.get4();
			int maxCodeBlock = reader.get4();
			sort.setMaxPage(maxCodeBlock);
			srt7.readSectionInfo(reader, true);
			reader.position(reader.position() + 6); // padding?
		}
		if (len > 0x44) {
			srt8.readSectionInfo(reader, true);
		}
		readCharacterTable();
		if (srt7.getSize() > 0) {
			readSrt7();
			readSrt8();
		}
		readExpansions();
	}

	private void readSrt7() {
		ImgFileReader reader = getReader();
		reader.position(tableHeader.getPosition() + srt7.getPosition());

		int block = 1;
		for (int i = 0; i < srt7.getNumItems(); i++) {
			int val = reader.get4();
			if (val >= 0)
				offsetToBlock.put(val/srt8.getItemSize(), block);
			block++;
		}
		
	}

	private void readSrt8() {
		ImgFileReader reader = getReader();
		reader.position(tableHeader.getPosition() + srt8.getPosition());
		int reclen = srt8.getItemSize();
		int block = 1;
		for (int i = 0; i < srt8.getNumItems(); i++) {
			Integer nblock = offsetToBlock.get(i);
			if (nblock != null) 
				block = nblock;
			int flags = reader.get1u();
			CodePosition cp = readCharPosition(reclen-1);
			int ch = block*256 + (i % 256);
			
			if ((flags & 0xf0) != 0) { 
				sort.add(ch, countExp + 1, 0, 0, flags);
				countExp += ((flags >> 4) & 0xf) + 1;
			}
			else { 
				sort.add(ch, cp.getPrimary(), cp.getSecondary(), cp.getTertiary(), flags);
			}
		}
		
	}

	private void readCharacterTable() {
		ImgFileReader reader = getReader();
		reader.position(characterTable.getPosition());
		int rs = characterTable.getItemSize();
		long start = tableHeader.getPosition() + characterTable.getPosition();
		reader.position(start);
		for (int ch = 1; ch <= characterTable.getNumItems(); ch++) {
			int flags = reader.get1u();
			CodePosition cp = readCharPosition(rs-1);
			if ((flags & 0xf0) != 0) { 
				sort.add(ch, countExp + 1, 0, 0, flags);
				countExp += ((flags >> 4) & 0xf) + 1;
			}
			else { 
				sort.add(ch, cp.getPrimary(), cp.getSecondary(), cp.getTertiary(), flags);
			}
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
			rec = reader.get2u();
			cp.setPrimary((char) (rec & 0xff));
			cp.setSecondary((byte) ((rec >> 8) & 0xf));
			cp.setTertiary((byte) ((rec >> 12) & 0xf));
			
		} else if (posLength == 3) {
			rec = reader.get3u();
			cp.setPrimary((char) (rec & 0xff));
			cp.setSecondary((byte) ((rec >> 8) & 0xf));
			cp.setTertiary((byte) ((rec >> 12) & 0xf));
		} else if (posLength == 4) {
			rec = reader.get4();
			cp.setPrimary((char) (rec & 0xffff));
			cp.setSecondary((byte) ((rec >> 16) & 0xff));
			cp.setTertiary((byte) ((rec >> 24) & 0xff));
		} else {
			throw new RuntimeException("unexpected value posLength " + posLength);
		}
		return cp;
	}

	private void readExpansions() {
		ImgFileReader reader = getReader();
		int reclen = srt5.getItemSize();
		reader.position(tableHeader.getPosition() + srt5.getPosition());
		List<CodePosition> expansionList = new ArrayList<>(srt5.getNumItems());
		if (countExp != srt5.getNumItems()) {
			throw new RuntimeException("unexpected number of expansions " + srt5.getNumItems() + " expected: " + countExp);
		}
		for (int i = 0; i < srt5.getNumItems(); i++) {
			CodePosition cp = readCharPosition(reclen);
			expansionList.add(cp);
		}
		sort.setExpansions(expansionList);
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
	
	public Sort getSort() {
		return sort;
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
		try {
			Files.delete(Paths.get(outfile, ""));
		} catch (Exception e) {
		}
		ImgChannel inChannel = null;
		FileSystem fs = null;
		try {
			if (infile.endsWith("srt"))
				inChannel = new FileImgChannel(infile, "r");
			else {
				fs = ImgFS.openFs(infile);
				List<DirectoryEntry> entries = fs.list();

				// Find the TRE entry
				String mapname = null;
				for (DirectoryEntry ent : entries) {
					if ("SRT".equals(ent.getExt())) {
						mapname = ent.getName();
						break;
					}
				}
				inChannel = fs.open(mapname + ".SRT", "r");
				
				ImgChannel chan = new FileImgChannel(outfile, "rw");
				SrtFileReader tr = new SrtFileReader(inChannel);
				tr.close();
				Sort sort1 = tr.getSort();
				SRTFile sf = new SRTFile(chan);
				sf.setSort(sort1);
				sf.write();
				sf.close();
				chan.close();
			}
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file: " + infile);
		} finally {
			if (fs != null) {
				fs.close();
			}
		}

	}
}

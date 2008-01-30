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
 * Create date: Nov 15, 2007
 */
package uk.me.parabola.mkgmap.combiners;

import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.trergn.TREFile;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.log.Logger;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;

/**
 * Used for holding information about an individual file that will be made into
 * a gmapsupp file.
 *
 * @author Steve Ratcliffe
 */
public class FileInfo {
	private static final Logger log = Logger.getLogger(FileInfo.class);

	// The file is an img file ie. it contains several subfiles.
	public static final int IMG_KIND = 0;
	// The file is a plain file and it doesn't need to be extracted from a .img
	public static final int FILE_KIND = 1;
	// The file is of an unknown or unsupported kind, and so it should be ignored.
	public static final int UNKNOWN_KIND = 99;

	private static final List<String> KNOWN_FILE_TYPE_EXT = Arrays.asList(
			"TRE", "RGN", "LBL", "NET", "NOD",
			"TYP"
	);

	// The name of the file.
	private String filename;

	// The kind of file, see *KIND definitions above.
	private final int kind;

	private String mapname;
	private String description;

	// If this is an img file, the size of various sections.
	private int rgnsize;
	private int tresize;
	private int lblsize;

	private List<Integer> fileSizes = new ArrayList<Integer>();
	private static final int ENTRY_SIZE = 240;

	private FileInfo(String filename, int kind) {
		this.filename = filename;
		this.kind = kind;
	}

	// The area covered by the map, if it is a IMG file
	private Area bounds;

	public String getMapname() {
		return mapname;
	}

	public void setMapname(String mapname) {
		this.mapname = mapname;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getRgnsize() {
		return rgnsize;
	}

	public void setRgnsize(int rgnsize) {
		this.rgnsize = rgnsize;
	}

	public int getTresize() {
		return tresize;
	}

	public void setTresize(int tresize) {
		this.tresize = tresize;
	}

	public int getLblsize() {
		return lblsize;
	}

	public void setLblsize(int lblsize) {
		this.lblsize = lblsize;
	}

	public Area getBounds() {
		return bounds;
	}

	/**
	 * Create a file info the the given file.
	 *
	 * @param inputName The filename to examine.
	 * @return The FileInfo structure giving information about the file.
	 * @throws FileNotFoundException If the file doesn't actually exist.
	 */
	public static FileInfo getFileInfo(String inputName) throws FileNotFoundException {

		int end = inputName.length();
		String ext = inputName.substring(end - 3).toUpperCase(Locale.ENGLISH);
		FileInfo info;

		if (ext.equals("IMG")) {
			info = imgInfo(inputName);
		} else if (KNOWN_FILE_TYPE_EXT.contains(ext)) {
			info = typInfo(inputName);
		} else {
			info = new FileInfo(inputName, UNKNOWN_KIND);
		}

		return info;
	}

	/**
	 * A TYP file, perhaps not a special enough case..
	 * @param inputName The input file name.
	 */
	private static FileInfo typInfo(String inputName) {
		FileInfo info = new FileInfo(inputName, FILE_KIND);
		return info;
	}

	/**
	 * An IMG file, this involves real work. We have to read in the file and
	 * extract several pieces of information from it.
	 *
	 * @param inputName The name of the file.
	 * @return The informaion obtained.
	 * @throws FileNotFoundException If the file doesn't exist.
	 */
	private static FileInfo imgInfo(String inputName) throws FileNotFoundException {
		FileSystem imgFs = ImgFS.openFs(inputName);

		FileSystemParam params = imgFs.fsparam();
		log.info("Desc", params.getMapDescription());
		log.info("Blocksize", params.getBlockSize());

		FileInfo info = new FileInfo(inputName, IMG_KIND);
		info.setFilename(inputName);
		info.setDescription(params.getMapDescription());

		List<DirectoryEntry> entries = imgFs.list();
		for (DirectoryEntry ent : entries) {
			if (ent.isSpecial())
				continue;

			log.info("file", ent.getFullName());
			String ext = ent.getExt();

			if ("TRE".equals(ext)) {
				info.setTresize(ent.getSize());
				info.setMapname(ent.getName());

				ImgChannel treChan = imgFs.open(ent.getFullName(), "r");
				TREFile treFile = new TREFile(treChan, false);
				Area area = treFile.getBounds();
				info.setBounds(area);
				//info.set
				treFile.close();
			} else if ("RGN".equals(ext)) {
				int size = ent.getSize();
				info.setRgnsize(size);
			} else if ("LBL".equals(ext)) {
				info.setLblsize(ent.getSize());
			}

			// add to the total size based on the rounded up size of this file
			info.fileSizes.add(ent.getSize());
		}
		return info;
	}

	private void setBounds(Area area) {
		this.bounds = area;
	}

	private void setFilename(String filename) {
		this.filename = filename;
	}

	public String getFilename() {
		return filename;
	}

	public boolean isImg() {
		return kind == IMG_KIND;
	}

	public int getKind() {
		return kind;
	}

	/**
	 * Get the number of blocks required at a particular block size.
	 * Each subfile will need at least one block and so we go through each
	 * separately and round up for each and return the total.
	 *
	 * @param blockSize The block size.
	 * @return The number of blocks that would be needed for all the subfiles
	 * in this .img file.
	 */
	public int getNumHeaderSlots(int blockSize) {
		int totHeaderSlots = 0;
		for (int size : fileSizes) {
			// You use up one header slot for every 240 blocks with a minimum
			// of one slot
			int nblocks = (size + (blockSize-1)) / blockSize;
			totHeaderSlots += (nblocks + (ENTRY_SIZE - 1)) / ENTRY_SIZE;
		}
		return totHeaderSlots;
	}

	public int getMapnameAsInt() {
		try {
			return Integer.valueOf(mapname);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

}

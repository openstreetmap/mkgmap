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
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.TREFile;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.log.Logger;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * Used for holding information about an individual file that will be made into
 * a gmapsupp file.
 *
 * @author Steve Ratcliffe
 */
public class FileInfo {
	private static final Logger log = Logger.getLogger(FileInfo.class);

	private String mapname;
	private String description;
	private int rgnsize;
	private int tresize;
	private int lblsize;
	private Area bounds;
	private String filename;

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

	public int getSize() {
		return lblsize + rgnsize + tresize;
	}

	public Area getBounds() {
		return bounds;
	}

	public static FileInfo getFileInfo(String inputName) throws FileNotFoundException {

		FileSystem imgFs = ImgFS.openFs(inputName);

		FileSystemParam params = imgFs.fsparam();
		log.info("Desc", params.getMapDescription());
		log.info("Blocksize", params.getBlockSize());

		FileInfo info = new FileInfo();
		info.filename = inputName;
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
				System.out.println("The map name determined was " + info.getMapname());
				System.out.println("tre size " + ent.getSize());
				ImgChannel treChan = imgFs.open(ent.getFullName(), "r");
				TREFile treFile = new TREFile(treChan, false);
				Area area = treFile.getBounds();
				info.setBounds(area);
				//info.set
				treFile.close();
			} else if ("RGN".equals(ext)) {
				int size = ent.getSize();
				info.setRgnsize(size);
				System.out.println("rgn size " + size);
			} else if ("LBL".equals(ext)) {
				info.setLblsize(ent.getSize());
				System.out.println("lbl size " + ent.getSize());
			}
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
}

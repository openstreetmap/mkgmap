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

/**
 * Used for holding information about an individual file that will be made into
 * a gmapsupp file.
 *
 * @author Steve Ratcliffe
 */
public class FileInfo {
	private String mapname;
	private String description;
	private int rgnsize;
	private int tresize;
	private int lblsize;

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
}

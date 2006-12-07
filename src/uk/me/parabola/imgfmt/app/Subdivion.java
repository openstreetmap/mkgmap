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
 * Create date: 07-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

/**
 * @author Steve Ratcliffe
 */
public class Subdivion {
	private int rgnPointer;
	
	private boolean hasPoints;
	private boolean hasIndPoints;
	private boolean hasPolylines;
	private boolean hasPolygons;

	// The location of the central point
	private int longitude;
	private int latitude;

	private int width;
	private int height;

	// This is set if the last one for a level XXX maybe work it out.
	boolean last;

	public Subdivion(int latitude, int longitude, int width, int height) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.width = width;
		this.height = height;
	}

	public void save(ImgFile ifile) {
		ifile.put3(rgnPointer);
	}

}

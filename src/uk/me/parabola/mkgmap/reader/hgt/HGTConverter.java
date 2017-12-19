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
package uk.me.parabola.mkgmap.reader.hgt;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;

/**
 * Very basic routine to find the correct hgt reader for a given coordinate.
 * @author Gerd Petermann
 *
 */
public class HGTConverter {
	final static double factor = 45.0d / (1<<29);
	
	private HGTReader[][] readers;
	final int minLat32;
	final int minLon32;
	final int res;
	public HGTConverter(String path, Area bbox) {
		int minLat = (int) Math.floor(Utils.toDegrees(bbox.getMinLat()));
		int minLon = (int) Math.floor(Utils.toDegrees(bbox.getMinLong()));
		int maxLat = (int) Math.ceil(Utils.toDegrees(bbox.getMaxLat()));
		int maxLon = (int) Math.ceil(Utils.toDegrees(bbox.getMaxLong()));
		minLat32 = Utils.toMapUnit(minLat) * 256; 
		minLon32 = Utils.toMapUnit(minLon) * 256; 
		// create matrix of readers 
		int dimLat = maxLat - minLat;
		int dimLon = maxLon - minLon;
		readers = new HGTReader[dimLat][dimLon];
		int maxRes = -1;
		for (int row = 0; row < dimLat; row++) {
			for (int col = 0; col < dimLon; col++) {
				HGTReader rdr = new HGTReader(row + minLat, col + minLon, path);
				readers[row][col] = rdr;
				maxRes = Math.max(maxRes, rdr.getRes()); 
			}
		}
		res = maxRes; // we use the highest available res
		return;
	}
	
	
	/**
	 * Return elevation in meter for a point given in DEM units (32 bit res).
	 * @param lat32
	 * @param lon32
	 * @return height in m or Short.MIN_VALUE if value is invalid 
	 */
	public short getElevation(int lat32, int lon32) {
		// TODO: maybe calculate the borders in 32 bit res ?
//		double lon = lon32 * factor;
//		double lat = lat32 * factor;
		int row = (int) ((lat32 - minLat32) * factor);
		int col = (int) ((lon32 - minLon32) * factor);
		HGTReader rdr = readers[row][col];
		int res = rdr.getRes();
		double f = res * factor;
		
		int y = ((int) Math.round((lat32 - minLat32) * f));
		int x = ((int) Math.round((lon32 - minLon32) * f));
		x -= col * res;
		y -= row * res;
		short rc = rdr.ele(x,y);
		return rc;
	}

	public void stats() {
		for (int i = 0; i < readers.length; i++) {
			for (int j = 0; j < readers[i].length; j++) {
				System.out.println(readers[i][j]);
			}
			
		}
	}

	public int getRes() {
		return res;
	}

	
}

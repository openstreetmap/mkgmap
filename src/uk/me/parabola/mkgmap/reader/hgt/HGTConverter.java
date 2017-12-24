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

import java.util.Arrays;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;

/**
 * Very basic routine to find the correct hgt reader for a given coordinate.
 * @author Gerd Petermann
 *
 */
public class HGTConverter {
	final static double FACTOR = 45.0d / (1<<29);
	
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
		int row = (int) ((lat32 - minLat32) * FACTOR);
		int col = (int) ((lon32 - minLon32) * FACTOR);
		HGTReader rdr = readers[row][col];
		int res = rdr.getRes();
		if (res <= 0)
			return 0;
		double scale  = res * FACTOR;
		
		
		double y1 = (lat32 - minLat32) * scale - row * res;
		double x1 = (lon32 - minLon32) * scale - col * res;
		int xLeft = (int) x1;
		int yBelow = (int) y1;
		int xRight = xLeft + 1;
		int yTop = yBelow + 1;
		
		int hLT = rdr.ele(xLeft, yTop);
		int hRT = rdr.ele(xRight, yTop);
		int hLB = rdr.ele(xLeft, yBelow);
		int hRB = rdr.ele(xRight, yBelow);
		
		double rc = interpolatedHeightInNormatedRectangle(x1-xLeft, y1-yBelow, hLT, hRT, hRB, hLB);
		if (rc == HGTReader.UNDEF) {
			int sum = 0;
			int valid = 0;
			for (int h : Arrays.asList(hLT,hRT,hLB,hRB)) {
				if (h == HGTReader.UNDEF)
					continue;
				valid++;
				sum += h;	
			}
			if(valid >= 2)
				rc = Math.round((double)sum/valid);
		}
		short rc2 = (short) Math.round(rc); 
//		double lon = lon32 * factor;
//		double lat = lat32 * factor;
//		System.out.println(String.format("%.7f %.7f: (%.2f) %d", lat,lon,rc,rc2));
//		if (Math.round(rc2) != (short) rc2) {
//			long dd = 4;
//		}
		return rc2;
	}
	
	/**
	 * Interpolate the height of point p from the 4 closest values in the hgt matrix.
	 * Code is a copy from Frank Stinners program BuildDEMFile (Hgtreader.cs) 
	 * @param qx value from 0 .. 1 gives relative x position in matrix 
	 * @param qy value from 0 .. 1 gives relative y position in matrix
	 * @param hlt height left top
	 * @param hrt height right top
	 * @param hrb height right bottom
	 * @param hlb height left bottom
	 * @return the interpolated height
	 */
	double interpolatedHeightInNormatedRectangle(double qx, double qy, int hlt, int hrt, int hrb, int hlb) {
		if (hlb == HGTReader.UNDEF || hrt == HGTReader.UNDEF)
			return HGTReader.UNDEF; // keine Berechnung möglich

		/*
		 * In welchem Dreieck liegt der Punkt? oben +-/ |/
		 * 
		 * unten /| /-+
		 */
		if (qy >= qx) { // oberes Dreieck aus hlb, hrt und hlt (Anstieg py/px
						// ist größer als height/width)

			if (hlt == HGTReader.UNDEF)
				return HGTReader.UNDEF;

			// hlt als Koordinatenursprung normieren; mit hrt und hlb 3 Punkte
			// einer Ebene (3-Punkt-Gleichung)
			hrt -= hlt;
			hlb -= hlt;
			qy -= 1;

			return hlt + qx * hrt - qy * hlb;

		} else { // unteres Dreieck aus hlb, hrb und hrt

			if (hrb == HGTReader.UNDEF)
				return HGTReader.UNDEF;

			// hrb als Koordinatenursprung normieren; mit hrt und hlb 3 Punkte
			// einer Ebene (3-Punkt-Gleichung)
			hrt -= hrb;
			hlb -= hrb;
			qx -= 1;

			return hrb - qx * hlb + qy * hrt;
		}
	}

	public void stats() {
		for (int i = 0; i < readers.length; i++) {
			for (int j = 0; j < readers[i].length; j++) {
				System.out.println(readers[i][j]);
			}
			
		}
	}

	public int getHighestRes() {
		return res;
	}

	
}

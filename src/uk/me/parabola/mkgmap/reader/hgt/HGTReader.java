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

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

/**
 * Very simple code to read a single HGT file with SRTM data. Based on old code in class HGTDEM 
 * in package uk.me.parabola.mkgmap.reader.dem which was removed in 2017.
 * @author Gerd Petermann
 *
 */
public class HGTReader {
	
	private MappedByteBuffer buffer;
	private int res = 1200;
	public final static short UNDEF = Short.MIN_VALUE;
	public final String fileName;
	private long count;
	public HGTReader(int lat, int lon, String pathToHGT) {
		fileName = String.format("%s/%s%02d%s%03d.hgt", pathToHGT, 
				lat < 0 ? "S" : "N", lat < 0 ? -lat : lat, 
						lon > 0 ? "E" : "W", lon < 0 ? -lon : lon);
		
		try (FileInputStream is = new FileInputStream(fileName)){
		    res = 1200;
		    if (is.getChannel().size() != 2*(res+1)*(res+1)) 
		    	res = 3600;
	    	buffer = is.getChannel().map(READ_ONLY, 0, 2*(res+1)*(res+1));
		}
		catch (Exception e) {
		    throw new RuntimeException("failed to open " + fileName, e);
		} 
	}
	
	public short ele(int x, int y) {
//		if (x < 0 || x > res || y < 0 || y > res) {
//			throw new RuntimeException("wrong x/y value for res" + res + " x=" + x + " y=" + y);
//		}
		count++;
		return buffer.getShort(2 * ((res - y) * (res + 1) + x));
		
	}

	public int getRes() {
		return res;
	}
	
	@Override
	public String toString() {
		return fileName + " (" + count + " reads)" ;
	}
}

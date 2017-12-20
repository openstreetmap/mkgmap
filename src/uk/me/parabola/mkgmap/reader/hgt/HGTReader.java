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
	private int res;
	public final static short UNDEF = Short.MIN_VALUE;
	public final String fileName;
	private long count;
	public HGTReader(int lat, int lon, String pathToHGT) {
		fileName = String.format("%s/%s%02d%s%03d.hgt", pathToHGT, 
				lat < 0 ? "S" : "N", lat < 0 ? -lat : lat, 
						lon > 0 ? "E" : "W", lon < 0 ? -lon : lon);
		try (FileInputStream is = new FileInputStream(fileName)) {
			res = 1200;
			if (is.getChannel().size() != expectedFileSize())
				res = 3600;
			if (is.getChannel().size() != expectedFileSize()) {
				buffer = null;
				res = 0;
				System.err.println("file " +  fileName +  " has unexpected size " + is.getChannel().size() + " and is ignored");
			} else
				buffer = is.getChannel().map(READ_ONLY, 0, expectedFileSize());
		} catch (Exception e) {
			System.err.println("check: " + fileName  + " should be sea only or is missing");
			
		}
	}
	
	public short ele(int x, int y) {
		if (x < 0 || x > res || y < 0 || y > res) {
			throw new RuntimeException("wrong x/y value for res" + res + " x=" + x + " y=" + y);
		}
		count++;
		if (buffer == null)
			return 0;
		return buffer.getShort(2 * ((res - y) * (res + 1) + x));
		
	}

	public int getRes() {
		return res;
	}

	private int expectedFileSize () {
		return 2*(res+1)*(res+1);
	}
	@Override
	public String toString() {
		return fileName + " (" + count + " reads)" ;
	}
}

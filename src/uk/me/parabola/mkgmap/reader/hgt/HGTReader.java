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
import java.util.HashSet;
import java.util.Set;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.mkgmap.reader.osm.SeaGenerator;

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
	
	private final static Set<String> missing = new HashSet<>();
	
	/**
	 * Class to read a single HGT file. 
	 * @param lat in degrees, -90 .. 90
	 * @param lon - -180..180
	 * @param dirsWithHGT comma separated list of directories to search for *.hgt files
	 */
	public HGTReader(int lat, int lon, String dirsWithHGT) {
		String name = String.format("%s%02d%s%03d",
				lat < 0 ? "S" : "N", lat < 0 ? -lat : lat, 
						lon > 0 ? "E" : "W", lon < 0 ? -lon : lon);
		
		String[] dirs = dirsWithHGT.split("[,]");
		String fName = ".";
		boolean knwonAsMissing;
		synchronized (missing) {
			knwonAsMissing = missing.contains(name); 
		}
		if (!knwonAsMissing) { 
			for (String dir : dirs) {
				fName = Utils.joinPath(dir, name, "hgt");
				try (FileInputStream is = new FileInputStream(fName)) {
					res = 1200;
					if (is.getChannel().size() != expectedFileSize(res))
						res = 3600;
					if (is.getChannel().size() != expectedFileSize(res)) {
						buffer = null;
						res = 1200;
						System.err.println("file " +  fName +  " has unexpected size " + is.getChannel().size() + " and is ignored");
					} else
						buffer = is.getChannel().map(READ_ONLY, 0, expectedFileSize(res));
					break;
				} catch (Exception e) {
				}
			}
			if (buffer == null) {
				synchronized (missing){
					missing.add(name);	
				}
				
				System.err.println("hgt file " + name + " not found. Is expected to cover sea.");
			}
		}
		fileName = (buffer != null) ? fName : name;
		
	}
	
	/**
	 * HGT files are organised as a matrix of n*n (short) values giving the elevation in metres.
	 * Invalid values are coded as 0x8000 = -327678 = Short.MIN_VALUE.
	 * @param x index for column west to east 
	 * @param y index for row north to south
	 * @return the elevation value stored in the file or 0 if 
	 */
	public short ele(int x, int y) {
		if (buffer == null)
			return 0;
		if (x < 0 || x > res || y < 0 || y > res) {
			throw new RuntimeException("wrong x/y value for res" + res + " x=" + x + " y=" + y);
		}
		count++;
		return buffer.getShort(2 * ((res - y) * (res + 1) + x));
		
	}

	/**
	 * @return the resolution to use with this file
	 */
	public int getRes() {
		return res;
	}

	private int expectedFileSize (int res) {
		return 2*(res+1)*(res+1);
	}
	
	@Override
	public String toString() {
		return fileName + " (" + count + " reads)" ;
	}
}

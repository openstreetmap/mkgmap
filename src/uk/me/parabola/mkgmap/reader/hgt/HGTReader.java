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
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.log.Logger;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

/**
 * Very simple code to read a single HGT file with SRTM data. Based on old code in class HGTDEM 
 * in package uk.me.parabola.mkgmap.reader.dem which was removed in 2017.
 * @author Gerd Petermann
 *
 */
public class HGTReader {
	private static final Logger log = Logger.getLogger(HGTReader.class);

	private ByteBuffer buffer;
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
	 * Supported are also zip files containing *.hgt files and directories containing *.hgt.zip.
	 */
	public HGTReader(int lat, int lon, String dirsWithHGT) {
		String name = String.format("%s%02d%s%03d.hgt",
				lat < 0 ? "S" : "N", lat < 0 ? -lat : lat, 
						lon < 0 ? "W" : "E", lon < 0 ? -lon : lon);
		
		String[] dirs = dirsWithHGT.split("[,]");
		String fName = ".";
		boolean knwonAsMissing;
		synchronized (missing) {
			knwonAsMissing = missing.contains(name); 
		}
		if (!knwonAsMissing) { 
			for (String dir : dirs) {
				if (dir.endsWith(".zip")) {
					try(ZipFile zipFile = new ZipFile(dir)){
						ZipEntry entry = zipFile.getEntry(name);
						if (entry != null){ 
							extractFromZip(zipFile, entry);
							if (buffer != null)
								break;
						}
					} catch (IOException exp) {
					} 
				}
				fName = Utils.joinPath(dir, name);
				try (FileInputStream is = new FileInputStream(fName)) {
					res = calcRes(is.getChannel().size());
					if (res < 0) {
						log.error("file " +  fName +  " has unexpected size " + is.getChannel().size() + " and is ignored");
					} else
						buffer = is.getChannel().map(READ_ONLY, 0, is.getChannel().size());
					break;
				} catch (IOException e) {
				}
				try(ZipFile zipFile = new ZipFile(fName + ".zip")){
					ZipEntry entry = zipFile.getEntry(name);
					if (entry != null){
						extractFromZip(zipFile, entry);
						if (buffer != null)
							break;
					}
				} catch (IOException exp) {
					buffer = null;
				}
			}
			if (buffer == null) {
				res = -1;
				synchronized (missing){
					missing.add(name);	
				}
				log.warn("file " + name + " not found. Is expected to cover sea.");
			}
		}
		fileName = (buffer != null) ? fName : name;
	}
	
	/**
	 * Try to unzip the file contained in a zip file.
	 * @param zipFile
	 * @param entry
	 * @throws IOException
	 */
	private void extractFromZip(ZipFile zipFile, ZipEntry entry) throws IOException {
		try(InputStream is = zipFile.getInputStream(entry)){
			res = calcRes(entry.getSize());
			if (res < 0) {
				log.error("file " +  entry.getName() +  " has unexpected size " + entry.getSize() + " and is ignored");
				return;
			}
			log.info("extracting data for " + entry.getName() + " from " + zipFile.getName());
			buffer = ByteBuffer.allocate((int) entry.getSize());
			byte[] ioBuffer = new byte[1024];
			int len = is.read(ioBuffer);
			while (len != -1) {
				buffer.put(ioBuffer, 0, len);
				len = is.read(ioBuffer);
			}
		}
	}

	/**
	 * calculate the resolution of the hgt file. size should be exactly 2 * (res+1) * (res+1) 
	 * @param size number of bytes
	 * @return resolution (typically 1200 for 3'' or 3600 for 1'')
	 */
	private int calcRes(long size) {
		long numVals = (long) Math.sqrt(size/2);
		if (2 * numVals*numVals  == size)
			return (int) (numVals - 1);
		return -1;
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
	 * @return the resolution to use with this file, -1 is return if file is invalid
	 */
	public int getRes() {
		return res;
	}
	
	@Override
	public String toString() {
		return fileName + " (" + count + " reads)" ;
	}
}

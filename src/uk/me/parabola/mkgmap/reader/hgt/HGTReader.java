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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.log.Logger;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

/**
 * Rather simple code to read a single HGT file with SRTM data. Based on old code in class HGTDEM 
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
	public String path;
	public boolean read;
	private long count;

	
	private final static Map<String,Set<String>> missingMap = new HashMap<>();
	private final static Set<String> badDir = new HashSet<>();
	
	/**
	 * Class to read a single HGT file. 
	 * @param lat in degrees, -90 .. 90
	 * @param lon - -180..180
	 * @param dirsWithHGT comma separated list of directories to search for *.hgt files
	 * Supported are also zip files containing *.hgt files and directories containing *.hgt.zip.
	 */
	public HGTReader(int lat, int lon, String dirsWithHGT) {
		String baseName = String.format("%s%02d%s%03d",
				lat < 0 ? "S" : "N", lat < 0 ? -lat : lat, 
						lon < 0 ? "W" : "E", lon < 0 ? -lon : lon);
		
 		String[] dirs = dirsWithHGT.split("[,]");
		fileName = baseName + ".hgt";
		String fName = ".";
		boolean knownAsMissing = false;
		synchronized (missingMap) {
			Set<String> missingSet = missingMap.get(dirsWithHGT);
			if (missingSet != null)
				knownAsMissing = missingSet.contains(fileName);
		}
		if (!knownAsMissing) {
			for (String dir : dirs) {
				dir = dir.trim();
				File f = new File (dir);
				if (!f.exists()) {
					synchronized (badDir) {
						if (badDir.add(dir))
							log.error("extracted path >" + dir + "< does not exist, check option dem:", dirsWithHGT);
					}
					continue;
				}
				if (f.isDirectory()) {
					fName = Utils.joinPath(dir, fileName);
					try (FileInputStream fis = new FileInputStream(fName)) {
						res = calcRes(fis.getChannel().size(), fName);
						if (res >= 0) {
							path = fName;
						}
						break;
					} catch (FileNotFoundException e) {
					} catch (IOException e) {
						log.error("failed to get size for file", fName);
					}
					fName += ".zip";
					checkZip(fName, fileName); // try to find *.hgt.zip in dir that contains *.hgt
					if (res > 0) {
						path = fName;
						return;
					}
					fName = Utils.joinPath(dir, baseName) + ".zip";
					checkZip(fName, fileName); // try to find *.hgt.zip in dir that contains *.hgt
					if (res > 0) {
						path = fName;
						return;
					}
				} else {
					if (dir.endsWith(".zip")) {
						checkZip(dir, fileName); // try to find *.hgt in zip file
					}
				}
				if (res > 0) {
					path = dir;
					return;
				}
			}
			if (res <= 0 || path == null) {
				res = -1;
				path = null;
				synchronized (missingMap){
					Set<String> missingSet = missingMap.get(dirsWithHGT);
					if (missingSet == null) { 
						missingSet = new HashSet<>();
						missingMap.put(dirsWithHGT, missingSet);
					}
					missingSet.add(fileName);
				}
				HGTList hgtList = HGTList.get();
				if (hgtList != null) {
					if (hgtList.shouldExist(lat, lon))
						System.err.println(this.getClass().getSimpleName() + ": file " + fileName + " not found but it should exist. Height values will be 0.");
					return;
				} else { 
					log.warn("file " + fileName + " not found. Is expected to cover sea.");
				}
			}
		}
	}
	
	/**
	 * try to find the needed file. Different hgt providers use slightly different methods to
	 * pack their data. 
	 * @param zipFile
	 * @param name
	 * @return
	 */
	private ZipEntry findZipEntry (ZipFile zipFile, String name) {
		ZipEntry entry = zipFile.getEntry(name);
		if (entry == null) {
			// no direct hit, try to recurse through all files
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				entry = entries.nextElement();
				if (!entry.isDirectory() && entry.getName().toUpperCase().endsWith(name.toUpperCase())) {
					return entry;
				}
			}
			return null;
		}
		return entry;
	}
	
	/**
	 * Check if we can find the wanted file in a zip container and if it has the right size. 
	 * @param fName path to container
	 * @param name wanted file
	 */
	private void checkZip(String fName, String name) {
		try(ZipFile zipFile = new ZipFile(fName)){
			ZipEntry entry = findZipEntry(zipFile, name);
			if (entry != null){
				res = calcRes(entry.getSize(), entry.getName());
			}
		} catch (FileNotFoundException e) {
		} catch (IOException exp) {
			log.error("failed to get size for file", name, "from", fName);
		}
	}

	/**
	 * Try to unzip the file contained in a zip file.
	 * @param zipFile
	 * @param entry
	 * @throws IOException
	 */
	private void extractFromZip(String fName, String name) throws IOException {
		try (ZipFile zipFile = new ZipFile(fName)) {
			ZipEntry entry = findZipEntry(zipFile, name);
			if (entry != null) {
				InputStream is = zipFile.getInputStream(entry);
				log.info("extracting data for " + entry.getName() + " from " + zipFile.getName());
				buffer = ByteBuffer.allocate((int) entry.getSize());
				byte[] ioBuffer = new byte[1024];
				int len = is.read(ioBuffer);
				while (len != -1) {
					buffer.put(ioBuffer, 0, len);
					len = is.read(ioBuffer);
				}
			} else {
				throw new FileNotFoundException(name);
			}
			read = true;
		} 
	}

	/**
	 * calculate the resolution of the hgt file. size should be exactly 2 * (res+1) * (res+1) 
	 * @param size number of bytes
	 * @param fname file name (for error possible message)
	 * @return resolution (typically 1200 for 3'' or 3600 for 1'')
	 */
	private int calcRes(long size, String fname) {
		long numVals = (long) Math.sqrt(size/2);
		if (2 * numVals*numVals  == size)
			return (int) (numVals - 1);
		log.error("file", fname, "has unexpected size", size, "and is ignored");
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
		if (!read && path != null) {
			prepRead();
		}
		if (buffer == null)
			return 0;
		assert (x >= 0 && x <= res && y >= 0 && y <= res) : "wrong x/y value for res" + res + " x=" + x + " y=" + y;
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
		return fileName + " (" + count + " reads) " + res ;
	}

	/**
	 * Return memory to GC. 
	 * @return true if heap memory was freed.
	 */
	public boolean freeBuf() {
		if (buffer == null)
			return false;
		buffer = null;
		read = false;
		return true;
	}

	public void prepRead() {
		if (!read && path != null) {
			try {
				if (count == 0)
					log.info("allocating buffer for", fileName);
				else 
					log.warn("re-allocating buffer for", fileName);
				if (path.endsWith(".zip"))
					extractFromZip(path, fileName);
				else {
					try (FileInputStream is = new FileInputStream(path)) {
						buffer = is.getChannel().map(READ_ONLY, 0, is.getChannel().size());
						read = true;
					}
				}
			} catch (FileNotFoundException e) {
				throw new MapFailedException("previously existing file is missing: " + path);
			} catch (IOException e) {
				log.error("failed to create buffer for file", path);
			}

		}
	}
	
}

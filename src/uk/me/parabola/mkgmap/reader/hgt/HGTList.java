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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.me.parabola.log.Logger;

/**
 * @author Gerd Petermann
 *
 */
public class HGTList {
	private static final Logger log = Logger.getLogger(HGTList.class);

	private final static HGTList instance = new HGTList();
	private static BitSet knownHgt;
	
	private HGTList()
	{
		try {
			knownHgt = loadConfig();
		} catch (IOException e) {
			e.printStackTrace();
		}
	} 
	
	public static HGTList get() {
		return instance;
	} 
	public BitSet getKnownHGT() {
		return knownHgt;
	} 
	
 	private BitSet loadConfig() throws IOException
 	{
		try {
			String name = "hgt/known-hgt.txt";
			BitSet bs = compileHGTList(name);
			if (bs != null) {
				System.out.println("HGTList uses " + name);
				return bs; 
			}
		} catch (Exception ex) {
			
		}

		InputStream inStream = this.getClass().getResourceAsStream("/known-hgt.bin");
		byte[] buf = new byte[180 * 360 / 8];
		int totalBytesRead = 0;

		while (totalBytesRead < buf.length) {
			int bytesRemaining = buf.length - totalBytesRead;
			// input.read() returns -1, 0, or more :
			int bytesRead = inStream.read(buf, totalBytesRead, bytesRemaining);
			if (bytesRead < 0)
				break;
			if (bytesRead > 0) {
				totalBytesRead = totalBytesRead + bytesRead;
			}
		}
		inStream.close();
		return BitSet.valueOf(buf);
 	}
 	
	/**
	 * Create a BitSet containing the information about available hgt files. The input file is assumed to contain
	 * one name of a hgt file per line.
	 * @param filename the path to the human readable file
	 * @return the {@link BitSet} 
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	private static BitSet compileHGTList(String filename) throws FileNotFoundException, IOException {
		final Pattern hgtPattern =  Pattern.compile("([sSnN])(\\d{2})([eEwW])(\\d{3}).*");
		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
			BitSet bs = new BitSet(180*360);
			String strLine;
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				if (strLine.isEmpty() || strLine.startsWith("#"))
					continue;
				Matcher m = hgtPattern.matcher(strLine);
				if (!m.matches()) {
					log.error("don't know how to handle ", strLine);
					continue;
				}
				try {
					String sn = m.group(1).toUpperCase();
					int lat = Integer.parseInt(m.group(2));
					String ew = m.group(3).toUpperCase();
					int lon = Integer.parseInt(m.group(4));
					if ("S".equals(sn))
						lat = -lat;
					if ("W".equals(ew))
						lon = -lon;
					
					bs.set(getBitSetPos(lat, lon));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
			}
			return bs;
		}
	}
	
	private static int getBitSetPos (int lat, int lon) {
		assert lat >= -90 && lat < 90 && lon >= -180 && lon < 180;
		return (90 + lat) * 360 + lon + 180;
	}
	
	public synchronized boolean shouldExist(int lat, int lon) {
		if (knownHgt != null)
			return knownHgt.get(getBitSetPos(lat, lon));
		return false;
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length >= 2 && "compile".equals(args[0])) {
			BitSet bs = compileHGTList(args[1]);
			String outName = (args.length > 2) ? args[2] : "known-hgt.bin";
			
			RandomAccessFile raf = new RandomAccessFile(outName, "rw");
			raf.write(bs.toByteArray());
			raf.close();
		} else {
			System.out.println("usage: HGTList compile hgt-list [outfile name]");
		} 
		
	}

}

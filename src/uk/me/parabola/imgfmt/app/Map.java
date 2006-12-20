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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.sys.FileSystem;
import uk.me.parabola.imgfmt.FileSystemParam;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;

/**
 * Holder for a complete map.  A map is made up of several files.
 * <p>Needless to say, it has nothing to do with java.util.Map.
 *
 * @author Steve Ratcliffe
 */
public class Map {
	private static final Logger log = Logger.getLogger(Map.class);

	private FileSystem fileSystem;

	private TREFile treFile;
	private RGNFile rgnFile;
	private LBLFile lblFile;


	// Use createMap() or loadMap() instead of creating a map directly.
	private Map() {
	}

	/**
	 * Create a complete map.  This consists of (at least) three
	 * files that all have the same basename and different extensions.
	 *
	 * @return A map object that holds together all the files that make it up.
	 * @param mapname The name of the map.  This is an 8 digit number as a
	 * string.
	 * @param params Parameters that describe the file system that the map
	 * will be created in.
	 */
	public static Map createMap(String mapname, FileSystemParam params) {
		Map m = new Map();
		try {
			m.fileSystem = new FileSystem(mapname + ".img", params);
			m.rgnFile = new RGNFile(m.fileSystem.create(mapname + ".RGN"));
			m.treFile = new TREFile(m.fileSystem.create(mapname + ".TRE"));
			m.lblFile = new LBLFile(m.fileSystem.create(mapname + ".LBL"));

			m.treFile.setMapId(Integer.parseInt(mapname));

		} catch (FileNotFoundException e) {
			log.error("failed to create file", e);
			return null;
		}

		return m;
	}

	/**
	 * Close this map by closing all the constituent files.
	 */
	public void close() {
		treFile.setLastRgnPos(rgnFile.position() - 29);
		
		rgnFile.close();
		treFile.close();
		lblFile.close();

		fileSystem.close();
	}

	public TREFile getTRE() {
		return treFile;
	}

	public LBLFile getLBL() {
		return lblFile;
	}

	public RGNFile getRGN() {
		return rgnFile;
	}
}

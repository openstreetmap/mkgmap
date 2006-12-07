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

import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileSystem;
import uk.me.parabola.imgfmt.FileExistsException;
import org.apache.log4j.Logger;

/**
 * Holder for a complete map.  A map is made up of several files.
 * <p>Needless to say, it has nothing to do with java.util.Map.
 *
 * @author Steve Ratcliffe
 */
public class Map {
	static private Logger log = Logger.getLogger(Map.class);

	private TREFile treFile;
	private RGNFile rgnFile;
	private LBLFile lblFile;


	// Use createMap() or loadMap() instead of creating a map directly.
	private Map() {
	}

	/**
	 * Create a complete map.  This consists of (at least) three
	 * files that all have the same basename and different extensions.
	 * TODO: is this needed?
	 */
	public static Map createMap(FileSystem fs, String name) {
		Map m = new Map();
		try {
			m.rgnFile = new RGNFile(fs.create(name + ".RGN"));
			m.treFile = new TREFile(fs.create(name + ".TRE"));
			m.lblFile = new LBLFile(fs.create(name + ".LBL"));
		} catch (FileExistsException e) {
			log.error("failed to create file", e);
			return null;
		}

		return m;
	}

	/**
	 * Close this map by closing all the constituent files.
	 */
	public void close() {
		rgnFile.close();
		treFile.close();
		lblFile.close();
	}

	public TREFile getTRE() {
		return treFile;
	}
}

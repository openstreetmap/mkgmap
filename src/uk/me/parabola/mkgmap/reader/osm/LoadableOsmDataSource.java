/*
 * Copyright (C) 2013.
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
package uk.me.parabola.mkgmap.reader.osm;

import java.io.InputStream;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;

/**
 * 
 * @author Gerd
 *
 */
public interface LoadableOsmDataSource extends LoadableMapDataSource{
	/**
	 * Load osm data from open stream.  
	 * You would implement this interface to allow reading data from
	 * zipped files.
	 *
	 * @param is the already opened stream.
	 * @throws FormatException For any kind of malformed input.
	 */
	
	void load(InputStream is) throws FormatException;
}

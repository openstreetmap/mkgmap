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
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap;

import java.io.FileNotFoundException;

/**
 * @author Steve Ratcliffe
 */
public interface MapSource {

	/**
	 * Set the object that will collect requests to add particular features
	 * to the map.
	 * @param mapper The object that collects mapping features.
	 */
	public void setMapCollector(MapCollector mapper);

	/**
	 * Load map by name.  The name is in a suitable form to be recognised
	 * by the particular map source.  It could be a file name or a URI.
	 *
	 * @param name The name of the resource to be loaded.
	 * @throws FileNotFoundException When the file or resource is not found.
	 * @throws FormatException For any kind of malformed input.
	 */
	public void load(String name)
			throws FileNotFoundException, FormatException;
}

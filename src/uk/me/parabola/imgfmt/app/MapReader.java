/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Dec 4, 2007
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.main.FileInfo;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * A class to represent reading of an img file.  As the focus of mkgmap is on
 * writing files, this is not as complete as the MapWriter class, and it is
 * mainly designed to create the extra files that are required to combine
 * map tiles.
 *
 * @author Steve Ratcliffe
 */
public class MapReader {
	private static final Logger log = Logger.getLogger(MapReader.class);

}

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
 * Create date: 20-Oct-2007
 */
package test;

import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;

import java.io.IOException;
import java.util.List;

/**
 * @author Steve Ratcliffe
 */
public class ListFile {
	public static void main(String[] args) throws IOException {
		//FileSystemParam params = new FileSystemParam();
		String file = "63240001.img";
		if (args.length > 0)
			file = args[0];
		
		FileSystem fs = ImgFS.openFs(file);
		List<DirectoryEntry> entries = fs.list();
		for (DirectoryEntry ent : entries) {
			String fullname = ent.getFullName();
			System.out.format("%-15s %d\n", fullname, ent.getSize());
		}

		fs.close();
	}
}

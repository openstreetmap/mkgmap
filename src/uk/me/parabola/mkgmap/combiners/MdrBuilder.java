/*
 * Copyright (C) 2009.
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
package uk.me.parabola.mkgmap.combiners;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.map.MapReader;
import uk.me.parabola.imgfmt.app.mdr.MDRFile;
import uk.me.parabola.imgfmt.app.mdr.MdrConfig;
import uk.me.parabola.imgfmt.app.trergn.Point;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.mkgmap.CommandArgs;

/**
 * Create the global index file.  This consists of an img file containing
 * an MDR file and optionally an SRT file.
 *
 * @author Steve Ratcliffe
 */
public class MdrBuilder implements Combiner {
	private MDRFile mdrFile;

	// Push things onto this stack to have them closed in the reverse order.
	private final Deque<Closeable> toClose = new ArrayDeque<Closeable>();

	/**
	 * Create the mdr file and initialise.
	 * It has a name that is based on the overview-mapname option, as does
	 * the associated MDX file.
	 *
	 * @param args The command line arguments.
	 */
	public void init(CommandArgs args) {
		String name = args.get("overview-mapname", "osmmap");

		ImgChannel mdrChan;
		try {
			// Create the .img file system/archive
			FileSystemParam params = new FileSystemParam();
			FileSystem fs = ImgFS.createFs(name + "_mdr.img", params);
			toClose.push(fs);

			// Create the MDR file within the .img
			mdrChan = fs.create(name.toUpperCase(Locale.ENGLISH) + ".MDR");
			toClose.push(mdrChan);
		} catch (IOException e) {
			throw new ExitException("Could not create global index file");
		}

		// Set the options that we are using for the mdr.
		MdrConfig config = new MdrConfig();
		config.setHeaderLen(286);
		config.setWritable(true);
		config.setForDevice(false);

		// Wrap the MDR channel with the MDRFile object
		mdrFile = new MDRFile(mdrChan, config);
		toClose.push(mdrFile);
	}

	/**
	 * Adds a new map to the file.  We need to read in the img file and
	 * extract all the information that can be indexed from it.
	 *
	 * @param finfo An interface to read the map.
	 */
	public void onMapEnd(FileInfo finfo) {
		// Add the map name
		mdrFile.addMap(finfo.getMapnameAsInt());

		String filename = finfo.getFilename();
		MapReader mr = null;
		try {
			mr = new MapReader(filename);
			List<Point> list = mr.indexedPointsForLevel(0);
			for (Point p : list) {
				if (p.getLabel().getText().length() > 0 && p.getType() == 0xb)		
					mdrFile.addPoint(p, true);
			}
		} catch (FileNotFoundException e) {
			throw new ExitException("Could not open " + filename + " when creating mdr file");
		} finally {
			Utils.closeFile(mr);
		}
	}

	public void onFinish() {
		// Write out the mdr file
		mdrFile.write();

		// Close everything
		for (Closeable file : toClose)
			Utils.closeFile(file);
	}
}

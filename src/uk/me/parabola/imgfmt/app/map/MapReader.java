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
package uk.me.parabola.imgfmt.app.map;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.Country;
import uk.me.parabola.imgfmt.app.lbl.LBLFileReader;
import uk.me.parabola.imgfmt.app.lbl.Region;
import uk.me.parabola.imgfmt.app.net.NETFileReader;
import uk.me.parabola.imgfmt.app.trergn.Point;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.imgfmt.app.trergn.RGNFileReader;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.imgfmt.app.trergn.TREFileReader;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;

/**
 * This is a view of a .img file when we are reading it.  The {@link Map}
 * class is the equivalent for writing.
 * 
 * @author Steve Ratcliffe
 */
public class MapReader implements Closeable {
	private final TREFileReader treFile;
	private final RGNFileReader rgnFile;
	private final LBLFileReader lblFile;
	private final NETFileReader netFile;

	private final Deque<Closeable> toClose = new ArrayDeque<Closeable>();

	public MapReader(String filename) throws FileNotFoundException {
		FileSystem fs = ImgFS.openFs(filename);
		saveForClose(fs);

		List<DirectoryEntry> entries = fs.list();

		// Find the TRE entry
		String mapname = null;
		for (DirectoryEntry ent : entries) {
			if ("TRE".equals(ent.getExt())) {
				mapname = ent.getName();
				break;
			}
		}

		if (mapname == null)
			throw new FileNotFoundException("No TRE entry in img file");

		ImgChannel chan = fs.open(mapname + ".TRE", "r");
		treFile = new TREFileReader(chan);
		saveForClose(treFile, chan);

		chan = fs.open(mapname + ".RGN", "r");
		rgnFile = new RGNFileReader(chan);
		saveForClose(rgnFile, chan);

		chan = fs.open(mapname + ".LBL", "r");
		lblFile = new LBLFileReader(chan);
		saveForClose(lblFile, chan);

		// The NET file is optional
		NETFileReader nr;
		try {
			chan = fs.open(mapname + ".NET", "r");
			nr = new NETFileReader(chan);
			saveForClose(nr);
		} catch (FileNotFoundException e) {
			nr = null;
		}
		netFile = nr;

		rgnFile.setNetFile(netFile);
		rgnFile.setLblFile(lblFile);
	}

	/**
	 * Get a list of all the points for a given level.
	 * @param level The level, lower numbers are the most detailed.
	 */
	public List<Point> pointsForLevel(int level) {
		List<Point> points = new ArrayList<Point>();

		Subdivision[] subdivisions = treFile.subdivForLevel(level);
		for (Subdivision sd : subdivisions) {
			List<Point> subdivPoints = rgnFile.pointsForSubdiv(sd);
			points.addAll(subdivPoints);
		}

		return points;
	}

	/**
	 * Get a list of all the lines for a given level.
	 * @param level The level, lower numbers are the most detailed.
	 */
	public List<Polyline> linesForLevel(int level) {
		ArrayList<Polyline> points = new ArrayList<Polyline>();

		Subdivision[] subdivisions = treFile.subdivForLevel(level);
		for (Subdivision div : subdivisions) {
			List<Polyline> subdivPoints = rgnFile.linesForSubdiv(div);
			points.addAll(subdivPoints);
		}

		return points;
	}

	public void close() throws IOException {
		for (Closeable c : toClose)
			Utils.closeFile(c);
	}

	private void saveForClose(Closeable c1, Closeable c2) {
		saveForClose(c1);
		saveForClose(c2);
	}

	private void saveForClose(Closeable c) {
		toClose.push(c);
	}

	public List<City> getCities() {
		return lblFile.getCities();
	}

	public List<Country> getCountries() {
		return lblFile.getCountries();
	}

	public List<Region> getRegions() {
		return lblFile.getRegions();
	}
}

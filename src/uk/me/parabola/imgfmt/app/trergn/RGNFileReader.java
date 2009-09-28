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
package uk.me.parabola.imgfmt.app.trergn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgReader;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.LBLFileReader;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.util.EnhancedProperties;

/**
 * The region file.  Holds actual details of points and lines etc.
 *
 * This is the view of the file when it is being read.  Use {@link RGNFile}
 * for writing the file.
 *
 * The main focus of mkgmap is creating files, there are plenty of applications
 * that read and display the data, reading is implemented only to the
 * extent required to support creating the various auxiliary files etc.
 * 
 * @author Steve Ratcliffe
 */
public class RGNFileReader extends ImgReader {

	private LBLFileReader lblFile;

	public RGNFileReader(ImgChannel chan) {
		RGNHeader header = new RGNHeader();
		setHeader(header);

		setReader(new BufferedImgFileReader(chan));
		header.readHeader(getReader());
	}

	public void config(EnhancedProperties props) {
		//config = props;
	}

	public List<Point> pointsForSubdiv(Subdivision sd) {
		if (!sd.hasPoints())
			return Collections.emptyList();

		Offsets offsets = getOffsets(sd);
		return fetchPointsCommon(sd, offsets.getPointStart(), offsets.getPointEnd());
	}

	public List<Point> indexPointsForSubdiv(Subdivision sd) {
		if (!sd.hasIndPoints())
			return Collections.emptyList();

		Offsets offsets = getOffsets(sd);
		return fetchPointsCommon(sd, offsets.getIndPointStart(), offsets.getIndPointEnd());
	}

	private List<Point> fetchPointsCommon(Subdivision sd, long start, long end) {
		List<Point> points = new ArrayList<Point>();
		position(start);
		ImgFileReader reader = getReader();

		int number = 1;
		while (position() < end) {
			Point p = new Point(sd);

			byte t = reader.get();
			int val = reader.get3();
			boolean hasSubtype = false;
			if ((val & 0x800000) != 0)
				hasSubtype = true;

			boolean hasPoi = false;
			if ((val & 0x400000) != 0)
				hasPoi = true;

			Label l;
			int labelOffset = val & 0x3fffff;
			if (hasPoi) {
				l = lblFile.fetchPoi(labelOffset);
				System.out.println("poi label " + l);
			} else {
				l = lblFile.fetchLabel(labelOffset);
			}
			p.setLabel(l);

			p.setDeltaLong(reader.getChar());
			p.setDeltaLat(reader.getChar());

			if (hasSubtype) {
				byte st = reader.get();
				p.setType(((t & 0xff) << 8) | (st & 0xff));
			} else {
				p.setType(t & 0xffff);
			}

			p.setNumber(number++);
			points.add(p);
		}
		return points;
	}

	private Offsets getOffsets(Subdivision sd) {
		int off = sd.getStartRgnPointer();
		position(getHeader().getHeaderLength() + off);

		return new Offsets(sd);
	}

	public void setLblFile(LBLFileReader lblFile) {
		this.lblFile = lblFile;
	}

	/**
	 * Class to hold the start and end points of point, lines etc within
	 * the area for a given subdivision in the RGN data.
	 */
	private class Offsets {
		private final int pointOffset;
		private int pointEnd;

		private int indPointOffset;
		private int indPointEnd;

		private int lineOffset;
		private int lineEnd;

		private int polygonOffset;
		private int polygonEnd;

		private final int start;
		private int headerLen;

		/**
		 * Calculate the offsets for the given subdivision.
		 * After this is called the position will be set after any pointers that
		 * exist at the beginning of the area.
		 *
		 * @param sd The subdivision.
		 */
		private Offsets(Subdivision sd) {
			ImgFileReader reader = getReader();

			start = (int) position();
			
			pointOffset = 0;

			if (sd.needsIndPointPtr()) {
				indPointOffset = reader.getChar();
				headerLen += 2;
			}
			
			if (sd.needsPolylinePtr()) {
				lineOffset = reader.getChar();
				headerLen += 2;
			}

			if (sd.needsPolygonPtr()) {
				polygonOffset = reader.getChar();
				headerLen += 2;
			}


			if (sd.hasPoints()) {
				if (sd.hasIndPoints())
					pointEnd = indPointOffset;
				else if (sd.hasPolylines())
					pointEnd = lineOffset;
				else if (sd.hasPolygons())
					pointEnd = polygonOffset;
				else
					pointEnd = sd.getEndRgnPointer() - sd.getStartRgnPointer();
			}
			if (sd.hasIndPoints()) {
				if (sd.hasPolylines())
					indPointEnd = lineOffset;
				else if (sd.hasPolygons())
					indPointEnd = polygonOffset;
				else
					indPointEnd = sd.getEndRgnPointer() - sd.getStartRgnPointer();
			}
			if (sd.hasPolylines()) {
				if (sd.hasPolygons())
					lineEnd = polygonOffset;
				else
					lineEnd = sd.getEndRgnPointer() - sd.getStartRgnPointer();
			}
			if (sd.hasPolygons()) {
				polygonEnd = sd.getEndRgnPointer() - sd.getStartRgnPointer();
			}
		}

		public String toString() {
			return String.format("rgn div offsets: %x-%x/%x-%x/%x-%x/%x-%x",
					pointOffset, pointEnd, indPointOffset, indPointEnd,
					lineOffset, lineEnd, polygonOffset, polygonEnd);
		}

		public long getPointStart() {
			return pointOffset == 0 ? start + headerLen : start + pointOffset;
		}

		public long getPointEnd() {
			return start + pointEnd;
		}

		public long getIndPointStart() {
			return indPointOffset == 0 ? start + headerLen : start + indPointOffset;
		}

		public long getIndPointEnd() {
			return start + indPointEnd;
		}
	}
}

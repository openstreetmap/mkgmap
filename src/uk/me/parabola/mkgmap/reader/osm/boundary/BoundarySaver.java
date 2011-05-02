/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

public class BoundarySaver {
	private static final Logger log = Logger.getLogger(BoundarySaver.class);

	private final File boundaryDir;

	/** keeps the open streams */
	private final Map<String, OutputStream> streams;

	public BoundarySaver(File boundaryDir) {
		this.boundaryDir = boundaryDir;
		this.streams = new HashMap<String, OutputStream>();
	}

	public void addBoundary(Boundary boundary) {
		Map<String, Area> splitBounds = splitArea(boundary.getArea());
		for (Entry<String, Area> split : splitBounds.entrySet()) {
			saveToFile(split.getKey(),
					new Boundary(split.getValue(), boundary.getTags()));
		}
	}

	public void end() {
		for (OutputStream stream : streams.values()) {
			try {
				stream.close();
			} catch (IOException exp) {
				log.error("Problems closing stream: " + exp);
			}
		}
		streams.clear();
	}

	private Area getSplitArea(int lat, int lon) {
		Rectangle splitRect = new Rectangle(lon, lat, BoundaryUtil.RASTER,
				BoundaryUtil.RASTER);
		return new Area(splitRect);
	}

	private Map<String, Area> splitArea(Area areaToSplit) {
		Map<String, Area> splittedAreas = new HashMap<String, Area>();

		Rectangle areaBounds = areaToSplit.getBounds();

		for (int latSplit = BoundaryUtil.getSplitBegin(areaBounds.y); latSplit <= BoundaryUtil
				.getSplitBegin(areaBounds.y + areaBounds.height); latSplit += BoundaryUtil.RASTER) {
			for (int lonSplit = BoundaryUtil.getSplitBegin(areaBounds.x); lonSplit <= BoundaryUtil
					.getSplitBegin(areaBounds.x + areaBounds.width); lonSplit += BoundaryUtil.RASTER) {
				Area tileCover = getSplitArea(latSplit, lonSplit);
				tileCover.intersect(areaToSplit);
				if (tileCover.isEmpty() == false) {
					splittedAreas.put(BoundaryUtil.getKey(latSplit, lonSplit),
							tileCover);
				}
			}
		}

		return splittedAreas;
	}

	private OutputStream getStream(String filekey) {
		OutputStream stream = streams.get(filekey);
		if (stream == null) {
			File file = new File(boundaryDir, "bounds_" + filekey + ".bnd");
			if (file.getParentFile().exists() == false)
				file.getParentFile().mkdirs();
			FileOutputStream fileStream = null;
			try {
				fileStream = new FileOutputStream(file);
				stream = new BufferedOutputStream(fileStream);
				streams.put(filekey, stream);
			} catch (IOException exp) {
				log.error("Cannot save boundary: " + exp);
				if (fileStream != null) {
					try {
						fileStream.close();
					} catch (Throwable thr) {

					}
				}
			} finally {
			}
		}
		return stream;
	}

	private void saveToFile(String filekey, Boundary boundary) {
		try {
			OutputStream stream = getStream(filekey);
			if (stream != null)
				write(stream, boundary);
		} catch (Exception exp) {
			log.error("Cannot write boundary: " + exp, exp);
		}
	}

	private void write(OutputStream stream, Boundary boundary) {
		ByteArrayOutputStream oneItemStream = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(oneItemStream);
		try {

			// write the tags
			int noOfTags = boundary.getTags().size();
			dos.writeInt(noOfTags);
			Iterator<Entry<String, String>> tagIter = boundary.getTags()
					.entryIterator();
			while (tagIter.hasNext()) {
				Entry<String, String> tag = tagIter.next();
				dos.writeUTF(tag.getKey());
				dos.writeUTF(tag.getValue());
				noOfTags--;
			}
			assert noOfTags==0 : "Remaining tags: "+noOfTags+" size: "+boundary.getTags().size()+" "+boundary.getTags().toString();

			// write the number of boundary elements to create the multipolygon
			List<BoundaryElement> boundaryElements = boundary
					.getBoundaryElements();
			dos.writeInt(boundaryElements.size());

			// now write each element
			for (BoundaryElement bElem : boundaryElements) {
				// is it an outer element?
				dos.writeBoolean(bElem.isOuter());
				// number of coords
				int eSize = bElem.getPoints().size();
				assert eSize > 0 : "eSize not greater 0 "+bElem.getPoints().size();
				dos.writeInt(eSize);
				for (Coord c : bElem.getPoints()) {
					dos.writeInt(c.getLatitude());
					dos.writeInt(c.getLongitude());
				}
			}

			// all items of the boundary element have been written
			dos.flush();
			dos.close();

			// now start to write into the real stream

			// first write the bounding box so that is possible to skip the
			// complete
			// entry
			uk.me.parabola.imgfmt.app.Area bbox = boundary.getBbox();
			DataOutputStream dOutStream = new DataOutputStream(stream);
			dOutStream.writeInt(bbox.getMinLat());
			dOutStream.writeInt(bbox.getMinLong());
			dOutStream.writeInt(bbox.getMaxLat());
			dOutStream.writeInt(bbox.getMaxLong());

			// write the size of the boundary block so that it is possible to
			// skip it
			byte[] data = oneItemStream.toByteArray();
			assert data.length > 0 : "bSize is not > 0 : "+data.length;
			dOutStream.writeInt(data.length);

			// write the boundary block
			dOutStream.write(data);
			dOutStream.flush();

		} catch (IOException exp) {
			log.error(exp.toString());
		}

	}

}

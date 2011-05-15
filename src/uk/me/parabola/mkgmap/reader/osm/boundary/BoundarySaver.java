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
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.Version;

public class BoundarySaver {
	private static final Logger log = Logger.getLogger(BoundarySaver.class);

	private final File boundaryDir;
	private uk.me.parabola.imgfmt.app.Area bbox;

	private int minLat = Integer.MAX_VALUE;
	private int minLong= Integer.MAX_VALUE;
	private int maxLat= Integer.MIN_VALUE;
	private int maxLong= Integer.MIN_VALUE;

	
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
		if (getBbox() != null) {
			// a bounding box is set => fill the gaps with empty files
			for (int latSplit = BoundaryUtil.getSplitBegin(getBbox().getMinLat()); latSplit <= BoundaryUtil
					.getSplitBegin(getBbox().getMaxLat()); latSplit += BoundaryUtil.RASTER) {
				for (int lonSplit = BoundaryUtil.getSplitBegin(getBbox()
						.getMinLong()); lonSplit <= BoundaryUtil
						.getSplitBegin(getBbox().getMaxLong()); lonSplit += BoundaryUtil.RASTER) {
					String key = BoundaryUtil.getKey(latSplit, lonSplit);
					OutputStream stream = getStream(key);
					try {
						stream.close();
					} catch (IOException exp) {
						log.error("Problems closing stream: " + exp);
					}
					streams.remove(key);
				}
			}
		}
		
		// close the rest of the streams
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
				writeDefaultInfos(stream);
				streams.put(filekey, stream);
				
				String[] keyParts = filekey.split(Pattern.quote("_"));
				int lat = Integer.valueOf(keyParts[0]);
				int lon = Integer.valueOf(keyParts[1]);
				if (lat < minLat) {
					minLat = lat;
					log.debug("New min Lat:",minLat);
				}
				if (lat > maxLat) {
					maxLat = lat;
					log.debug("New max Lat:",maxLat);
				}
				if (lon < minLong) {
					minLong = lon;
					log.debug("New min Lon:",minLong);
				}
				if (lon > maxLong) {
					maxLong = lon;
					log.debug("New max Long:",maxLong);
				}
				
			} catch (IOException exp) {
				log.error("Cannot save boundary: " + exp);
				if (fileStream != null) {
					try {
						fileStream.close();
					} catch (Throwable thr) {
					}
				}
				return null;
			} finally {
			}
		}
		return stream;
	}
	
	private void writeDefaultInfos(OutputStream stream) throws IOException {
		DataOutputStream dos = new DataOutputStream(stream);
		dos.writeUTF(Version.VERSION);
		dos.writeLong(System.currentTimeMillis());
		dos.flush();
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

	public uk.me.parabola.imgfmt.app.Area getBbox() {
		if (bbox == null) {
			bbox = new uk.me.parabola.imgfmt.app.Area(minLat, minLong, maxLat, maxLong);
			log.error("Calculate bbox to "+bbox);
		}
		return bbox;
	}

	public void setBbox(uk.me.parabola.imgfmt.app.Area bbox) {
		if (bbox.isEmpty()) {
			log.warn("Do not use bonding box because it's empty");
			this.bbox = null;
		} else {
			this.bbox = bbox;
			log.info("Set bbox: "+bbox.getMinLat()+" "+bbox.getMinLong()+" "+bbox.getMaxLat()+" "+bbox.getMaxLong());
		}
	}
}

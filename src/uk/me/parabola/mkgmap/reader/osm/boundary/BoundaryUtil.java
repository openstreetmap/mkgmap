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

import java.awt.geom.Area;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryUtil {
	private static final Logger log = Logger.getLogger(BoundaryUtil.class);

	public static class BoundaryFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && pathname.getName().endsWith(".bnd");
		}
	}
	
	public static List<BoundaryElement> splitToElements(Area area) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		}

		List<List<Coord>> areaElements = Java2DConverter.areaToShapes(area);

		if (areaElements.isEmpty()) {
			// this may happen if a boundary overlaps a raster tile in a very small area
			// so that it is has no dimension
			log.debug("Area has no dimension. Area:",area.getBounds());
			return Collections.emptyList();
		}
		
		List<BoundaryElement> bElements = new ArrayList<BoundaryElement>();
		for (List<Coord> singleElement : areaElements) {
			if (singleElement.size() <= 3) {
				// need at least 4 items to describe a polygon
				continue;
			}
			Way w = new Way(0, singleElement);

			boolean outer = w.clockwise();
			bElements.add(new BoundaryElement(outer, singleElement));
		}

		if (bElements.isEmpty()) {
			// should not happen because empty polygons should be removed by
			// the Java2DConverter
			log.error("Empty boundary elements list after conversion. Area: "+area.getBounds());
			return Collections.emptyList();
		}

		
		// reverse the list because it starts with the inner elements first and
		// we need the other way round
		Collections.reverse(bElements);

		assert bElements.get(0).isOuter() : log.threadTag()+" first element is not outer. "+ bElements;
		return bElements;
	}

	public static Area convertToArea(List<BoundaryElement> list) {
		Area area = new Area();

		for (BoundaryElement elem : list) {
			if (elem.isOuter()) {
				area.add(elem.getArea());
			} else {
				area.subtract(elem.getArea());
			}
		}
		return area;
	}

	public static List<Boundary> loadBoundaryFile(File boundaryFile,
			uk.me.parabola.imgfmt.app.Area bbox) throws IOException
	{
		log.debug("Load boundary file",boundaryFile,"within",bbox);
		List<Boundary> boundaryList = new ArrayList<Boundary>();
		FileInputStream stream = new FileInputStream(boundaryFile);
		try {
			DataInputStream inpStream = new DataInputStream(
					new BufferedInputStream(stream, 1024 * 1024));

			try {
				// 1st read the mkgmap release the boundary file is created by
				String mkgmapRel = inpStream.readUTF();
				long createTime = inpStream.readLong();
				
				if (log.isDebugEnabled()) {
					log.debug("File created by mkgmap release",mkgmapRel,"at",new Date(createTime));
				}
				
				while (true) {
					int minLat = inpStream.readInt();
					int minLong = inpStream.readInt();
					int maxLat = inpStream.readInt();
					int maxLong = inpStream.readInt();
					log.debug("Next boundary. Lat min:",minLat,"max:",maxLat,"Long min:",minLong,"max:",maxLong);
					uk.me.parabola.imgfmt.app.Area rBbox = new uk.me.parabola.imgfmt.app.Area(
							minLat, minLong, maxLat, maxLong);
					int bSize = inpStream.readInt();
					log.debug("Size:",bSize);

					if (bbox == null || bbox.intersects(rBbox)) {
						log.debug("Bbox intersects. Load the boundary");
						Tags tags = new Tags();
						int noOfTags = inpStream.readInt();
						for (int i = 0; i < noOfTags; i++) {
							String name = inpStream.readUTF();
							String value = inpStream.readUTF();
							tags.put(name, value);
						}

						int noBElems = inpStream.readInt();
						assert noBElems > 0;
						
						// the first area is always an outer area and will be assigned to the variable
						Area area = null;
						
						for (int i = 0; i < noBElems; i++) {
							boolean outer = inpStream.readBoolean();
							int noCoords = inpStream.readInt();
							log.debug("No of coords",noCoords);
							List<Coord> points = new ArrayList<Coord>(noCoords);
							for (int c = 0; c < noCoords; c++) {
								int lat = inpStream.readInt();
								int lon = inpStream.readInt();
								points.add(new Coord(lat, lon));
							}

							Area elemArea = Java2DConverter.createArea(points);
							if (outer) {
								if (area == null) {
									area = elemArea;
								} else {
									area.add(elemArea);
								}
							} else {
								area.subtract(elemArea);
							}
						}

						Boundary boundary = new Boundary(area, tags);
						boundaryList.add(boundary);

					} else {
						log.debug("Bbox does not intersect. Skip",bSize);
						inpStream.skipBytes(bSize);
					}
				}
			} catch (EOFException exp) {
				// it's always thrown at the end of the file
//				log.error("Got EOF at the end of the file");
			} 
			inpStream.close();
		} finally {
			if (stream != null)
				stream.close();
		}
		return boundaryList;
	}

	public static List<File> getBoundaryFiles(File boundaryDir,
			uk.me.parabola.imgfmt.app.Area bbox) {
		List<File> boundaryFiles = new ArrayList<File>();
		for (int latSplit = BoundaryUtil.getSplitBegin(bbox.getMinLat()); latSplit <= BoundaryUtil
				.getSplitBegin(bbox.getMaxLat()); latSplit += BoundaryUtil.RASTER) {
			for (int lonSplit = BoundaryUtil.getSplitBegin(bbox.getMinLong()); lonSplit <= BoundaryUtil
					.getSplitBegin(bbox.getMaxLong()); lonSplit += BoundaryUtil.RASTER) {
				File bndFile = new File(boundaryDir, "bounds_"
						+ getKey(latSplit, lonSplit) + ".bnd");
				if (bndFile.exists())
					boundaryFiles.add(bndFile);
			}
		}
		return boundaryFiles;
	}

	public static List<Boundary> loadBoundaries(File boundaryDir,
			uk.me.parabola.imgfmt.app.Area bbox) {
		List<File> boundaryFiles = getBoundaryFiles(boundaryDir, bbox);
		List<Boundary> boundaries = new ArrayList<Boundary>();
		for (File boundaryFile : boundaryFiles) {
			try {
				boundaries.addAll(loadBoundaryFile(boundaryFile, bbox));
			} catch (IOException exp) {
				log.warn("Cannot load boundary file", boundaryFile + ".",exp);
//				String basename = "missingbounds/";
//				String[] bParts = boundaryFile.getName().substring(0,boundaryFile.getName().length()-4).split("_");
//				int minLat = Integer.valueOf(bParts[1]);
//				int minLong = Integer.valueOf(bParts[2]);
//				uk.me.parabola.imgfmt.app.Area bBbox = new uk.me.parabola.imgfmt.app.Area(minLat, minLong, minLat+RASTER, minLong+RASTER);
//				GpxCreator.createAreaGpx(basename+boundaryFile.getName(), bBbox);
//				log.error("GPX created "+basename+boundaryFile.getName());
			}
		}
		if (boundaryFiles.size() > 1) {
			boundaries = mergeBoundaries(boundaries);
		}
		return boundaries;
	}

	private static List<Boundary> mergeBoundaries(List<Boundary> boundaryList) {
		int noIdBoundaries = 0;
		Map<String, Boundary> mergeMap = new HashMap<String, Boundary>();
		for (Boundary toMerge : boundaryList) {
			String bId = toMerge.getTags().get("mkgmap:boundaryid");
			if (bId == null) {
				noIdBoundaries++;
				mergeMap.put("n" + noIdBoundaries, toMerge);
			} else {
				Boundary existingBoundary = mergeMap.get(bId);
				if (existingBoundary == null) {
					mergeMap.put(bId, toMerge);
				} else {
					if (log.isInfoEnabled())
						log.info("Merge boundaries", existingBoundary.getTags(), "with", toMerge.getTags());
					existingBoundary.getArea().add(toMerge.getArea());
					
					// Merge the mkgmap:lies_in tag
					// They should be the same but better to check that...
					String liesInTagExist = existingBoundary.getTags().get("mkgmap:lies_in");
					String liesInTagMerge = toMerge.getTags().get("mkgmap:lies_in");
					if (liesInTagExist != null && liesInTagExist.equals(liesInTagMerge)==false) {
						if (liesInTagMerge == null) {
							existingBoundary.getTags().remove("mkgmap:lies_in");
						} else {
							// there is a difference in the lies_in tag => keep the equal ids
							Set<String> existIds = new HashSet<String>(Arrays.asList(liesInTagExist.split(";")));
							Set<String> mergeIds = new HashSet<String>(Arrays.asList(liesInTagMerge.split(";")));
							existIds.retainAll(mergeIds);
							if (existIds.isEmpty()) {
								existingBoundary.getTags().remove("mkgmap:lies_in");
							} else {
								StringBuilder newLiesIn = new StringBuilder();
								for (String liesInEntry : existIds) {
									if (newLiesIn.length() > 0) {
										newLiesIn.append(";");
									}
									newLiesIn.append(liesInEntry);
								}
								existingBoundary.getTags().put("mkgmap:lies_in", newLiesIn.toString());
							}
						}
					}
				}
			}
		}
		if (noIdBoundaries > 0) {
			log.error(noIdBoundaries
					+ " without boundary id. Could not merge them.");
		}
		return new ArrayList<Boundary>(mergeMap.values());
	}

	public static final int RASTER = 50000;

	public static int getSplitBegin(int value) {
		int rem = value % RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value - rem;
		} else {
			return value - RASTER - rem;
		}
	}

	public static int getSplitEnd(int value) {
		int rem = value % RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value + RASTER - rem;
		} else {
			return value - rem;
		}
	}

	public static String getKey(int lat, int lon) {
		return lat + "_" + lon;
	}
	
	/**
	 * Retrieve the bounding box of the given boundary file.
	 * @param boundaryFile the boundary file
	 * @return the bounding box
	 */
	public static uk.me.parabola.imgfmt.app.Area getBbox(File boundaryFile) {
		String filename = boundaryFile.getName();
		// cut off the extension
		filename = filename.substring(0,filename.length()-4);
		String[] fParts = filename.split(Pattern.quote("_"));
		
		int lat = Integer.valueOf(fParts[1]);
		int lon = Integer.valueOf(fParts[2]);
		
		return new uk.me.parabola.imgfmt.app.Area(lat, lon, lat+RASTER, lon+RASTER);
	}
	
}

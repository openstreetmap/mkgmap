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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryUtil {
	private static final Logger log = Logger.getLogger(BoundaryUtil.class);

	public static List<BoundaryElement> splitToElements(Area area) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		}

		List<List<Coord>> areaElements = Java2DConverter.areaToShapes(area);

		if (areaElements.isEmpty()) {
			// this may happen if a boundary overlaps a raster tile in a very small area
			// so that it is has no dimension
			log.debug("Area has no dimension. Area: ",area.getBounds());
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
		List<Boundary> boundaryList = new ArrayList<Boundary>();
		FileInputStream stream = new FileInputStream(boundaryFile);
		try {
			DataInputStream inpStream = new DataInputStream(
					new BufferedInputStream(stream, 1024 * 1024));

			try {
				while (true) {
					int minLat = inpStream.readInt();
					int minLong = inpStream.readInt();
					int maxLat = inpStream.readInt();
					int maxLon = inpStream.readInt();
					uk.me.parabola.imgfmt.app.Area rBbox = new uk.me.parabola.imgfmt.app.Area(
							minLat, minLong, maxLat, maxLon);
					int bSize = inpStream.readInt();

					if (bbox == null || bbox.intersects(rBbox)) {
						Tags tags = new Tags();
						int noOfTags = inpStream.readInt();
						for (int i = 0; i < noOfTags; i++) {
							String name = inpStream.readUTF();
							String value = inpStream.readUTF();
							tags.put(name, value);
						}

						int noBElems = inpStream.readInt();
						Area area = new Area();
						for (int i = 0; i < noBElems; i++) {
							boolean outer = inpStream.readBoolean();
							int noCoords = inpStream.readInt();
							List<Coord> points = new ArrayList<Coord>(noCoords);
							for (int c = 0; c < noCoords; c++) {
								int lat = inpStream.readInt();
								int lon = inpStream.readInt();
								points.add(new Coord(lat, lon));
							}

							Area elemArea = Java2DConverter.createArea(points);
							if (outer) {
								area.add(elemArea);
							} else {
								area.subtract(elemArea);
							}
						}

						Boundary boundary = new Boundary(area, tags);
						boundaryList.add(boundary);

					} else {
						inpStream.skip(bSize);
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
				.getSplitEnd(bbox.getMaxLat()); latSplit += BoundaryUtil.RASTER) {
			for (int lonSplit = BoundaryUtil.getSplitBegin(bbox.getMinLong()); lonSplit <= BoundaryUtil
					.getSplitEnd(bbox.getMaxLong()); lonSplit += BoundaryUtil.RASTER) {
				boundaryFiles.add(new File(boundaryDir, "bounds_"
						+ getKey(latSplit, lonSplit) + ".bnd"));
			}
		}
		return boundaryFiles;
	}

	public static List<Boundary> loadBoundaries(File boundaryDir,
			uk.me.parabola.imgfmt.app.Area bbox) {
		List<File> boundaryFiles = getBoundaryFiles(boundaryDir, bbox);
		List<Boundary> boundaries = new ArrayList<Boundary>(
				boundaryFiles.size());
		for (File boundaryFile : boundaryFiles) {
			try {
				boundaries.addAll(loadBoundaryFile(boundaryFile, bbox));
			} catch (IOException exp) {
				log.error("Cannot load boundary file " + boundaryFile + ": "
						+ exp);
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
					log.info("Merge boundaries " + existingBoundary.getTags()
							+ " with " + toMerge.getTags());
					existingBoundary.getArea().add(toMerge.getArea());
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
	
	public static void main(String[] args) {
		File bdir = new File(args[0]);
		File[] bFiles = bdir.listFiles(new FileFilter() {
			
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".bnd");
			}
		});
		
		uk.me.parabola.imgfmt.app.Area allArea = new uk.me.parabola.imgfmt.app.Area(Utils.toMapUnit(-180.0d), Utils.toMapUnit(-180.0d), Utils.toMapUnit(180.0d), Utils.toMapUnit(180.0d));
		for (File bFile : bFiles) {
			try {
				System.out.println(bFile);
				List<Boundary> boundaries = loadBoundaryFile(bFile, allArea);
				for (Boundary b : boundaries) {
					uk.me.parabola.imgfmt.app.Area bbox = b.getBbox();
					System.out.println(b.getTags()+" "+bbox.getMinLat()+" "+bbox.getMinLong()+" "+bbox.getMaxLat()+" "+bbox.getMaxLong());
				}
			} catch (IOException exp) {
				System.out.println(exp);
				exp.printStackTrace(System.out);
			}
		}
	}

}

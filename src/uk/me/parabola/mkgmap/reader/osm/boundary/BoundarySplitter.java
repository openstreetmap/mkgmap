package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.MultiHashMap;

public class BoundarySplitter extends Thread {
	private static final Logger log = Logger.getLogger(BoundarySplitter.class);

	private final String boundaryFilename;
	private final String boundsDir;

	public BoundarySplitter(EnhancedProperties properties) {
		this.boundaryFilename = properties
				.getProperty("createboundsfile", null);
		this.boundsDir = properties.getProperty("boundsdirectory", "bounds");
	}

	public void run() {
		if (boundaryFilename == null) {
			return;
		}
		try {
			Osm5BoundaryDataSource dataSource = new Osm5BoundaryDataSource();
			log.info("Started loading", boundaryFilename);
			dataSource.load(boundaryFilename);
			log.info("Finished loading", boundaryFilename);
			List<Boundary> boundaries = dataSource.getBoundaries();
			log.info(boundaries.size() + " boundaries loaded");
			dataSource = null;
			MultiHashMap<String, Boundary> splittedBounds = split(boundaries);
			saveToFile(splittedBounds);
		} catch (FileNotFoundException exp) {
			log.error("Boundary file " + boundaryFilename + " not found.");
		} catch (IOException exp) {
			log.error("Problems saving boundaries: " + exp);
		}
	}

	private final int RASTER = 40000;

	private int getSplitBegin(int value) {
		int rem = value % RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value - rem;
		} else {
			return value - RASTER - rem;
		}
	}

	private int getSplitEnd(int value) {
		int rem = value % RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value + RASTER - rem;
		} else {
			return value - rem;
		}
	}

	private Area getSplitArea(int lat, int lon) {
		Rectangle splitRect = new Rectangle(lon, lat, RASTER, RASTER);
		return new Area(splitRect);
	}

	private String getKey(int lat, int lon) {
		return lat + "_" + lon;
	}

	private Map<String, Area> getSplitAreas(Area areaToSplit) {
		Map<String, Area> splittedAreas = new HashMap<String, Area>();

		Rectangle areaBounds = areaToSplit.getBounds();

		for (int latSplit = getSplitBegin(areaBounds.y); latSplit <= getSplitEnd(areaBounds.y
				+ areaBounds.height); latSplit += RASTER) {
			for (int lonSplit = getSplitBegin(areaBounds.x); lonSplit <= getSplitEnd(areaBounds.x
					+ areaBounds.width); lonSplit += RASTER) {
				Area tileCover = getSplitArea(latSplit, lonSplit);
				tileCover.intersect(areaToSplit);
				if (tileCover.isEmpty() == false) {
					splittedAreas.put(getKey(latSplit, lonSplit), tileCover);
				}
			}
		}

		return splittedAreas;
	}

	private MultiHashMap<String, Boundary> split(List<Boundary> boundaries) {

		MultiHashMap<String, Boundary> splittedBoundaries = new MultiHashMap<String, Boundary>();

		for (Boundary bound : boundaries) {
			Map<String, Area> splitBounds = getSplitAreas(bound.getArea());
			for (Entry<String, Area> split : splitBounds.entrySet()) {
				splittedBoundaries.add(split.getKey(),
						new Boundary(split.getValue(), bound.getTags()));
			}
		}

		return splittedBoundaries;
	}

	private void saveToFile(MultiHashMap<String, Boundary> bounds) throws IOException {
		for (Entry<String, List<Boundary>> fileData : bounds.entrySet()) {
			saveToFile(fileData.getKey(), fileData.getValue());
		}
	}

	private void saveToFile(String filekey, List<Boundary> bounds)
			throws IOException {
		File file = new File(boundsDir, "bounds_" + filekey + ".bnd");
		file.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(file);
		try {
			ObjectOutputStream obj = new ObjectOutputStream(out);
			for (Boundary bound : bounds) {
				obj.writeObject(bound);
			}
			obj.close();
		} finally {
			if (out != null)
				out.close();
		}
	}

	public static void main(String[] args) throws IOException,
			ClassNotFoundException {
		FileInputStream in = new FileInputStream(
				"C:/Garmin/Tools/mkgmap/index/test.bnd");
		ObjectInputStream obj = new ObjectInputStream(in);

		do {
			Object next = obj.readObject();
			System.out.println(next.getClass());
			if (next instanceof Boundary) {
				Boundary b = (Boundary) next;
				System.out.println(b.getArea().getBounds());
			}
		} while (true);
	}

}

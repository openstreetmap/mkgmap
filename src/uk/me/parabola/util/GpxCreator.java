package uk.me.parabola.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Way;

public class GpxCreator {
	private static final Logger log = Logger.getLogger(GpxCreator.class);

	public static String getGpxBaseName() {
		String tilePath = (log.threadTag() == null ? "unknown" : log.threadTag());
		
		int tilenameStart = tilePath.lastIndexOf("/");
		// check the case if the tiles are defined without path
		tilenameStart = (tilenameStart < 0 ? 0 : tilenameStart+1);
		
		int tilenameEnd = tilePath.lastIndexOf(".osm");
		if (tilenameEnd < tilenameStart) {
			// the tiles do not end with .osm*
			// do not cut the file ending
			tilenameEnd = tilePath.length();
		}
		
		return tilePath.substring(tilenameStart,tilenameEnd) + "/";
	}

	private static void addTrkPoint(PrintWriter pw, int latitude, int longitude) {
		addGpxPoint(pw, "trkpt", latitude, longitude);
	}

	private static void addWptPoint(PrintWriter pw, int latitude, int longitude) {
		addGpxPoint(pw, "wpt", latitude, longitude);
	}

	private static void addGpxPoint(PrintWriter pw, String type, int latitude,
			int longitude) {
		pw.print("<");
		pw.print(type);
		pw.print(" lat=\"");
		pw.print(Utils.toDegrees(latitude));
		pw.print("\" lon=\"");
		pw.print(Utils.toDegrees(longitude));
		pw.print("\"/>");
	}

	public static void createAreaGpx(String name, Area bbox) {
		List<Coord> points = new ArrayList<Coord>(5);
		points.add(new Coord(bbox.getMinLat(), bbox.getMinLong()));
		points.add(new Coord(bbox.getMaxLat(), bbox.getMinLong()));
		points.add(new Coord(bbox.getMaxLat(), bbox.getMaxLong()));
		points.add(new Coord(bbox.getMinLat(), bbox.getMaxLong()));
		points.add(new Coord(bbox.getMinLat(), bbox.getMinLong()));

		GpxCreator.createGpx(name, points);
	}
	
	/**
	 * Creates a gpx file for each way. The filename is the baseDir plus the id
	 * of the way.
	 * @param baseDir the base directory name
	 * @param ways list of ways
	 */
	public static void createGpx(String baseDir, Collection<Way> ways) {
		for (Way w : ways) {
			GpxCreator.createGpx(baseDir+w.getId(), w.getPoints());
		}
	}

	public static void createGpx(String name, List<Coord> points) {
		try {
			File f = new File(name);
			f.getParentFile().mkdirs();
			PrintWriter pw = new PrintWriter(new FileWriter(name + ".gpx"));
			pw.print("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" ");
			pw.print("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" version=\"1.1\" ");
			pw.print("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\"> ");

			pw.print("<trk><name>");
			pw.print(name);
			pw.print("</name><trkseg>");

			for (Coord c : points) {
				addTrkPoint(pw, c.getLatitude(), c.getLongitude());
			}
			pw.print("</trkseg></trk></gpx>");
			pw.close();
		} catch (Exception exp) {
			// only for debugging so just log
			log.warn("Could not create gpx file ", name);
		}
	}

	public static void createGpx(String name, List<Coord> polygonpoints,
			List<Coord> singlePoints) {
		try {
			File f = new File(name);
			f.getParentFile().mkdirs();
			PrintWriter pw = new PrintWriter(new FileWriter(name + ".gpx"));
			pw.print("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" ");
			pw.print("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" version=\"1.1\" ");
			pw.print("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\"> ");

			if (singlePoints != null) {
				for (Coord c : singlePoints) {
					addWptPoint(pw, c.getLatitude(), c.getLongitude());
				}
			}

			if (polygonpoints != null && polygonpoints.isEmpty() == false) {
				pw.print("<trk><name>");
				pw.print(name);
				pw.print("</name><trkseg>");

				for (Coord c : polygonpoints) {
					addTrkPoint(pw, c.getLatitude(), c.getLongitude());
				}
				pw.print("</trkseg></trk>");
			}
			pw.print("</gpx>");
			pw.close();
		} catch (Exception exp) {
			// only for debugging so just log
			log.warn("Could not create gpx file ", name);
		}
	}
}

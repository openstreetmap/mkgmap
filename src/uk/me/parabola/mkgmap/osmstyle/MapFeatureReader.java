package uk.me.parabola.mkgmap.osmstyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.GType;

/**
 * Read in a map-features file.  This is the 'old' way of specifying styles.
 * It is also supported in the new styles and can be used as a base.
 */
public class MapFeatureReader {
	private static final Logger log = Logger.getLogger(MapFeatureReader.class);

	private static final int DEFAULT_RESOLUTION = 24;

	private static final int F_FEATURE_TYPE = 0;
	private static final int F_OSM_TYPE = 1;
	private static final int F_OSM_SUBTYPE = 2;
	private static final int F_GARMIN_TYPE = 3;
	private static final int F_GARMIN_SUBTYPE = 4;
	private static final int F_MIN_RESOLUTION = 5;
	private static final int N_MIN_FIELDS = 5;

	private final Map<String, GType> pointFeatures = new HashMap<String, GType>();
	private final Map<String, GType> lineFeatures = new HashMap<String, GType>();
	private final Map<String, GType> shapeFeatures = new HashMap<String, GType>();

	/**
	 * Read the features from the file.
	 *
	 * @param in The open file.
	 * @throws IOException On any problems reading.
	 */
	public void readFeatures(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			if (line.trim().startsWith("#"))
				continue;

			String[] fields = line.split("\\|", -1);
			if (fields.length < N_MIN_FIELDS)
				continue;

			String type = fields[F_FEATURE_TYPE];
			log.debug("feature kind", type);
			if (type.equals("point")) {
				log.debug("point type found");
				saveFeature(GType.POINT, fields, pointFeatures);

			} else if (type.equals("polyline")) {
				log.debug("polyline type found");
				// Lines only have types and not subtypes on
				// the garmin side
				assert fields[F_GARMIN_SUBTYPE].length() == 0;
				saveFeature(GType.POLYLINE, fields, lineFeatures);

			} else if (type.equals("polygon")) {
				log.debug("polygon type found");
				assert fields[F_GARMIN_SUBTYPE].length() == 0;
				saveFeature(GType.POLYGON, fields, shapeFeatures);

			} else {
				// Unknown type
				log.warn("unknown feature type " + type);
			}
		}
	}

	/**
	 * Create a description from the fields and put it into the features map.
	 *
	 * @param fields The fields from the map-features file.
	 * @param features This is where the GarminType is put.
	 */
	private void saveFeature(int featureKind, String[] fields, Map<String, GType> features) {
		String osm = makeKey(fields[F_OSM_TYPE], fields[F_OSM_SUBTYPE]);

		GType gt;
		String gsubtype = fields[F_GARMIN_SUBTYPE];

		if (gsubtype == null || gsubtype.length() == 0) {
			gt = new GType(featureKind, fields[F_GARMIN_TYPE]);
		} else {
			gt = new GType(featureKind, fields[F_GARMIN_TYPE], gsubtype);
		}

		if (fields.length > F_MIN_RESOLUTION) {
			String field = fields[F_MIN_RESOLUTION];
			int res = DEFAULT_RESOLUTION;
			if (field != null && field.length() > 0) {
				res = Integer.valueOf(field);
				if (res < 0 || res > 24) {
					System.err.println("Warning: map feature resolution out of range");
					res = 24;
				}
			}
			gt.setMinResolution(res);
		} else {
			gt.setMinResolution(24);
		}
		features.put(osm, gt);
	}

	public Map<String, GType> getPointFeatures() {
		return pointFeatures;
	}

	public Map<String, GType> getLineFeatures() {
		return lineFeatures;
	}

	public Map<String, GType> getShapeFeatures() {
		return shapeFeatures;
	}

	private String makeKey(String key, String val) {
		return key + '=' + val;
	}
}

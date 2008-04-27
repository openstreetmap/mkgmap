package uk.me.parabola.mkgmap.reader.osm;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.log.Logger;

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

	private final Map<String, Type> pointFeatures = new HashMap<String, Type>();
	private final Map<String, Type> lineFeatures = new HashMap<String, Type>();
	private final Map<String, Type> shapeFeatures = new HashMap<String, Type>();

	/**
	 * Read the features from the file.
	 *
	 * @param in The open file.
	 * @throws IOException On any problems reading.
	 */
	void readFeatures(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			if (line.trim().startsWith("#"))
				continue;

			String[] fields = line.split("\\|", -1);
			if (fields.length < N_MIN_FIELDS)
				continue;

			String type = fields[F_FEATURE_TYPE];
			log.debug("feature kind " + type);
			if (type.equals("point")) {
				log.debug("point type found");
				saveFeature(fields, pointFeatures);

			} else if (type.equals("polyline")) {
				log.debug("polyline type found");
				// Lines only have types and not subtypes on
				// the garmin side
				assert fields[F_GARMIN_SUBTYPE].length() == 0;
				saveFeature(fields, lineFeatures);

			} else if (type.equals("polygon")) {
				log.debug("polygon type found");
				assert fields[F_GARMIN_SUBTYPE].length() == 0;
				saveFeature(fields, shapeFeatures);

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
	private void saveFeature(String[] fields, Map<String, Type> features) {
		String osm = makeKey(fields[F_OSM_TYPE], fields[F_OSM_SUBTYPE]);

		Type gtype;
		String gsubtype = fields[F_GARMIN_SUBTYPE];
		log.debug("subtype", gsubtype);
		if (gsubtype == null || gsubtype.length() == 0) {
			log.debug("took the subtype road");
			gtype = new Type(fields[F_GARMIN_TYPE]);
		} else {
			gtype = new Type(fields[F_GARMIN_TYPE], gsubtype);
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
			gtype.setMinResolution(res);
		} else {
			int res = getDefaultResolution(gtype.getType());
			gtype.setMinResolution(res);
		}
		features.put(osm, gtype);
	}

	public Map<String, Type> getPointFeatures() {
		return pointFeatures;
	}

	public Map<String, Type> getLineFeatures() {
		return lineFeatures;
	}

	public Map<String, Type> getShapeFeatures() {
		return shapeFeatures;
	}

	/**
	 * Get a default resolution based on the type only.  This is historical.
	 * @param type The garmin type field.
	 * @return The minimum resolution at which the feature will be displayed.
	 */
	private int getDefaultResolution(int type) {
		// The old way - there is a built in list of min resolutions based on
		// the element type, this will eventually go.  You can't distinguish
		// between points and lines here either.
		int res;
		switch (type) {
		case 1:
		case 2:
			res = 10;
			break;
		case 3:
			res = 18;
			break;
		case 4:
			res = 19;
			break;
		case 5:
			res = 21;
			break;
		case 6:
			res = 24;
			break;
		case 0x14:
		case 0x17:
			res = 20;
			break;
		case 0x15: // coast, make always visible
			res = 10;
			break;
		default:
			res = 24;
			break;
		}

		return res;
	}

	private String makeKey(String key, String val) {
		return key + '|' + val;
	}
}

package uk.me.parabola.mkgmap.osmstyle;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read a type description from a style file.
 */
public class TypeReader {
	private static final Logger log = Logger.getLogger(TypeReader.class);

	private final FeatureKind kind;
	private final LevelInfo[] levels;
	private static final Pattern HYPHEN_PATTERN = Pattern.compile("-");

	public TypeReader(FeatureKind kind, LevelInfo[] levels) {
		this.kind = kind;
		this.levels = levels;
	}

	public GType readType(TokenScanner ts){
		return readType(ts, false, null);
	}
	
	public GType readType(TokenScanner ts, boolean performChecks, Map<Integer, List<Integer>> overlays) {
		// We should have a '[' to start with
		Token t = ts.nextToken();
		if (t == null || t.getType() == TokType.EOF)
			throw new SyntaxException(ts, "No garmin type information given");

		if (!t.getValue().equals("[")) {
			throw new SyntaxException(ts, "No type definition");
		}

		ts.skipSpace();
		String type = ts.nextValue();
		if (!Character.isDigit(type.charAt(0)))
			throw new SyntaxException(ts, "Garmin type number must be first.  Saw '" + type + '\'');

		log.debug("gtype", type);

		GType gt = new GType(kind, type);
		while (!ts.isEndOfFile()) {
			ts.skipSpace();
			String w = ts.nextValue();
			if (w.equals("]"))
				break;

			if (w.equals("level")) {
				setLevel(ts, gt);
			} else if (w.equals("resolution")) {
				setResolution(ts, gt);
			} else if (w.equals("default_name")) {
				gt.setDefaultName(nextValue(ts));
			} else if (w.equals("road_class")) {
				gt.setRoadClass(nextIntValue(ts));
			} else if (w.equals("road_speed")) {
				gt.setRoadSpeed(nextIntValue(ts));
			} else if (w.equals("copy")) {
				// Reserved
			} else if (w.equals("continue")) {
				gt.setContinueSearch(true);
				// By default no propagate of actions on continue 
				gt.propagateActions(false);
			} else if (w.equals("propagate") || w.equals("with_actions") || w.equals("withactions")) {
				gt.propagateActions(true);
			} else if (w.equals("no_propagate")) {
				gt.propagateActions(false);
			} else if (w.equals("oneway")) {
				// reserved
			} else if (w.equals("access")) {
				// reserved
			} else {
				throw new SyntaxException(ts, "Unrecognised type command '" + w + '\'');
			}
		}

		gt.fixLevels(levels);
		if (performChecks){
			boolean fromOverlays = false;
			List<Integer> usedTypes = null;
			if (overlays != null){
				usedTypes = overlays.get(gt.getType());
				if (usedTypes != null)
					fromOverlays = true;
			}
			if (usedTypes == null)
				usedTypes = Arrays.asList(gt.getType());
			for (Integer usedType: usedTypes){
				boolean isOk = true;
				if (usedType >= 0x010000){
					if ((usedType & 0xff) > 0x1f)
						isOk = false;
				} else {
					if (kind == FeatureKind.POLYLINE && usedType > 0x3f)
						isOk = false;
					else if (kind == FeatureKind.POLYGON && usedType> 0x7f)
						isOk = false;
					else if (kind == FeatureKind.POINT){
						if (usedType < 0x0100 || (usedType & 0x00ff) > 0x1f) 
							isOk = false;
					}
				}
				if (!isOk){
					String msg = "Warning: invalid type " + type + " for " + kind + " in style file " + ts.getFileName() + ", line " + ts.getLinenumber();
					if (fromOverlays)
						msg += ". Type is overlaid with " + String.format("0x%x", usedType);
					System.err.println(msg);
				}
				if (kind == FeatureKind.POLYLINE && gt.getMaxResolution() == 24 && usedType >= 0x01 && usedType <= 0x13){
					if (gt.isRoad() == false){
						String msg = "Warning: routable type " + type  + " is used for non-routable line with resolution 24. This may break routing. Style file "+ ts.getFileName() + ", line " + ts.getLinenumber();
						if (fromOverlays)
							msg += ". Type is overlaid with " + String.format("0x%x", usedType);
						System.err.println(msg);
					}
				}
			}
		}
		
		return gt;
	}

	private int nextIntValue(TokenScanner ts) {
		if (ts.checkToken("="))
			ts.nextToken();
		try {
			return ts.nextInt();
		} catch (NumberFormatException e) {
			throw new SyntaxException(ts, "Expecting numeric value");
		}
	}

	/**
	 * Get the value in a 'name=value' pair.
	 */
	private String nextValue(TokenScanner ts) {
		if (ts.checkToken("="))
			ts.nextToken();
		return ts.nextWord();
	}

	/**
	 * A resolution can be just a single number, in which case that is the
	 * min resolution and the max defaults to 24.  Or a min to max range.
	 */
	private void setResolution(TokenScanner ts, GType gt) {
		String str = ts.nextWord();
		log.debug("res word value", str);
		try {
			if (str.indexOf('-') >= 0) {
				String[] minmax = HYPHEN_PATTERN.split(str, 2);
				// Previously there was a bug where the order was reversed, so we swap the numbers if they are
				// the wrong way round.
				// This is not done for level as that never had the bug.
				int val1 = Integer.parseInt(minmax[0]);
				int val2 = Integer.parseInt(minmax[1]);
				if (val1 <= val2) {
					gt.setMinResolution(val1);
					gt.setMaxResolution(val2);
				} else {
					gt.setMinResolution(val2);
					gt.setMaxResolution(val1);
				}
			} else {
				gt.setMinResolution(Integer.parseInt(str));
			}
		} catch (NumberFormatException e) {
			gt.setMinResolution(24);
		}
	}

	/**
	 * Read a level spec, which is either the max level or a min to max range.
	 * This is immediately converted to resolution(s).
	 */
	private void setLevel(TokenScanner ts, GType gt) {
		String str = ts.nextWord();
		try {
			if (str.indexOf('-') >= 0) {
				String[] minmax = HYPHEN_PATTERN.split(str, 2);
				gt.setMaxResolution(toResolution(Integer.parseInt(minmax[0])));
				gt.setMinResolution(toResolution(Integer.parseInt(minmax[1])));
			} else {
				gt.setMinResolution(toResolution(Integer.parseInt(str)));
			}
		} catch (NumberFormatException e) {
			gt.setMinResolution(24);
		}
	}

	private int toResolution(int level) {
		int max = levels.length - 1;
		if (level > max)
			throw new SyntaxException("Level number too large, max=" + max);

		return levels[max - level].getBits();
	}
}

package uk.me.parabola.mkgmap.osmstyle;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read a type description from a style file.
 */
public class TypeReader {
	private static final Logger log = Logger.getLogger(TypeReader.class);

	private final int kind;
	private final LevelInfo[] levels;

	public TypeReader(int kind, LevelInfo[] levels) {
		this.kind = kind;
		this.levels = levels;
	}

	public GType readType(TokenScanner ts) {
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
				// reserved word.  not currently used
			} else {
				throw new SyntaxException(ts, "Unrecognised type command '" + w + '\'');
			}
		}

		gt.fixLevels(levels);
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

	private void setResolution(TokenScanner ts, GType gt) {
		String str = ts.nextWord();
		log.debug("res word value", str);
		try {
			if (str.indexOf('-') >= 0) {
				String[] minmax = str.split("-", 2);
				gt.setMaxResolution(Integer.parseInt(minmax[0]));
				gt.setMinResolution(Integer.parseInt(minmax[1]));
			} else {
				gt.setMinResolution(Integer.parseInt(str));
			}
		} catch (NumberFormatException e) {
			gt.setMinResolution(24);
		}
	}

	private void setLevel(TokenScanner ts, GType gt) {
		String str = ts.nextWord();
		try {
			if (str.indexOf('-') >= 0) {
				String[] minmax = str.split("-", 2);
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

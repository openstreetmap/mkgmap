package uk.me.parabola.mkgmap.osmstyle;

import uk.me.parabola.log.Logger;
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

	public TypeReader(int kind) {
		this.kind = kind;
	}

	public GType readType(TokenScanner ts) {
		// We should have a '[' to start with
		ts.skipSpace();
		Token t = ts.nextToken();
		if (t == null || t.getType() == TokType.EOF)
			throw new SyntaxException(ts, "No garmin type information given");

		if (!t.getValue().equals("[")) {
			SyntaxException e = new SyntaxException(ts, "No type definition");
			throw e;
		}

		String type = ts.nextValue();
		if (!Character.isDigit(type.charAt(0)))
			throw new SyntaxException(ts, "Garmin type number must be first.  Saw '" + type + '\'');

		log.debug("gtype", type);
		GType gt = new GType(kind, type);

		while (!ts.isEndOfFile()) {
			ts.skipSpace();
			log.debug("type ", ts.peekToken().getType(), "val=", ts.peekToken().getValue());
			String w = ts.nextValue();
			assert !ts.isEndOfFile();
			if (w.equals("]"))
				break;
			if (w.equals("level")) {
				ts.skipSpace();
				String val = ts.nextValue();
				log.debug("level val=", val);
				throw new SyntaxException(ts, "The level command is not yet implemented, coming soon...");
			} else if (w.equals("resolution")) {
				ts.skipSpace();
				setResolution(ts, gt);
			} else {
				throw new SyntaxException(ts, "Unrecognised type command '" + w + '\'');
			}
			ts.skipSpace();
		}

		return gt;
	}

	private void setResolution(TokenScanner ts, GType gt) {
		gt.setMinResolution(ts.nextInt());
	}
}

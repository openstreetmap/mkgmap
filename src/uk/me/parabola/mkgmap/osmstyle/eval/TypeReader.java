package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read a type description from a style file.
 */
public class TypeReader {
	private static final Logger log = Logger.getLogger(TypeReader.class);

	//private final RuleFileReader ruleFileReader;
	private final int kind;

	public TypeReader(int kind) {
		this.kind = kind;
	}

	public GType readType(TokenScanner ts) {
		// We should have a '{' to start with
		ts.skipSpace();
		Token t = ts.nextToken();
		if (t == null || t.getType() == TokType.EOF)
			throw new SyntaxException(ts.getLinenumber(), "No garmin type information given");

		if (!t.getValue().equals("{")) {
			SyntaxException e = new SyntaxException(ts.getLinenumber(), "No type definition");
			throw e;
		}

		String type = ts.nextValue();
		if (!Character.isDigit(type.charAt(0)))
			throw new SyntaxException(ts.getLinenumber(), "Garmin type number must be first.  Saw '" + type + '\'');

		log.debug("gtype", type);
		//int level;
		//int resolution;
		while (!ts.isEndOfFile()) {
			ts.skipSpace();
			log.debug("type ", ts.peekToken().getType(), "val=", ts.peekToken().getValue());
			String w = ts.nextValue();
			assert !ts.isEndOfFile();
			if (w.equals("}"))
				break;
			if (w.equals("level")) {
				ts.skipSpace();
				String val = ts.nextValue();
				log.debug("level val=", val);
			} else if (w.equals("resolution")) {
				ts.skipSpace();
				String val = ts.nextValue();
				log.debug("resolution val=", val);
			} else {
				throw new SyntaxException(ts.getLinenumber(), "Unrecognised type command '" + w + '\'');
			}
			ts.skipSpace();
		}

		GType gt = new GType(kind, type);
		return gt;
	}
}

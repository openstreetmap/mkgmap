/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 02-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.osmstyle.eval.TypeReader;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read a rules file.  A rules file contains a list of rules and the
 * resulting garmin type, should the rule match.
 *
 * <pre>
 * 
 * </pre>
 * @author Steve Ratcliffe
 */
public class RuleFileReader {
	private static final Logger log = Logger.getLogger(RuleFileReader.class);

	private final ExpressionReader expressionReader = new ExpressionReader(new Stack<Op>(), new Stack<Op>());
	private final TypeReader typeReader;

	private Map<String, Rule> rules = new HashMap<String, Rule>();

	public RuleFileReader(int kind) {
		typeReader = new TypeReader(kind);
	}

	/**
	 * Read a rules file.
	 * @param loader A file loader.
	 * @param name The name of the file to open.
	 * @throws FileNotFoundException If the given file does not exist.
	 */
	public void load(StyleFileLoader loader, String name) throws FileNotFoundException {
		Reader r = loader.open(name);
		load(r);
	}

	private void load(Reader r) {
		TokenScanner ts = new TokenScanner(r);

		// Read all the rules in the file.
		while (!ts.isEndOfFile()) {
			ts.skipSpace();
			if (ts.peekToken().getType() == TokType.EOF)
				break;

			// A rule is followed by a type definition.

			saveRule(expressionReader.readConditions(ts), typeReader.readType(ts));
		}
	}

	public Map<String, Rule> getRules() {
		return rules;
	}

	private void saveRule(Op op, GType gt) {
		log.debug("save rule", op);

		// E1 | E2 {type...} is exactly the same as:
		// E1 {type...}
		// E2 {type...}
		if (op.getType() == Op.OR) {
			saveRule(op.getFirst(), gt);
			saveRule(((BinaryOp) op).getSecond(), gt);
			return;
		}

		if (op instanceof BinaryOp) {
			BinaryOp binaryOp = (BinaryOp) op;
			Op first = binaryOp.getFirst();
			Op second = binaryOp.getSecond();

			log.debug("binop", op.getType(), first.getType());
			if (op.getType() == Op.EQUALS && first.getType() == Op.VALUE) 
				save(op.toString(), new FixedRule(gt));
			else if (op.getType() == Op.AND && first.getType() == Op.EQUALS) {
				save(first.toString(), new ExpressionRule(second, gt));
			} else {
				throw new SyntaxException("Invalid rule file");
			}
		} else {
			// TODO: better message ;)
			throw new SyntaxException("Invalid rule file");
		}
	}

	private void save(String s, Rule rule) {
		log.debug("saving", s, " R:", rule);
		Rule existingRule = rules.get(s);
		if (existingRule == null) {
			rules.put(s, rule);
		} else {
			if (existingRule instanceof SequenceRule) {
				((SequenceRule) existingRule).add(rule);
			} else {
				// There was already a single rule there.  Create a sequence
				// rule and add the existing and the new rule to it.
				SequenceRule sr = new SequenceRule();
				sr.add(existingRule);
				sr.add(rule);
				rules.put(s, sr);
			}
		}
	}

	public static void main(String[] args) {
		StringReader r = new StringReader("highway=footway | highway = path" +
				"{0x23 level 2}\n" +
				"foo=bar & bar=two {0x1}\n" +
				"amenity=pub {0x2}\n" +
				"highway=footway & type=rough {0x3}\n" +
				"");
		RuleFileReader rr = new RuleFileReader(GType.POLYLINE);
		rr.load(r);
		System.out.println("Result: " + rr.rules);
	}
}

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

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
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

	private final TypeReader typeReader;

	private final RuleSet rules;
	private TokenScanner scanner;

	public RuleFileReader(int kind, RuleSet rules) {
		this.rules = rules;
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
		load(name, r);
	}

	private void load(String name, Reader r) {
		scanner = new TokenScanner(name, r);

		ExpressionReader expressionReader = new ExpressionReader(scanner);

		// Read all the rules in the file.
		while (!scanner.isEndOfFile()) {
			scanner.skipSpace();
			if (scanner.peekToken().getType() == TokType.EOF)
				break;

			saveRule(expressionReader.readConditions(), typeReader.readType(scanner));
		}
	}

	public RuleSet getRules() {
		return rules;
	}

	private void saveRule(Op op, GType gt) {
		log.info("EXP", op, ", type=", gt);

		// E1 | E2 {type...} is exactly the same as:
		// E1 {type...}
		// E2 {type...}
		if (op.isType(Op.OR)) {
			saveRule(op.getFirst(), gt);
			saveRule(((BinaryOp) op).getSecond(), gt);
			return;
		}

		if (op instanceof BinaryOp) {
			BinaryOp binaryOp = (BinaryOp) op;
			Op first = binaryOp.getFirst();
			Op second = binaryOp.getSecond();

			log.debug("binop", op.getType(), first.getType());
			if (op.isType(Op.EQUALS) && first.isType(Op.VALUE)) {
				String s = op.toString();
				Rule rule = new FixedRule(gt);
				log.debug("saving", s, " R:", rule);
				rules.add(s, rule);
			}
			else if (op.isType(Op.AND) && first.isType(Op.EQUALS)) {
				String s = first.toString();
				Rule rule = new ExpressionRule(second, gt);
				log.debug("saving", s, " R:", rule);
				rules.add(s, rule);
			} else if (op.isType(Op.AND) && (first.isType(Op.EXISTS) || first.isType(Op.NOT_EXISTS))){
				throw new SyntaxException(scanner, "Cannot yet start rule with tag=*");
			} else {
				throw new SyntaxException(scanner, "Invalid rule file (expr " + op.getType() +')');
			}
		} else {
			// TODO: better message ;)
			throw new SyntaxException(scanner, "Invalid rule file");
		}
	}

	public static void main(String[] args) {
		StringReader r = new StringReader("a=b & (c=d | e=f)[0x1]\n" +
				"highway=footway & highway = path\n" +
				"[0x23 resolution 22]\n" +
				"foo=\nbar & bar=two [0x1]\n" +
				"amenity=pub [0x2]\n" +
				"highway=footway & type=rough [0x3]\n" +
				//"highway=* & oneway=true [0x0]\n" +
				"");
		RuleSet rs = new RuleSet();
		RuleFileReader rr = new RuleFileReader(GType.POLYLINE, rs);
		rr.load("string", r);
		log.info("Result: " + rs.getMap());
	}
}

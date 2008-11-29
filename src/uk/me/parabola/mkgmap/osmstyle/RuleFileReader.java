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
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.osmstyle.actions.Action;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.AND;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.EQUALS;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.EXISTS;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.NOT_EXISTS;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.OR;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.VALUE;
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

	public RuleFileReader(int kind, LevelInfo[] levels, RuleSet rules) {
		this.rules = rules;
		typeReader = new TypeReader(kind, levels);
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
		scanner.setExtraWordChars("-:");

		ExpressionReader expressionReader = new ExpressionReader(scanner);
		ActionReader actionReader = new ActionReader(scanner);

		// Read all the rules in the file.
		scanner.skipSpace();
		while (!scanner.isEndOfFile()) {
			Op expr = expressionReader.readConditions();

			// TODO this fails when action list is just empty... although perhaps it should.
			List<Action> actions = actionReader.readActions();

			// If there is an action list, then we don't need a type
			GType type = null;
			if (actions.isEmpty()) {
				if (scanner.checkToken(TokType.SYMBOL, "["))
					type = typeReader.readType(scanner);
				else
					throw new SyntaxException(scanner, "No type definition given");
			}
			
			saveRule(expr, actions, type);
			scanner.skipSpace();
		}
	}

	/**
	 * Save the expression as a rule.  We need to extract an index such
	 * as highway=primary first and then add the rest of the expression as
	 * the condition for it.
	 *
	 * So in other words each condition is dropped into a number of different
	 * baskets based on the first 'tag=value' term.  We then only look
	 * for expressions that are in the correct basket.  For each expression
	 * in a basket we know that the first term is true so we can drop that
	 * from the expression.
	 */
	private void saveRule(Op op, List<Action> actions, GType gt) {
		log.info("EXP", op, ", type=", gt);

		// E1 | E2 {type...} is exactly the same as the two rules:
		// E1 {type...}
		// E2 {type...}
		// so just recurse on each term, throwing away the original OR.
		if (op.isType(OR)) {
			saveRule(op.getFirst(), actions, gt);
			saveRule(((BinaryOp) op).getSecond(), actions, gt);
			return;
		}

		if (op instanceof BinaryOp) {
			optimiseAndSaveBinaryOp(op, actions, gt);
		} else {
			throw new SyntaxException(scanner, "Invalid operation '" + op.getType() + "' at top level");
		}
	}

	/**
	 * Optimise the expression tree, extract the primary key and
	 * save it as a rule.
	 */
	private void optimiseAndSaveBinaryOp(Op op, List<Action> actions, GType gt) {
		BinaryOp binaryOp = (BinaryOp) op;
		Op first = binaryOp.getFirst();
		Op second = binaryOp.getSecond();

		log.debug("binop", op.getType(), first.getType());

		/*
         * We allow the following cases:
		 * An EQUALS at the top.
		 * An AND at the top level.
		 * (The case that there is an OR at the top level has already been
		 * dealt with)
         */
		String keystring;
		Op expr;
		if (op.isType(EQUALS)) {
			if (first.isType(VALUE) && second.isType(VALUE)) {
				keystring = op.toString();
				expr = null;
			} else {
				throw new SyntaxException(scanner, "Invalid rule file (expr " + op.getType() +')');
			}
		} else if (op.isType(AND)) {
			if (first.isType(EQUALS)) {
				keystring = first.toString();
				expr = second;
			} else if (second.isType(EQUALS)) {
				// Swap the terms and everything will be fine.
				keystring = second.toString();
				expr = first;
			} else if (first.isType(EXISTS) || first.isType(NOT_EXISTS)) {
				throw new SyntaxException(scanner, "Cannot start rule with tag(!)=* (yet)");
			} else {
				throw new SyntaxException(scanner, "Invalid rule file (expr " + op.getType() +')');
			}
		} else {
			throw new SyntaxException(scanner, "Invalid operation '" + op.getType() + "' at top level");
		}

		Rule rule;
		if (!actions.isEmpty())
			rule = new ActionRule(expr, actions, gt);
		else if (expr != null) {
			rule = new ExpressionRule(expr, gt);
		} else {
			rule = new FixedRule(gt);
		}

		rules.add(keystring, rule);
	}

	public static void main(String[] args) throws FileNotFoundException {
		Reader r;
		if (args.length > 0) {
			r = new FileReader(args[0]);
		} else {
			r = new StringReader(
					"a=b & (c=d | e=f) & x>10 [0x1]\n" +
					"highway=footway & highway = path\n" +
							"[0x23 resolution 22]\n" +
							"foo=\nbar & bar=two [0x1]\n" +
							"amenity=pub [0x2]\n" +
							"highway=footway & type=rough [0x3 level 2]\n" +
							"highway=* & oneway=true [0x0]\n" +
							"");
		}
		RuleSet rs = new RuleSet();
		RuleFileReader rr = new RuleFileReader(GType.POLYLINE, LevelInfo.createFromString("0:24 1:20 2:18 3:16 4:14"), rs);
		rr.load("string", r);
		log.info("Result: " + rs);
	}
}

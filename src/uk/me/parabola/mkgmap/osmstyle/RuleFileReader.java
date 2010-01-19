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
import java.util.List;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.osmstyle.actions.Action;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.AndOp;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.OrOp;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.scan.TokenScanner;

import static uk.me.parabola.mkgmap.osmstyle.eval.Op.*;

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
		load(r, name);
	}

	void load(Reader r, String name) {
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
			if (scanner.checkToken("["))
				type = typeReader.readType(scanner);
			else if (actions == null)
				throw new SyntaxException(scanner, "No type definition given");

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

		//System.out.println("From: " + op);
		Op op2 = rearrangeExpression(op);
		//System.out.println("RE: " + op2);

		if (op2 instanceof BinaryOp) {
			optimiseAndSaveBinaryOp((BinaryOp) op2, actions, gt);
		} else {
			optimiseAndSaveOtherOp(op2, actions, gt);
		}
	}

	/**
	 * Rearrange the expression so that it is solvable, that is it starts with
	 * an EQUALS or an EXISTS.
	 * @param op The expression to be rearranged.
	 * @return An equivelent expression re-arranged so that it starts with an
	 * indexable term. If that is not possible then the original expression is
	 * returned.
	 */
	private static Op rearrangeExpression(Op op) {
		if (isFinished(op))
			return op;

		if (op.isType(AND)) {
			// If the first term is an EQUALS or EXISTS then this subtree is
			// already solved and we need to do no more.
			Op op1 = rearrangeExpression(op.getFirst());
			Op op2 = rearrangeExpression(((AndOp) op).getSecond());

			if (isSolved(op1)) {
				return rearrangeAnd((AndOp) op, op1, op2);
			} else if (isSolved(op2)) {
				return rearrangeAnd((AndOp) op, op2, op1);
			}
		}

		return op;
	}

	/**
	 * Rearrange an AND expression so that it can be executed with indexable
	 * terms at the front.
	 * @param top This will be an AndOp.
	 * @param op1 This is a child of top that is guaranteed to be
	 * solved already.
	 * @param op2 This expression is the other child of top.
	 * @return A re-arranged expression with an indexable term at the beginning
	 * or several such expressions ORed together.
	 */
	private static BinaryOp rearrangeAnd(AndOp top, Op op1, Op op2) {
		if (isIndexable(op1)) {
			top.setFirst(op1);
			top.setSecond(op2);
			return top;
		} else if (op1.isType(AND)) {
			// The first term is AND.
			// Its first must be indexable (EQUALS or EXIST).
			// Make that our first term.
			Op first = op1.getFirst();
			if (isIndexable(first)) {
				top.setFirst(first);
				op1.setFirst(op2);
				top.setSecond(op1);
				return top;
			}
		} else if (op1.isType(OR)) {
			// Transform ((first | second) & topSecond)
			// into (first & topSecond) | (second & topSecond)

			Op first = op1.getFirst();
			OrOp orOp = new OrOp();

			Op topSecond = top.getSecond();

			AndOp and1 = new AndOp();
			and1.setFirst(first);
			and1.setSecond(topSecond);

			AndOp and2 = new AndOp();
			Op second = rearrangeExpression(((OrOp) op1).getSecond());
			and2.setFirst(second);
			and2.setSecond(topSecond);

			orOp.setFirst(and1);
			orOp.setSecond(and2);
			return orOp;
		} else {
			// This shouldn't happen
			throw new SyntaxException("X3" + op1.getType());
		}
		return top;
	}

	/**
	 * True if this operation can be indexed.  It is a plain equality or
	 * Exists operation.
	 */
	private static boolean isIndexable(Op op) {
		return op.isType(EQUALS) || op.isType(EXISTS);
	}

	/**
	 * True if this expression is 'solved'.  This means that the first term
	 * is indexable or it is indexable itself.
	 */
	private static boolean isSolved(Op op) {
		return isIndexable(op) || isIndexable(op.getFirst());
	}

	/**
	 * True if there is nothing more that we can do to rearrange this expression.
	 * It is either solved or it cannot be solved.
	 */
	private static boolean isFinished(Op op) {
		if (isSolved(op))
			return true;
		char type = op.getType();
		switch (type) {
		case AND:
		case OR: // possibly not required
			return false;
		default:
			return true;
		}
	}

	private void optimiseAndSaveOtherOp(Op op, List<Action> actions, GType gt) {
		if (op.isType(EXISTS)) {
			createAndSaveRule(op.value() + "=*", null, actions, gt);
		} else {
			throw new SyntaxException(scanner, "Invalid operation '" + op.getType() + "' at top level");
		}
	}

	/**
	 * Optimise the expression tree, extract the primary key and
	 * save it as a rule.
	 * @param op a binary expression
	 * @param actions list of actions to execute on match
	 * @param gt the Garmin type of the element
	 */
	private void optimiseAndSaveBinaryOp(BinaryOp op, List<Action> actions, GType gt) {
		Op first = op.getFirst();
		Op second = op.getSecond();

		log.debug("binop", op.getType(), first.getType());

		/*
		 * We allow the following cases:
		 * An EQUALS at the top.
		 * An AND at the top level.
		 * An OR at the top level.
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

			} else if (first.isType(EXISTS)) {
				keystring = first.value() + "=*";
				expr = second;

			} else if (first.isType(NOT_EXISTS)) {
				throw new SyntaxException(scanner, "Cannot start rule with tag!=*");
			} else {
				throw new SyntaxException(scanner, "Invalid rule file (expr " + op.getType() +')');
			}
		} else if (op.isType(OR)) {
			saveRule(first, actions, gt);
			saveRule(second, actions, gt);
			return;
		} else {
			throw new SyntaxException(scanner, "Invalid operation '" + op.getType() + "' at top level");
		}

		createAndSaveRule(keystring, expr, actions, gt);
	}

	private void createAndSaveRule(String keystring, Op expr, List<Action> actions, GType gt) {
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
		if (args.length > 0) {
			Reader r = new FileReader(args[0]);
			RuleSet rs = new RuleSet();
			RuleFileReader rr = new RuleFileReader(GType.POLYLINE,
					LevelInfo.createFromString("0:24 1:20 2:18 3:16 4:14"), rs);
			rr.load(r, "string");
			log.info("Result: " + rs);
		} else {
			System.err.println("Usage: RuleFileReader <file>");
		}
	}
}

/*
 * Copyright (C) 2017.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.osmstyle.eval.AbstractOp;
import uk.me.parabola.mkgmap.osmstyle.eval.AndOp;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.EqualsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExistsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.GTEOp;
import uk.me.parabola.mkgmap.osmstyle.eval.GTOp;
import uk.me.parabola.mkgmap.osmstyle.eval.LTEOp;
import uk.me.parabola.mkgmap.osmstyle.eval.LTOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NodeType;
import uk.me.parabola.mkgmap.osmstyle.eval.NotEqualOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NotExistsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NotRegexOp;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.OrOp;
import uk.me.parabola.mkgmap.osmstyle.eval.RegexOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;

import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.*;

/**
 * Routines to re-arrange a rule expression so that it can be used by the
 * mkgmap rule engine.
 */
public class ExpressionArranger {
	// Combining operation types.
	private static final EnumSet<NodeType> OPERATORS = EnumSet.of(AND, OR, NOT);
	// Combining operations that are binary
	private static final EnumSet<NodeType> BIN_OPERATORS = EnumSet.of(AND, OR);

	// These types need to be combined with EXISTS if they are first.
    private static final EnumSet<NodeType> NEED_EXISTS = EnumSet.of(GT, GTE, LT, LTE, REGEX, NOT_REGEX);

    // The invertible op types, basically everything apart from the value types
	private static final EnumSet<NodeType> INVERTIBLE;
	static {
		INVERTIBLE = EnumSet.allOf(NodeType.class);
		INVERTIBLE.removeAll(EnumSet.of(VALUE, FUNCTION));
	}

    Logger log = Logger.getLogger(getClass());

	public Op arrange(Op expr) {
		log.debug("IN: " + fmtExpr(expr));
		Op op = arrangeTop(expr);
		log.debug("OUT: " + fmtExpr(expr));
		return op;
	}

	private Op arrangeTop(Op expr) {
		if (!OPERATORS.contains(expr.getType()))
			return expr;

		// Remove all NOT operations from the whole tree
		Op op = removeAllNot(expr);

		if (BIN_OPERATORS.contains(op.getType()))
			orderBest(op);

		switch (op.getType()) {
		case AND:
			reAssociate(op, AND);

			// A, B, ... in the best order
			arrangeAndChain(op);

			// If we have an OR to the left after all this, we have to get rid of it.
			// This will turn this node into an OR.
			if (op.getFirst().isType(OR)) {
				op = distribute(op);

				// Now arrange this new OR
				arrangeTop(op);
			}
			break;

		case OR:
			arrangeOr(op);
			break;
		}

		return op;
	}

	/**
	 * Each side of an OR is effectively a separate rule, so check each.
	 *
	 * The input should be a chain or ORs, we test each part as if it were
	 * a complete expression.
	 *
	 * If there are any ORs on the left (first) then they are merged into the chain,
	 * so at the end all ORs are to the right (second).
	 */
	private void arrangeOr(Op op) {
		Op last = op;
		for (Op current = op; current != null && current.isType(OR); current = current.getSecond()) {
			Op newop = arrangeTop(current.getFirst());
			current.setFirst(newop);
			while (current.getFirst().isType(OR)) {
				reAssociate(current, OR);
			}

			last = current;
		}
		Op newop = arrangeTop(last.getSecond());
		last.setSecond(newop);
	}

	/**
	 * Create a new OR expression from OR and an other expression.
	 *
	 * Starting point is a node of the form (a|b|...) & c
	 *
	 * The output is (a & c) | (b & c) | ...
	 */
	private Op distribute(Op op) {
		Op ab = op.getFirst();
		Op a = ab.getFirst();
		Op b = ab.getSecond();
		Op c = op.getSecond();

		assert a != b : "ab";
		assert b != c : "bc";

		// Collect the OR terms into a list
		List<Op> orterms = new ArrayList<>();
		while (b.isType(OR)) {
			orterms.add(b.getFirst());

			b = b.getSecond();
		}
		OrOp topOR = new OrOp();

		topOR.setFirst(new AndOp().set(a, c));
		OrOp current = topOR;
		for (Op orterm : orterms) {
			AndOp and = new AndOp().set(orterm, c.copy());
			OrOp newOr = new OrOp().set(and, null);
			current.setSecond(newOr);
			current = newOr;
		}
		current.setSecond(new AndOp().set(b, c.copy()));

		return topOR;
	}

	/**
	 * Order the child nodes so that the 'best' one is on the left (first).
	 */
	private void orderBest(Op op) {
		assert OPERATORS.contains(op.getType());

		if (leftNodeWeight(op.getFirst()) > leftNodeWeight(op.getSecond())) {
			op.set(op.getSecond(), op.getFirst());
		}
	}

	/**
	 * Which node should be on the left?
	 *
	 * We prefer AND to OR and prefer everything else to AND.
	 */
	private int leftNodeWeight(Op op) {
		switch (op.getType()) {
		case AND: return 10;
		case OR: return 20;
		default: return 0;
		}
	}

	/**
	 * Scan the whole tree and remove all the not operations.
	 *
	 * Each node that is preceded by NOT is inverted.
	 */
	private Op removeAllNot(Op expr) {
		if (expr == null)
			return null;

		Op op = expr;
		while (op.isType(NOT) && INVERTIBLE.contains(op.getFirst().getType()))
			op = removeNot(op);

		if (OPERATORS.contains(op.getType())) {
			op.set(
					removeAllNot(op.getFirst()),
					removeAllNot(op.getSecond())
			);
		}
		return op;
	}

	/**
	 * Remove a NOT operation.
	 *
	 * This is complicated by the fact that !(a<2) is not the same as a>=2 but
	 * in fact is (a>=2 | a!=*)
	 *
	 * @param op This will be a NOT node.
	 * @return A new expression, could be the same as given.
	 */
	private Op removeNot(Op op) {
		return invert(op.getFirst());
	}

	private Op invert(Op op) {
		switch (op.getType()) {
		case NOT:
			Op f = op.getFirst();
			while (f != null && f.isType(NOT) && f.getFirst().isType(NOT))
				f = f.getFirst().getFirst();
			return f;
		case EQUALS:
			return new NotEqualOp().set(op.getFirst(), op.getSecond());
		case GT:
			return neWith(new LTEOp().set(op.getFirst(), op.getSecond()));
		case GTE:
			return neWith(new LTOp().set(op.getFirst(), op.getSecond()));
		case LT:
			return neWith(new GTEOp().set(op.getFirst(), op.getSecond()));
		case LTE:
			return neWith(new GTOp().set(op.getFirst(), op.getSecond()));
		case NOT_EQUALS:
			return new EqualsOp().set(op.getFirst(), op.getSecond());
		case EXISTS:
			new NotExistsOp().setFirst(op.getFirst());
			return new NotExistsOp();
		case NOT_EXISTS:
			return new ExistsOp().setFirst(op.getFirst());
		case OR:
			// !(A | B) -> !A & !B
			return new AndOp().<AndOp>set(
					invert(op.getFirst()),
					invert(op.getSecond())
			);

		case AND:
			// !(A & B) -> !A | !B
			return new OrOp().<OrOp>set(
					invert(op.getFirst()),
					invert(op.getSecond())
			);
		case REGEX:
			return new NotRegexOp().set(op.getFirst(), op.getSecond());
		case NOT_REGEX:
			return new RegexOp().set(op.getFirst(), op.getSecond());
		case VALUE:
		case FUNCTION:
		case OPEN_PAREN:
		case CLOSE_PAREN:
			throw new ExitException("Programming error, tried to invert invalid node " + op);
		}
		return null;
	}

	/**
	 * Combine the given operation with NOT_EXISTS.
	 *
	 * This is used when inverting nodes because !(a>0) is (a<=0 | a!=*) because
	 * the statement is true when the tag does not exist.
	 */
	private Op neWith(Op op) {
		return new OrOp().set(
				new NotExistsOp().setFirst(op.getFirst()),
				op
		);
	}

	/**
	 * Fix a chain of AND/OR nodes so that the chain is on the right.
	 *
	 * Eg: given (A&B)&(C&D) we return (A&(B&(C&D)))
	 */
	private void reAssociate(Op op, NodeType kind) {
		assert op.isType(kind);
		assert kind == OR || kind == AND;

		// Rearrange ((A&B)&C) to (A&(B&C)).
		while (op.getFirst().isType(kind)) {
			Op aAndB = op.getFirst();
			Op a = aAndB.getFirst();
			Op b = aAndB.getSecond();
			Op c = op.getSecond();

			assert a != b;
			assert a != c;
			assert b != c;

			BinaryOp and = AbstractOp.createOp(kind).set(b, c);
			op.set(a, and);
		}
	}

	/**
	 * Starting with A&(B&(C&(...))) order A,B,C into the best order.
	 *
	 * If any of A,B.. happen to be AND terms, then these are merged into the
	 * chain first.  If there is an OR on the left it will float to the back.
	 */
	private void arrangeAndChain(Op op) {
		Op last = op;
		List<Op> terms = new ArrayList<>();
		terms.add(op.getFirst());

		for (Op second = op.getSecond(); second != null && second.isType(AND); second = second.getSecond()) {
			reAssociate(second, AND);
			terms.add(second.getFirst());
			last = second;
		}

		for (int i = 0; i < terms.size(); i++) {
			Op o = terms.get(i);
			if (selectivity(o) > selectivity(last.getSecond())) {
				Op tmp = last.getSecond();
				last.setSecond(o);
				terms.set(i, tmp);
			}
		}

		if (terms.size() > 1)
			terms.sort(Comparator.comparingInt(this::selectivity));

		Op current = op;
		for (Op o : terms) {
			current.setFirst(o);
			current = current.getSecond();
		}
	}


	/**
	 * Format the expression emphasising the top operator.
	 */
	public static String fmtExpr(Op op) {
		return String.format("%s [%s] %s", op.getFirst(), op.getType(), op.getSecond());
	}

	/**
	 * Return a score for how much this op should be at the front.
	 *
	 * Lower is better and should be nearer the front.
	 *
	 * This is the core of the whole process, ideally we would like the term that is
	 * most likely to return false to be at the beginning of a chain of ands because
	 * as soon as one term returns false then we are done.
	 *
	 * Currently we have a very simple system, where equals and exists are first.
	 *
	 * Ideally you might want to consider tag frequencies, since highway is a very
	 * common tag, it would be better to push it behind other tags.
	 */
	private int selectivity(Op op) {
		switch (op.getType()) {
		case EQUALS:
			return 0;
		case EXISTS:
			return 100;

		case NOT_EQUALS:
		case NOT_EXISTS:
		case NOT:
			// None of these can be first, this will ensure that they never are
			// when there is more than one term
			return 1000;

		case AND:
			return 500;
		case OR:
			return 501;

		// Everything else is 200+, put regex behind as they are probably a bit slower.
		case REGEX:
		case NOT_REGEX:
			return 201;
		default:
			// Non-indexable functions must go to the back.
			if (!isIndexable(op))
				return 1000;
			return 200;
		}
	}

	/**
	 * True if this expression is 'solved'.  This means that the first term is indexable or it is indexable itself.
	 */
	public static boolean isSolved(Op op) {
		switch (op.getType()) {
		case NOT:
			return false;
		case AND:
			return isIndexable(op.getFirst());
		case OR:
			Op or = op;
			boolean valid = true;
			do {
				if (!isAndIndexable(or.getFirst()))
					valid = false;
				or = or.getSecond();
			} while (or.isType(OR));
			if (!isAndIndexable(or))
				valid = false;
			return valid;
		default:
			return isIndexable(op);
		}
	}

	/**
	 * This is the full test that a node is indexable including when it is an AND.
	 */
	private static boolean isAndIndexable(Op op) {
		if (op.isType(AND)) {
			return isIndexable(op.getFirst());
		} else {
			return isIndexable(op);
		}
	}
	/**
	 * True if this operation can be indexed.  It is a plain equality or Exists operation.
	 */
	private static boolean isIndexable(Op op) {
		return op.isType(EQUALS)
				&& ((ValueOp) op.getFirst()).isIndexable() && op.getSecond().isType(VALUE)
				|| NEED_EXISTS.contains(op.getType()) && ((ValueOp) op.getFirst()).isIndexable() && op.getSecond().isType(VALUE)
				|| op.isType(EXISTS) && ((ValueOp) op.getFirst()).isIndexable();
	}

}

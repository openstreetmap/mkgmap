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

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.osmstyle.eval.AbstractOp;
import uk.me.parabola.mkgmap.osmstyle.eval.AndOp;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.EqualsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExistsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NodeType;
import uk.me.parabola.mkgmap.osmstyle.eval.NotEqualOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NotExistsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NotOp;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.OrOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;
import uk.me.parabola.mkgmap.scan.SyntaxException;

import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.*;

/**
 * Routines to re-arrange a rule expression so that it can be used by the
 * mkgmap rule engine.
 */
public class ExpressionArranger {
	// Combining operation types.
	private static final EnumSet<NodeType> OPERATORS = EnumSet.of(AND, OR, NOT);

	// These types need to be combined with EXISTS if they are first.
    private static final EnumSet<NodeType> NEED_EXISTS = EnumSet.of(GT, GTE, LT, LTE, REGEX);

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

		Op op = expr;

		// If we have a not at the top level then it must be removed.
		while (op.isType(NOT))
			op = removeNot(op, true);

		if (OPERATORS.contains(op.getType()))
			orderBest(op);

		switch (op.getType()) {
		case AND:
			reAssociate(op, AND);

			// A, B, ... in the best order
			pulldownBest(op);

			// If we have an OR to the left after all this, we have to get rid of it.
			// This will turn this node into an OR.
			if (op.getFirst().isType(OR)) {
				op = distribute(op);
				arrangeTop(op);
			}
			break;

		case OR:
			reAssociate(op, OR);
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
		orderBest(last);
		reAssociate(op, OR);
	}

	/**
	 * Create a new OR expression from OR and other expression.
	 *
	 * Starting point is a node of the form (a|b) & c
	 *
	 * The output is (a & c) | (b & c)
	 */
	private Op distribute(Op op) {
		Op ab = op.getFirst();
		Op a = ab.getFirst();
		Op b = ab.getSecond();
		Op c = op.getSecond();

		assert a != b : "ab";
		assert b != c : "bc";

		List<Op> orterms = new ArrayList<>();
		while (b.isType(OR)) {
			Op nb = b.getFirst();
			assert nb != c;
			orterms.add(nb);
			b = b.getSecond();
			assert a != b;
			assert b != c;
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

	private void orderBest(Op op) {
		assert OPERATORS.contains(op.getType());

		if (leftNodeWeight(op.getFirst()) > leftNodeWeight(op.getSecond())) {
			op.set(op.getSecond(), op.getFirst());
		}
	}

	private int leftNodeWeight(Op op) {
		switch (op.getType()) {
		case AND: return 10;
		case OR: return 20;
		default: return 0;
		}
	}

	/**
	 * Remove a NOT operation.
	 *
	 * This is complicated by the fact that !(a<2) is not the same as a>=2 but
	 * in fact is (a>=2 | a!=*)
	 *
	 * @param op This will be a NOT node.
	 * @param must If true then throw an exception when the NOT is not removed.
	 * @return A new expression, could be the same as given.
	 */
	private Op removeNot(Op op, boolean must) {
		Op first = op.getFirst();
		switch (first.getType()) {
		case NOT:
			return first.getFirst();
		case EQUALS:
			BinaryOp ne = new NotEqualOp();
			return ne.set(first.getFirst(), first.getSecond());
		case NOT_EQUALS:
			BinaryOp eq = new EqualsOp();
			return eq.set(first.getFirst(), first.getSecond());
		case EXISTS:
			NotExistsOp nex = new NotExistsOp();
			nex.setFirst(first.getFirst());
			return nex;
		case NOT_EXISTS:
			ExistsOp ex = new ExistsOp();
			ex.setFirst(first.getFirst());
			return ex;
		case OR:
			// !(A | B) -> !A & !B
			AndOp and = new AndOp();
			NotOp n = new NotOp();
			n.setFirst(first.getFirst());
			and.setFirst(n);

			n = new NotOp();
			n.setFirst(first.getSecond());
			and.setSecond(n);

			orderBest(and);
			return and;

		case AND:
			// !(A & B) -> !A | !B
			OrOp or = new OrOp();
			n = new NotOp();
			n.setFirst(first.getFirst());
			or.setFirst(n);

			n = new NotOp();
			n.setFirst(first.getSecond());
			or.setSecond(n);

			orderBest(or);
			return or;
		default:
			if (must)
				throw new SyntaxException("Use of ! with " + first.getType() + " not supported");
		}

		return op;
	}

	/**
	 * Fix a chain of AND/OR nodes so that the chain is on the right.
	 *
	 * Eg: given (A&B)&(C&D) we return (A&(B&(C&D)))
	 */
	private void reAssociate(Op op, NodeType kind) {
		assert op.isType(kind);
		assert kind == OR || kind == AND;

		removeNotFromFirst(op);

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
			//removeNotFromFirst(and);
			op.set(a, and);

			removeNotFromFirst(op);
		}
	}

	private void removeNotFromFirst(Op op) {
		if (op.getFirst().isType(NOT))
			op.setFirst(removeNot(op.getFirst(), false));
		if (op.getSecond() != null && op.getSecond().isType(NOT))
			op.setSecond(removeNot(op.getSecond(), false));
	}

	private void pulldownBest(Op op) {
		Op last = op;
		List<Op> terms = new ArrayList<>();
		terms.add(op.getFirst());

		for (Op second = op.getSecond(); second != null && second.isType(AND); second = second.getSecond()) {
			orderBest(second);
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


	public static String fmtExpr(Op op) {
		return String.format("%s[%s]%s", op.getFirst(), op.getType(), op.getSecond());
	}

	private int selectivity(Op op) {
		//if (op.getFirst().isType(FUNCTION) && !isIndexableFunction(op.getFirst()))
		//	return 200;

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

		case OR:
		case AND:
			return 500;

		default:
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

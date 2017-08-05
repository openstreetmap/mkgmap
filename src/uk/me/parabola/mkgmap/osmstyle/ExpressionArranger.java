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

import java.util.EnumSet;

import uk.me.parabola.mkgmap.osmstyle.eval.NodeType;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;

import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.*;

/**
 * Routines to re-arrange a rule expression so that it can be used by the
 * mkgmap rule engine.
 */
public class ExpressionArranger {
	// These types need to be combined with EXISTS if they are first.
    private static final EnumSet<NodeType> NEED_EXISTS = EnumSet.of(GT, GTE, LT, LTE, REGEX);

	public Op arrange(Op expr) {
		return expr;
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

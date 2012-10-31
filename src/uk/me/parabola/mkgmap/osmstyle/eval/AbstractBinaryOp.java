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
 * Create date: 03-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.VALUE;

/**
 * A base class that can be used for binary operations.
 * It has a second operand.
 *
 * @author Steve Ratcliffe
 */
public abstract class AbstractBinaryOp extends AbstractOp implements BinaryOp {

	private Op second;

	public Op getSecond() {
		return second;
	}

	public void setSecond(Op second) {
		this.second = second;
	}

	public String toString() {
		String t2 = second.isType(VALUE) ? "'" + second + "'" : second.toString();
		return "(" + getFirst() + getType().toSymbol() + t2 + ')';
	}
}

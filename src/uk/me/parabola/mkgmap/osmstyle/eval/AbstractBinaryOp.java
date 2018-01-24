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
		StringBuilder sb = new StringBuilder();

		if (getSecond() instanceof LinkedOp) {
			// NOTE: care needed the toString method is used in compile() and we have
			// to therefore produce something unique to the expression.
			// It looks ugly at the moment because of that.
			sb.append("# Part of previous OR: ");
		}

		boolean needParen;

		needParen = this.hasHigherPriority(getFirst());
		if (needParen)
			sb.append('(');
		sb.append(getFirst().toString());
		if (needParen)
			sb.append(')');

		sb.append(' ');
		sb.append(getType().toSymbol());
		sb.append(' ');

		needParen = this.hasHigherPriority(getSecond());
		if (needParen)
			sb.append('(');
		sb.append(getSecond().toString());
		if (needParen)
			sb.append(')');

		return sb.toString();
	}
}

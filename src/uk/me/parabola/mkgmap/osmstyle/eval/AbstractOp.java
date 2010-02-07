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
 * A base class that can be used as the superclass of an operation.
 *
 * @author Steve Ratcliffe
 */
public abstract class AbstractOp implements Op {

	protected Op first;
	private char type;

	public static Op createOp(String value) {
		char c = value.charAt(0);
		Op op;
		switch (c) {
		case EQUALS: op = new EqualsOp(); break;
		case AND: op = new AndOp(); break;
		case OR: op = new OrOp(); break;
		case REGEX: op = new RegexOp(); break;
		case OPEN_PAREN: op = new OpenOp(); break;
		case CLOSE_PAREN: op = new CloseOp(); break;
		case '>':
			if (value.equals(">="))
				op = new GTEOp();
			else
				op = new GTOp();
			break;
		case '<':
			if (value.equals("<="))
				op = new LTEOp();
			else
				op = new LTOp();
			break;
		case '!':
			if (value.equals("!="))
				op = new NotEqualOp();
			else
				op = new NotOp();
			break;
		default:
			throw new SyntaxException("Unrecognised operation " + c);
		}
		return op;
	}

	/**
	 * Does this operation have a higher priority that the other one?
	 * @param other The other operation.
	 */
	public boolean hasHigherPriority(Op other) {
		return priority() > other.priority();
	}

	public Op getFirst() {
		return first;
	}

	public void setFirst(Op first) {
		this.first = first;
	}

	public char getType() {
		return type;
	}

	void setType(char type) {
		this.type = type;
	}

	public String value() {
		return first.toString();
	}

	public boolean isType(char value) {
		return type == value;
	}
}

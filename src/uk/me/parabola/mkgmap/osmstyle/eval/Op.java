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

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * A term in an expression for the style files.
 *
 * @author Steve Ratcliffe
 */
public abstract class Op {

	public static final int EQUALS = '=';
	public static final int NOT_EQUALS = '!';
	public static final int EXISTS = 'E';
	public static final int NOT_EXISTS = 'N';
	public static final int AND = '&';
	public static final int OR = '|';
	public static final char VALUE = 'V';
	public static final char OPEN_PAREN = '(';
	public static final char CLOSE_PAREN = ')';

	protected Op first;
	private char type;

	public static Op createOp(String value) {
		char c = value.charAt(0);
		switch (c) {
		case EQUALS:
			return new EqualsOp();
		case AND:
			return new AndOp();
		case OR:
			return new OrOp();
		case OPEN_PAREN:
			return new OpenOp();
		case CLOSE_PAREN:
			return new CloseOp();
		default:
			throw new SyntaxException("Unrecognised operation " + c);
		}
	}

	public abstract boolean eval(Element el);

	public abstract int priority();

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

	public void setType(char type) {
		this.type = type;
	}
}
